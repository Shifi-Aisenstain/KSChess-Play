# Starts the KungFu Chess Swing client. Portable: locates the project via $PSScriptRoot
# (not a hardcoded absolute path) and prefers a real `mvn package` build; falls back to a plain
# javac compile (using jars already cached in the local ~/.m2/repository) if Maven isn't on PATH.
#
# Usage: .\run_client.ps1 [ws://host:port]   (defaults to ws://localhost:8887, see Main.java)
param(
    [string]$ServerUri
)
. "$PSScriptRoot\build-and-run.ps1"
$appArgs = @()
if ($ServerUri) { $appArgs = @($ServerUri) }
Invoke-KungFuChess -MainClass "Main" -JarClassifier "client" -Title "KUNGFU CHESS CLIENT" -Color Cyan -AppArgs $appArgs
