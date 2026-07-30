פרויקט: KSChessPlay (KungFu Chess) — שחמט רשתי בזמן אמת, ללא תורות. המימוש הנוכחי: תהליך Java יחיד, שרת Java-WebSocket על פורט 8887, SQLite (kungfu_chess.db), RoomManager/GameSession בזיכרון בתוך אותו JVM, לולאת טיק של 50ms (20Hz), matchmaking לפי טווח Elo (±100).

עדכון (v2): מסמך זה מיישר את העיצוב עם הכיוון שהוצע במייל של תמר לנדאו (27.7) ובדיאגרמה המצורפת אליו, תוך שמירה על סדר הבנייה ההדרגתי ותוכנית הקיבולת שכבר תוכננו כאן. השינוי המהותי היחיד מול הגרסה הקודמת של המסמך: **הלקוח לא מתחבר ישירות ל-pod של game-session**. במקום זאת יש שכבת WS Gateway קבועה שמסיימת (terminates) את חיבור ה-WebSocket של הלקוח למשך כל המשחק, ומעבירה תעבורה בזמן אמת ל-Game Server Shard דרך bus פנימי (NATS/Redis PubSub). זה נותן ללקוח endpoint יציב אחד לתמיד (reconnect פשוט יותר), ומאפשר ש-Game Server Shards לא יהיו חשופים לרשת הציבורית בכלל — רק שכבת ה-Gateway חשופה.

1. רכיבי המערכת

- API Gateway (REST/HTTP) — login, ניהול חדרים, היסטוריה. לא בזמן אמת. ממופה ל-AuthService + RoomManager הקיימים, אבל כשירות נפרש בנפרד.
- WS Gateway — מנהל את כל החיבורים החיים מול הלקוחות, Async I/O (ללא thread-per-client), משדר state updates. לא מחליט כלום על חוקי המשחק — רק retransmits. רכיב חדש שצריך להוסיף (היום GameSession מדבר ישירות עם הסוקט של הלקוח).
- Matchmaker — מתאים בין שחקנים לפי טווח Elo. ממופה ל-MatchmakingService/MatchmakingServiceImpl הקיימים.
- Game Allocator — מחליט על אילו Game Server Shard ירוץ כל room חדש, ורושם את המיפוי room_id → shard. היום זה קורה באופן מובלע בתוך RoomManager במונוליט; צריך להוציא לרכיב עצמאי.
- Game Server Shards — מריצים את המשחקים בפועל: הרבה חדרים פעילים, תורי פקודות per-room, GameEngine authoritative (GameManager/RoomManager/RealTimeArbiter הקיימים). זה ה-single source of truth היחיד לחוקי המשחק — לא הלקוח, ולא ה-Gateway.
- Observability — logs, metrics, health checks, load tests. היום קיים רק כ-server_activity.log; צריך dashboard/metrics אמיתיים לפני production.

2. טכנולוגיות מומלצות

- NATS (או Redis PubSub) — ה-bus הפנימי בין כל השירותים: WS Gateway ↔ Game Server Shards (relay של תעבורת המשחק), Matchmaker ↔ Game Allocator, וגם נתיב ה-persistence האסינכרוני (ראו §3, שלב 4).
- Redis — מידע זמני: sessions, active rooms (registry room_id → shard), reconnect, תור matchmaking. מחליף את התור בזיכרון של MatchmakingServiceImpl ואת ה-presence שהיום חי רק בתוך ה-JVM.
- PostgreSQL — מידע קבוע: users, games, results, move history, Elo. UserRepository/SqliteUserRepository כבר מבודדים את שכבת ה-persistence מאחורי ממשק — מוסיפים PostgresUserRepository מאחורי אותו ממשק. SQLite נשאר ברירת המחדל לפיתוח מקומי ולהדגמה; Postgres הוא מתג קונפיגורציה ל-production.
- Docker Compose — גרסה קטנה עובדת מקומית של כל השירותים יחד (יעד קרוב, לפי בקשת תמר).
- Kubernetes / K3s — הרצת כל השירותים בסקאלה, עם autoscaling.
- Agones (אופציונלי, מאוחר) — לשקול ברגע שה-allocator הביתי (סעיף Game Allocator למעלה) מוכיח את הרעיון; מחליף את הלוגיקה הביתית של "לבחור shard, לרשום את החדר" ב-CRDs מסוג GameServer/Fleet/GameServerAllocation.

כלל עיצוב מרכזי: לא הלקוח ולא ה-Gateway (API או WS) מחליטים על חוקי המשחק. ה-GameEngine בתוך ה-Game Server Shard הוא ה-single source of truth היחיד. קווים מקווקווים בדיאגרמות מייצגים נתיבי בקרה/ניהול (control/management), לא זרימת דאטה של המשחק עצמו.

3. תוכנית פעולה (סדר בנייה)

זהו הסדר שבו המערכת צריכה להיבנות ולהיפרס בפועל, מהשרת החד-JVM הנוכחי ועד ליעד ה-production. כל שלב בונה על הקודם ונשאר עובד (deployable) בסופו.

1. הכנסה לקונטיינר כמות שהוא. Dockerfile סביב תהליך ה-mvn package הקיים; להריץ את kungfu-chess-server.jar בתוך קונטיינר. עדיין בלי שינוי ארכיטקטוני — רק מוכיח שהבנייה נשכפלת (reproducible).
2. גרסה קטנה עובדת עם Docker Compose (יעד מיידי לפי המייל של תמר). בשלב הזה מותר לאחד תפקידים לצורך פשטות — למשל API+WS Gateway בקונטיינר אחד, Matchmaker+Game Allocator בקונטיינר אחד — כל עוד הגבולות הלוגיים בין הרכיבים מכובדים בקוד. המטרה: end-to-end עובד עם Redis+Postgres אמיתיים, לפני אופטימיזציה.
3. פיצול אמיתי ל-6 הרכיבים בסעיף 1, כל אחד כשירות נפרד. מוציאים את ה-WS relay ל-WS Gateway עצמאי, ואת ה-Game Allocator מתוך RoomManager. זה שינוי הקוד הגדול ביותר, והתנאי המקדים לכל מה שאחריו.
4. הכנסת Redis עבור matchmaking queue, registry (room_id → shard), ו-session/presence — כל מה שחייב להיות משותף ברגע שיש יותר מתהליך אחד.
5. הפיכת האחסון ל-pluggable: PostgresUserRepository מאחורי הממשק הקיים, לצד SqliteUserRepository. הוספת נתיב persistence אסינכרוני — Game Server Shards מפרסמים אירוע GameEnded ל-NATS במקום לכתוב Elo באופן סינכרוני; מאגר writer workers מרוקן לתוך Postgres ב-batches.
6. פריסה על K3s (node יחיד או VM קטן): Deployment+Service לכל אחד מ-6 הרכיבים, Redis, Postgres. זו הנקודה הראשונה שבה "הרבה Pods, registry אחד" באמת רץ, גם אם בסקאלה קטנה.
7. Autoscaling: HPA לפי מספר חיבורים עבור WS Gateway, HPA לפי מטריקה מותאמת-אישית של "חדרים פעילים" (Prometheus adapter) עבור Game Server Shards.
8. Load-test כדי להחליף הנחות במדידות אמיתיות — ראו §4, לפני שמתחייבים לתוכנית קיבולת production.
9. מעבר לרב-אזורי (multi-region) לפרודקשן אמיתי — שכפול המחסנית לפי אזור, GeoDNS/latency routing לחיבור לאזור הקרוב.
10. שקילת Agones ברגע שה-allocator הביתי מוכיח את הרעיון בסקאלה קטנה.

4. תוכנית קיבולת ל-production — כמה שרתים/Pods, ומחולק איך?

המספרים האלה הם עבור יעד ה-production האמיתי, לא הדגמת K3s/Docker Compose (ההדגמה מריצה כמה replicas בודדים מכל תפקיד — מספיק כדי להוכיח את הארכיטקטורה, לא לשאת עומס אמיתי). כל מספר למטה נגזר מהנחת קיבולת מפורשת per-pod, שחייבת להיות מאומתת ב-load test (שלב 8 למעלה) לפני שמתייחסים אליה כתקציב אמיתי.

עומס בסיס: 10,000,000 שחקנים במקביל ⇒ ~5,000,000 משחקים במקביל (2 שחקנים/משחק); ~166,000 כתיבות עדכון-Elo בשנייה בסיום משחק; ~10,000,000 הודעות-מהלך בשנייה בסה"כ.

| שכבה | הנחה per-pod | חישוב | מספר Pods (כולל ~25% מרווח בטחון) |
|---|---|---|---|
| Game Server Shards (בעלים על חדרים, לולאת הטיק) | ~3,000 חדרים במקביל ל-Pod (4 vCPU/8GB) — דורש אימות ב-load-test | 5,000,000 חדרים ÷ 3,000 | ~1,700–2,100 Pods |
| WS Gateway (סיום WebSocket + relay, stateless) | ~50,000 חיבורים במקביל ל-Pod (4 vCPU/8GB) | 10,000,000 חיבורים ÷ 50,000 | ~200–250 Pods |
| API Gateway (REST, stateless) | ~10,000 בקשות/שנייה ל-Pod | תעבורת login/rooms/history, נמוכה משמעותית מ-WS | ~10–20 Pods |
| Matchmaker + Game Allocator (stateless, בקשות קצרות + Redis) | ~5,000 בקשות/שנייה ל-Pod | ~166,000 אירועי-התאמה/שנייה ÷ 5,000 | ~35–50 Pods |
| Persistence writers (אסינכרוני, batched) | ~2,000 כתיבות/שנייה ל-worker | 166,000 ÷ 2,000 | ~85–100 Pods |
| Redis (registry, תור, presence) | cluster מנוהל | ops/שנייה בתוך קיבולת נוחה ל-cluster צנוע | ~6–12 shards |
| PostgreSQL (חשבונות, Elo, היסטוריה) | ~6,000–10,000 כתיבות מתמשכות/שנייה ל-shard | 166,000 ÷ ~6,000–10,000 | ~20–28 shards (כל אחד primary + replicas) |

סה"כ טביעת רגל שכבת האפליקציה: בסדר גודל של 2,000–2,500 קונטיינרים בשיא הגלובלי, לפני ספירת replicas של DB/cache — מפוצל בין כמה אזורים ולא ריכוזי במקום אחד. החלוקה לא בהכרח שווה: מספר ה-Pods לפי אזור צריך לעקוב אחרי היכן ששחקנים באמת נמצאים, בעוד מספר ה-shards של Redis/Postgres קרוב יותר לקבוע ומתוכנן לפי קצב הכתיבה הגלובלי.

זו בכוונה לא תשובה מסוג "מריצים N שרתים" — כל הרעיון בעיצוב הוא שאף שרת בודד לא צריך להיות גדול; המספרים למעלה קיימים כדי לתכנן את גודל הצי, לא כדי לתכנן קופסה אחת.

5. תהליך ההתחברות של הלקוח — לאיזה שרת בפועל ניגשים?

בניגוד לגרסה הקודמת של המסמך, כאן יש **endpoint ציבורי קבוע אחד בלבד** — הלקוח לעולם לא מקבל ולא פותח חיבור ישיר ל-Game Server Shard.

- REST/HTTP (login, rooms, history) → API Gateway.
- WebSocket (בזמן אמת) → WS Gateway. תמיד אותה כתובת (למשל `wss://play.kschess.com`, או ב-K3s מקומי — NodePort/Ingress של שירות ה-WS Gateway).

זרימת המשחק בפועל:

1. הלקוח מתחבר ל-WS Gateway (כתובת קבועה).
2. WS Gateway מאמת מול API Gateway/Redis, ומעביר בקשת matchmaking או room ל-Matchmaker/Game Allocator דרך ה-bus הפנימי (NATS).
3. Game Allocator בוחר/מקצה Game Server Shard, רושם room_id → shard ב-Redis, ומודיע ל-WS Gateway לאיזה shard לחבר את הלקוח.
4. WS Gateway מתחיל לשדר (relay) בין הלקוח לבין ה-shard דרך ה-bus הפנימי — הלקוח לא יודע ולא צריך לדעת על קיומו של ה-shard.
5. בניתוק: הלקוח מתחבר מחדש לאותה כתובת קבועה; ה-WS Gateway מאתר את מיפוי room_id → shard הקיים ב-Redis וממשיך את ה-relay, בלי להקצות חדר חדש.

היתרון על פני "שתי קפיצות עם חיבור ישיר" מהגרסה הקודמת: ה-shards אף פעם לא חשופים ברשת הציבורית (רק ה-Gateway חשוף — משטח תקיפה קטן יותר), והלקוח תמיד מדבר עם אותה כתובת per session, מה שמפשט logic של reconnect בצד הלקוח. המחיר: כל בייט של תעבורת משחק עובר hop נוסף דרך ה-WS Gateway, ולכן שכבת ה-Gateway (~200–250 Pods בטבלה למעלה) חייבת לשאת את כל נפח ה-relay, לא רק את ניהול החיבורים עצמם — נקודה שצריכה אימות מפורש ב-load test (שלב 8).

                    ┌─────────────────────┐
לקוח (כתובת קבועה)   │  API Gateway (REST)  │  ← login / rooms / history
───────────────────▶│  WS Gateway (בזמן אמת)│
                    └──────────┬───────────┘
                               │ NATS / Redis PubSub
                    ┌──────────┴───────────┐
                    │ Matchmaker            │
                    │ Game Allocator        │
                    └──────────┬───────────┘
                               │ room_id → shard (ב-Redis)
                               ▼
                    ┌─────────────────────┐
                    │ Game Server Shard     │  (authoritative GameEngine)
                    │ (בעלים על החדר הזה)   │
                    └─────────────────────┘

6. סטטוס בפועל (2026-07-30)

שלבים 1-5 מסעיף 3 הושלמו בקוד (ר' `PLAN_split_6_services.md` §6 לפירוט מלא, כולל סטיות מהתכנון וההצדקות שלהן):

- **שלבים 1-2** (קונטיינר בודד, אז Docker Compose עם Redis+Postgres אמיתיים) — קיימים מקודם.
- **שלב 3** (פיצול אמיתי ל-6 הרכיבים) — הושלם: `server.shard`/`server.gateway`/`server.matchmaking`/`server.allocator`/`server.apigateway`, כל אחד תהליך נפרד. הרכיב היחיד שלא קיבל תהליך ייעודי משלו הוא "Observability" (סעיף 1 למעלה) - זה נשאר structured logging + health checks + `/metrics` בכל תהליך (ר' למטה), לא שירות נפרד. גם `GET /history`/`GET /history/moves` (REST, על ה-API Gateway) נוספו - השורה "REST/HTTP (login, rooms, history)" בסעיף 1 עכשיו ממומשת במלואה, לא רק login.
- **`/metrics` בכל תהליך** — נוסף: `shared.metrics.MetricsRegistry`/`MetricsRestHandler`, פורמט Prometheus-text פשוט (ללא ספריית תלות) לצד `/health` הקיים. סופר בקשות/הצלחות/כשלים ומדדי gauge כמו `gateway_active_connections`/`shard_active_games` per תהליך. אין עדיין Prometheus/Grafana פרוסים בפועל מול docker-compose (יש רק ב-`k8s/40-prometheus.yaml` למטה) - "dashboard/metrics אמיתיים לפני production" (סעיף 1) התקדם מ"לא קיים" ל"נמדד, לא עדיין מוצג/מתריע".
- **שלב 4** (Redis) — הושלם באופן חלקי-מכוון: תור ה-matchmaking וה-registry של active-room/reconnect עברו ל-Redis. **מיפוי room_id → shard במובן המילולי (טבלה נפרדת שאפשר לשאול "איזה shard מריץ את החדר הזה") לא מומש** - הקצאת ה-shard קורית פעם אחת ב-`RoomAllocatedPayload.shardId` ולא נשמרת בשום מקום אחר, כי שום דבר לא צריך לשאול את זה אחרי ההקצאה כרגע (כל תעבורת המשחק זורמת דרך `room.<roomId>.commands/.updates`, לא תלויה ב-shard id). זו הכרעת scope, לא פספוס: אם/כשיהיה צורך אמיתי (למשל Allocator עם כמה instances שצריכים להסכים מי מריץ מה אחרי restart), זה המקום להוסיף את זה.
- **שלב 5** (Postgres + persistence אסינכרוני) — הושלם: `PostgresUserRepository`, ותהליך `EloWriterMain` נפרד שצורך אירועי Elo מ-NATS ומרוקן ב-batches (לא Postgres-batches ברמת SQL בלבד - גם ברמת האפליקציה: צובר X שניות ואז כותב את כל מה שהצטבר בטרנזקציה אחת).
- **שלבים 6-8** (K3s, autoscaling, load test) — יש עכשיו מסמכי-תשתית/קוד מלאים אך לא מאומתים על cluster/stack חי: `k8s/*.yaml` (Deployment+Service ל-9 השירותים, ר' `k8s/README.md`), `k8s/40-42-*.yaml`+`HPA_AUTOSCALING.md` (Prometheus+adapter+HPA, כולל הסבר מפורש למה HPA של ה-shard הוא no-op מכוון עד שיש רישום shard דינמי), ו-`loadtest.LoadTestMain`+`LOAD_TEST.md` (הרנס עצמו רץ נגד הפרוטוקול האמיתי, אבל לא הורץ כאן נגד stack חי/cluster אמיתי - אין Docker/K3s בסביבה שבה זה נכתב). שלב 8 גם לא סיפק עדיין מדידות אמיתיות שמאמתות את מספרי סעיף 4 - הם עדיין הנחות.
- **שלבים 9-10** (multi-region, Agones) — תועדו ב-`MULTI_REGION_AND_AGONES.md` (כולל דוגמאות CRD מסומנות-כאילוסטרטיביות ב-`k8s/agones/`), לא מומשו כקוד/תשתית פרוסה - אלה החלטות ארכיטקטורה ל-scale שהפרויקט עדיין לא נמצא בו, בדיוק כפי שסעיף 3 למעלה מציין ("Agones ברגע שה-allocator הביתי מוכיח את הרעיון").
- **Sessions (Redis, סעיף 2 למעלה) — נסגר**: `server.auth.SessionStore`/`InMemorySessionStore`/`RedisSessionStore` נוספו. `POST /login` על ה-API Gateway כבר לא מחזיר userId/username/elo גולמיים ללקוח לשימוש חוזר - הוא מנפיק session token (TTL 24 שעות, נשמר ב-Redis). ה-IDENTIFY שהלקוח שולח ל-WS Gateway נושא רק את ה-token; `server.dispatch.handlers.IdentifyHandler` מאמת אותו מול Redis ומגזור את הזהות בצד השרת - במקום לסמוך על userId/username/elo שהלקוח שולח (שהיה אפשר לזייף). סוגר את הפער היחיד שנשאר תחת "Redis" בדיאגרמה המקורית.
- **Alerts (Observability, סעיף 1 למעלה) — נסגר**: `k8s/44-alert-rules.yaml` (8 חוקים - זמינות/אימות-session/persistence/matchmaking/רוויה, כולם מעל שמות המדדים ש-`shared.metrics.MetricsRegistry` כבר חושף) ו-`k8s/45-alertmanager.yaml` (Alertmanager חד-instance, receiver ריק במכוון - אין פרטי Slack/email/webhook אמיתיים לחבר). מחובר ל-`40-prometheus.yaml` דרך `rule_files`/`alerting.alertmanagers`.
- **Traces (Observability, סעיף 1 למעלה) — נסגר**: `shared.protocol.Message` קיבל שדה `traceId`, מופץ אוטומטית דרך `shared.tracing.TraceContext` (ThreadLocal, ללא תלות ב-OpenTelemetry SDK - תואם לסגנון ה-metrics הביתי) גם ב-WS Gateway וגם על כל קפיצת NATS (matchmaker/allocator/shard/elowriter) בלי לגעת בכל handler בנפרד. `gateway`/`apigateway` שולחים spans בפורמט Zipkin v2 ל-Jaeger (`shared.tracing.JaegerReporter`, `k8s/13-jaeger.yaml`); כל שורת log ב-`ServerActivityLogger` מסומנת עם ה-traceId הפעיל.
- **מגבלה ידועה שלא נפתרה**: זרימת ה-login/matchmaking דרך ה-WS Gateway בדיאגרמה למעלה (סעיף 5, "WS Gateway מאמת מול API Gateway... דרך ה-bus הפנימי") שונה מהמימוש בפועל - ה-login האמיתי (§3.5) קורה ישירות REST מהלקוח ל-API Gateway *לפני* שהלקוח בכלל פותח את ה-WebSocket, לא relay דרך ה-Gateway. זה תואם את הכוונה המקורית ("REST/HTTP → API Gateway" בשורה 64 למעלה) יותר מאשר את הדיאגרמה המפורטת בשורות 69-73, שנשארה לא מעודכנת מאז v2 של המסמך.
- **הכרעת scope שנשארת בכוונה, לא באג**: מיפוי room_id → shard במובן המילולי (טבלה נפרדת שאפשר לשאול "איזה shard מריץ את החדר הזה") עדיין לא מומש, כפי שסעיף שלב 4 למעלה מסביר - שום דבר לא צריך לשאול את זה כרגע.
