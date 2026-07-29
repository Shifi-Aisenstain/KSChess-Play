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
