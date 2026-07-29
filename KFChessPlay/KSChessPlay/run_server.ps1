# Starts the KungFu Chess WebSocket server. Portable: locates the project via $PSScriptRoot
# (not a hardcoded absolute path) and prefers a real `mvn package` build; falls back to a plain
# javac compile (using jars already cached in the local ~/.m2/repository) if Maven isn't on PATH.
. "$PSScriptRoot\build-and-run.ps1"
Invoke-KungFuChess -MainClass "server.ServerController" -JarClassifier "server" -Title "KUNGFU CHESS SERVER" -Color Green
