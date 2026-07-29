# KSChessPlay — תוכנית פיצול ל-6 שירותים (שלב 3, Server_Design.md §3.3)

החלטות שנקבעו לפני התכנון:
- **Bus פנימי בין השירותים: NATS.** Redis נשאר ייעודי ל-registry/queue/session (שלב 4 במסמך העיצוב) — כך שהתפקידים לא מתערבבים: NATS מעביר הודעות, Redis שומר מצב.
- **REST ל-API Gateway: `com.sun.net.httpserver`** (מובנה ב-JDK, בלי תלות חדשה) — תואם לסגנון הפרויקט עד כה (בלי Spring/framework כבד).
- **בקשות room-create/join/matchmaking (לא login) נשארות על פרוטוקול ה-WS הקיים**, ומועברות דרך WS Gateway ל-bus הפנימי — זה בדיוק מה שמתואר בדיאגרמה של §5 במסמך ("WS Gateway... מעביר בקשת matchmaking או room ל-Matchmaker/Game Allocator דרך ה-bus הפנימי"). **LOGIN לבדו עובר ל-REST** דרך API Gateway, כפי שהמסמך קובע במפורש ("REST/HTTP (login, rooms, history) → API Gateway") — זה מחייב שינוי קטן בלקוח (קריאת HTTP אחת לפני פתיחת ה-WebSocket).

---

## 1. מיפוי: קוד קיים → שירות יעד

| שירות יעד | קוד קיים שעובר אליו (רוב/כל הלוגיקה) |
|---|---|
| **Game Server Shard** | `engine/*`, `models/*`, `rules/*`, `realtime/RealTimeArbiter`, `controller/MoveCommand`, `io/CsvBoardLoader`, `view/GameSnapshot`, `view/PieceSnapshot`, `view/SnapshotSource`, `view/CooldownHighlight`, `server.game.GameSession`, `server.game.DisconnectWatchdog`, `server.game.GameEventBroadcaster`, `server.rating.*` |
| **WS Gateway** | `server.net.ChessWebSocketServer`, `server.net.SessionRegistry`, `server.net.PlayerSession`, `server.dispatch.MessageDispatcher`, handlers: `SelectHandler`, `MoveHandler`, `JumpHandler`, `ResignHandler`, `PingHandler`, `RoomCreateHandler`, `RoomJoinHandler`, `RoomCancelHandler`, `PlayRequestHandler`, `PlayCancelHandler` |
| **Matchmaker** | `server.matchmaking.*` (MatchmakingServiceImpl/MatchmakingService/MatchRequest/MatchCriteria/EloRangeMatchCriteria/MatchListener) |
| **Game Allocator** | חלק מ-`server.rooms.RoomManager` (יצירת חדר, `RoomIdGenerator`, `reserveRoom`/`reserveNewRoom`, מעקב `activeRoomByUserId`) — הופך למחלקה נפרדת |
| **API Gateway** | `server.auth.*` (AuthService/AuthServiceImpl/PasswordHasher/UserRepository/SqliteUserRepository), `LoginHandler` (נכתב מחדש כ-endpoint REST) |
| **Observability** | `server.logging.ServerActivityLogger` (הופך ל-structured logging בכל שירות + health endpoint) |
| **נשאר משותף (shared)** | `shared.protocol.*` (Message/MessageCodec/MessageType/payload/*) — פרוטוקול הלקוח, וגם עבור הודעות ה-bus הפנימיות (אותו Gson codec, לא צריך פורמט חדש). `shared.eventbus.*` נשאר per-process לצרכים פנימיים (למשל GameEventBroadcaster בתוך ה-Shard) |
| **לא זז (client-only)** | `client/*`, `view/GameWindow`, `view/GameLoop`, `view/ImgRenderer`, `view/SpriteLoader`, `graphics/*`, `input/*` |

`server.rooms.Room` מתפצל בפועל: תפקיד ה"מי חבר בחדר ומה ה-role שלו" נשאר רלוונטי בתוך ה-Shard (כחלק מ-GameSession), אבל בלי `MessageSink` ישיר לסוקט — הפצת עדכונים יוצאת כהודעת NATS במקום קריאה ישירה.

---

## 2. פרוטוקול תקשורת פנימי (NATS)

משתמשים ב-`MessageCodec` הקיים (Gson) לסריאליזציה — אין צורך בפורמט הודעה חדש, רק subjects חדשים:

| Subject | כיוון | מטרה |
|---|---|---|
| `room.<roomId>.commands` | WS Gateway → Shard | SELECT/MOVE/JUMP/RESIGN שהתקבלו מהלקוח |
| `room.<roomId>.updates` | Shard → WS Gateway | STATE_UPDATE (per-user), GAME_EVENT, GAME_OVER |
| `matchmaking.requests` | WS Gateway → Matchmaker | PLAY_REQUEST/PLAY_CANCEL |
| `matchmaking.results` | Matchmaker → WS Gateway | MATCH_FOUND / not-found (כולל userId בפיילוד לניתוב) |
| `room.create` / `room.join` / `room.cancel` | WS Gateway → Allocator | פעולות ניהול חדר |
| `room.allocated` | Allocator → WS Gateway + Shard | "לחדר X הוקצה shard Y, תתחילו" |
| `user.<userId>.active-room` (request/reply) | WS Gateway → Allocator | reconnect: "לאיזה חדר המשתמש הזה שייך?" (מחליף את `activeRoomByUserId` של RoomManager היום) |

**הערה חשובה על scope לשלב 3:** ב-docker-compose רץ replica יחיד של WS Gateway (אין עדיין autoscaling — זה שלב 7). לכן מותר להניח שכל הלקוחות של חדר נתון מחוברים לאותו WS Gateway יחיד, וה-Gateway יכול פשוט להירשם ל-`room.<roomId>.updates` לפי הצורך בלי registry מפורש. ניתוב בין כמה instances של WS Gateway (כשיהיה autoscaling) דורש רישום מפורש (Redis, שלב 4/7) — לא פותרים את זה עכשיו, רק מסמנים כמגבלה ידועה.

---

## 3. סדר בנייה בפועל (תת-שלבים בתוך שלב 3)

כל תת-שלב נשאר buildable ו-testable בסופו — לא מפרקים הכל בבת אחת.

**3.0 — הכנה (bus + תשתית משותפת)**
- הוספת תלות `io.nats:jnats` ל-`pom.xml`.
- הוספת שירות `nats` (image `nats:2-alpine`) ל-`docker-compose.yml`.
- מחלקה חדשה `shared.bus.MessageBus` — עטיפה דקה מעל NATS client: `publish(subject, Message)`, `subscribe(subject, handler)`, `request(subject, Message, timeout)`. משתמשת ב-`MessageCodec` הקיים.
- בדיקה: טסט round-trip פשוט (publish→receive) מול NATS מקומי.

**3.1 — הוצאת Game Server Shard (השינוי הגדול והמסוכן ביותר — מבודד לבד)**
- `server.shard.GameServerShardMain`: מתחבר ל-NATS, מקבל "תתחיל לרוץ על חדר X" (`room.allocated`), בונה `GameSession` (כמעט ללא שינוי) שמפרסם עדכונים ל-`room.<roomId>.updates` במקום `Room.sendTo` ישיר.
- שאר המערכת (WS handling, RoomManager, Matchmaker, Auth) **נשארת במונוליט הקיים** בתת-שלב הזה — רק `RoomManager.startGameSession` משתנה: במקום `new GameSession(...)` בתהליך, שולח פקודה ל-Shard החיצוני ומתייחס אליו כמרוחק.
- בדיקה: המונוליט (הכל חוץ מ-GameSession) משוחח בהצלחה עם Shard חיצוני יחיד על NATS; משחק מלא עובד קצה-לקצה.

**3.2 — הוצאת WS Gateway**
- `server.gateway.WsGatewayMain`: `ChessWebSocketServer` + `SessionRegistry`/`PlayerSession` + `MessageDispatcher` + handlers של SELECT/MOVE/JUMP/RESIGN/PING/ROOM_CREATE/ROOM_JOIN/ROOM_CANCEL/PLAY_REQUEST/PLAY_CANCEL.
- ה-handlers האלה כבר לא קוראים ל-`RoomManager`/`GameSession` בתהליך — מפרסמים ל-NATS.
- בדיקה: לקוח Swing מתחבר ל-WS Gateway בלבד, ומצליח לשחק משחק מלא מול ה-Shard מ-3.1 דרך ה-Gateway.

**3.3 — הוצאת Matchmaker**
- `server.matchmaking.MatchmakerMain`: נרשם ל-`matchmaking.requests`, מפרסם תוצאות ל-`matchmaking.results`, ובמקום `roomManager.createRoomForMatch` ישיר — שולח `room.create` ל-Allocator.
- בדיקה: שני לקוחות נכנסים ל-matchmaking queue, מותאמים, ומתחיל משחק.

**3.4 — הוצאת Game Allocator**
- `server.allocator.GameAllocatorMain`: `RoomIdGenerator`, `reserveRoom`/`reserveNewRoom`, מעקב `activeRoomByUserId` — נרשם ל-`room.create`/`room.join`/`room.cancel`, בוחר Shard (round-robin פשוט על רשימת shard IDs מקונפיגורציה — לא load-based עדיין), עונה ל-`user.<userId>.active-room`.
- בדיקה: יצירת חדר ידנית + הצטרפות לפי קוד עובדות קצה-לקצה; reconnect למשחק קיים עובד.

**3.5 — הוצאת API Gateway**
- `server.apigateway.ApiGatewayMain`: `AuthService`/`SqliteUserRepository` מאחורי `POST /login` עם `com.sun.net.httpserver`.
- **שינוי לקוח נדרש כאן:** ה-Swing client צריך קריאת HTTP ל-`/login` לפני פתיחת ה-WebSocket, במקום לשלוח הודעת LOGIN על הסוקט. זה שינוי ממוקד וקטן (מקום אחד ב-`NetworkGameController`/`LoginController`), אבל הוא שינוי לקוח אמיתי שצריך לתכנן בנפרד.
- בדיקה: login מלא דרך REST, ואז חיבור WS עם ה-token/userId שהתקבל.

**3.6 — Observability (מינימלי לשלב הזה)**
- `ServerActivityLogger` → פלט JSON מובנה ל-stdout בכל התהליכים (ולא רק קובץ) — נוח ל-log aggregation עתידי.
- `/health` endpoint טריוויאלי בכל שירות (כולל אלה עם REST server — Shard/Gateway/Matchmaker/Allocator יכולים לחשוף health דרך socket פשוט או שרת HTTP זעיר משלהם).
- דשבורדים/מטריקות אמיתיים (Prometheus וכו') — **לא בשלב הזה**, המסמך עצמו ממסגר את זה כדרישה מאוחרת יותר.

**3.7 — חיווט build/deploy**
- `pom.xml`: 5 executions חדשים של shade-plugin (אחד לכל mainClass חדש), לצד client-jar הקיים.
- Dockerfile יחיד עם build-arg שבוחר איזה jar/mainClass להריץ (במקום 5 Dockerfile כמעט-זהים) — עקבי עם הגישה של `build-and-run.ps1` (לוגיקה משותפת, לא כפילות).
- `docker-compose.yml`: בלוק שירות לכל אחד מ-5 התהליכים החדשים + `nats`, לצד `redis`/`postgres` הקיימים.
- `build-and-run.ps1`/`run_server.ps1`: כנראה יתיישנו כנתיב ההרצה העיקרי לטובת `docker compose up` — לסמן ולא למחוק עד שמוודאים.

**3.8 — אימות קצה-לקצה**
- `docker compose up --build` מריץ את כל 5+3 הקונטיינרים (5 שירותי אפליקציה + nats+redis+postgres).
- זרימה מלאה: login → יצירת/הצטרפות חדר או matchmaking → מהלכים/קפיצות → resign/checkmate → עדכון Elo.
- הטסטים הקיימים צריכים להתפצל בהתאם — `GameSessionConcurrencyTest` למשל נשאר רלוונטי כטסט פנימי של ה-Shard (בלי שכבת הרשת), נקי יותר מבעבר.

---

## 4. מה נשאר מחוץ לתחום (מכוון, לפי סדר הבנייה של המסמך)

- Registry אמיתי ב-Redis ל-`room_id → shard` (כרגע in-memory ב-Allocator) — שלב 4.
- `PostgresUserRepository` + נתיב persistence א-סינכרוני דרך NATS ל-Postgres — שלב 5 (כרגע ה-Shard כותב Elo ישירות דרך `UserRepository` הקיים, סינכרוני, בדיוק כמו היום).
- פריסה על K3s, autoscaling — שלבים 6-7.
- Load test לאימות ההנחות (~3,000 חדרים ל-Shard וכו') — שלב 8.

---

## 5. סיכונים ונקודות תשומת לב

- **3.1 הוא הסיכון המרכזי**: `GameSession` היום מניח גישה סינכרונית ל-`Room.sendTo`/`broadcastToAll`. המעבר לפרסום א-סינכרוני על NATS חייב לשמר את אותה סמנטיקה של locking (`gameStateLock`) — אין שינוי בלוגיקת ה-lock עצמה, רק בְּמה שקורה אחרי שחרור הנעילה (פרסום להודעת NATS במקום כתיבת סוקט ישירה).
- **ניתוב STATE_UPDATE ל-instance הנכון של WS Gateway** כשיהיו כמה instances (אחרי שלב 7) — לא נפתר עכשיו, מתועד כמגבלה ידועה.
- **טסטים קיימים** (`MoveHandlerTest`, `JumpHandlerTest` וכו') בודקים היום תגובת שגיאה סינכרונית מיידית מה-handler. אחרי הפיצול, ה-handler שולח פקודה ל-bus וה-Shard עונה א-סינכרונית — סביר שחלק מהטסטים האלה צריכים להיכתב מחדש עם mock ל-`MessageBus` במקום ל-`RoomManager`/`GameSession` ישירות.
- **שינוי הלקוח (3.5, ה-login)** הוא שינוי אמיתי בקוד ה-Swing, לא רק בשרת — כדאי לתכנן אותו כתת-משימה נפרדת כשמגיעים לשם.
