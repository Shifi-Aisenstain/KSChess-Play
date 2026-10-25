# Starts the KungFu Chess Swing client. Portable: locates the project via $PSScriptRoot
# (not a hardcoded absolute path) and prefers a real `mvn package` build; falls back to a plain
# javac compile (using jars already cached in the local ~/.m2/repository) if Maven isn't on PATH.
#
# Usage: .\run_client.ps1 [ws://host:port] [http://host:port]
#   First arg:  the WS Gateway address (defaults to ws://localhost:8887, see Main.java)
#   Second arg: the API Gateway address (defaults to http://localhost:8080, PLAN_split_6_services.md
#               §3.5 - login goes over REST to this address before the WebSocket above is opened)
param(
    [string]$ServerUri,
    [string]$ApiBaseUrl
)
. "$PSScriptRoot\build-and-run.ps1"
$appArgs = @()
if ($ServerUri) { $appArgs = @($ServerUri) }
if ($ApiBaseUrl) {
    if ($appArgs.Count -eq 0) { $appArgs = @("ws://localhost:8887") } # Main.java reads args positionally
    $appArgs += $ApiBaseUrl
}
Invoke-KungFuChess -MainClass "Main" -JarClassifier "client" -Title "KUNGFU CHESS CLIENT" -Color Cyan -AppArgs $appArgs
