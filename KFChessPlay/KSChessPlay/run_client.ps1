Set-Location "c:\Users\User\Desktop\studies\bootcamp\KSChess-Play\KFChessPlay\KSChessPlay"
$cp = "target\classes;C:\Users\User\.m2\repository\com\google\code\gson\gson\2.10.1\gson-2.10.1.jar;C:\Users\User\.m2\repository\org\java-websocket\Java-WebSocket\1.5.4\Java-WebSocket-1.5.4.jar;C:\Users\User\.m2\repository\org\xerial\sqlite-jdbc\3.44.1.0\sqlite-jdbc-3.44.1.0.jar;C:\Users\User\.m2\repository\org\slf4j\slf4j-api\2.0.6\slf4j-api-2.0.6.jar"
Write-Host "=== KUNGFU CHESS CLIENT ===" -ForegroundColor Cyan
java -cp $cp Main
