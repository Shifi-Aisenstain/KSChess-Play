# Shared build/run logic for run_server.ps1 / run_client.ps1. Portable on purpose: everything is
# resolved relative to this script's own location ($PSScriptRoot) or the user's home directory
# ($env:USERPROFILE), never a hardcoded machine-specific path.
#
# Prefers a real `mvn package` (uses the shaded client/server jars from pom.xml, so Maven stays
# the source of truth for the build). Falls back to a plain javac compile against jars already
# cached in the local ~/.m2/repository only if `mvn` isn't on PATH.

function Invoke-KungFuChess {
    param(
        [Parameter(Mandatory = $true)][string]$MainClass,
        [Parameter(Mandatory = $true)][string]$JarClassifier,
        [Parameter(Mandatory = $true)][string]$Title,
        [string]$Color = "White",
        [string[]]$AppArgs = @()
    )

    $projectRoot = $PSScriptRoot
    Write-Host "=== $Title ===" -ForegroundColor $Color

    Push-Location $projectRoot
    try {
        $mvn = Get-Command mvn -ErrorAction SilentlyContinue
        if ($mvn) {
            Write-Host "Building with Maven..." -ForegroundColor DarkGray
            & mvn -q package "-DskipTests"
            if ($LASTEXITCODE -ne 0) {
                throw "mvn package failed (exit $LASTEXITCODE)."
            }
            # maven-shade-plugin's shadedArtifactAttached uses the standard Maven attached-artifact
            # naming (artifactId-version-classifier.jar), e.g. "kungfu-chess-2.0.0-server.jar" - NOT
            # the plain <finalName> ("kungfu-chess.jar") that the default jar:jar goal produces.
            # Glob instead of hardcoding the version so a version bump in pom.xml doesn't break this.
            $jarMatches = Get-ChildItem -Path (Join-Path $projectRoot "target") -Filter "kungfu-chess-*-$JarClassifier.jar" -ErrorAction SilentlyContinue
            if (-not $jarMatches) {
                throw "Build succeeded but no target\kungfu-chess-*-$JarClassifier.jar was produced - check the shade-plugin config in pom.xml."
            }
            $jarPath = $jarMatches[0].FullName
            & java -jar $jarPath @AppArgs
            return
        }

        Write-Host "Maven not found on PATH - falling back to javac + local ~/.m2 jars." -ForegroundColor Yellow
        Build-WithJavac -ProjectRoot $projectRoot
        $classpath = Get-FallbackClasspath -ProjectRoot $projectRoot
        & java -cp $classpath $MainClass @AppArgs
    }
    finally {
        Pop-Location
    }
}

function Resolve-M2Jar {
    param([string]$GroupPath, [string]$ArtifactId, [string]$Version)
    $jarPath = Join-Path $env:USERPROFILE ".m2\repository\$GroupPath\$ArtifactId\$Version\$ArtifactId-$Version.jar"
    if (-not (Test-Path $jarPath)) {
        throw ("Missing dependency jar: $jarPath`n" +
               "Install Maven (recommended) or run 'mvn -q dependency:go-offline' once from an IDE " +
               "to populate your local ~/.m2/repository, then re-run this script.")
    }
    return $jarPath
}

function Get-FallbackClasspath {
    param([string]$ProjectRoot)
    $gson      = Resolve-M2Jar "com\google\code\gson" "gson" "2.10.1"
    $websocket = Resolve-M2Jar "org\java-websocket" "Java-WebSocket" "1.5.4"
    $sqlite    = Resolve-M2Jar "org\xerial" "sqlite-jdbc" "3.44.1.0"
    # sqlite-jdbc's JDBC driver class-inits against slf4j-api - a transitive Maven dependency that
    # a real `mvn package` bundles automatically, but this manual fallback has to list explicitly.
    $slf4j     = Resolve-M2Jar "org\slf4j" "slf4j-api" "2.0.6"
    $classesDir = Join-Path $ProjectRoot "target\classes"
    return ($classesDir, $gson, $websocket, $sqlite, $slf4j) -join ";"
}

function Build-WithJavac {
    param([string]$ProjectRoot)

    $srcDir = Join-Path $ProjectRoot "src"
    $outDir = Join-Path $ProjectRoot "target\classes"
    New-Item -ItemType Directory -Force -Path $outDir | Out-Null

    $sourceFiles = Get-ChildItem -Path $srcDir -Recurse -Filter "*.java" | ForEach-Object { $_.FullName }
    if (-not $sourceFiles) { throw "No .java files found under $srcDir." }

    $sourcesListFile = Join-Path $env:TEMP "kfchess-sources.txt"
    $sourceFiles | Set-Content -Encoding utf8 $sourcesListFile

    # Lombok needs explicit annotation-processor flags under manual javac (see ARCHITECTURE.md §1) -
    # plain -cp does not trigger annotation processing on newer JDKs.
    $lombok = Resolve-M2Jar "org\projectlombok" "lombok" "1.18.46"
    $compileClasspath = (Get-FallbackClasspath -ProjectRoot $ProjectRoot) + ";" + $lombok

    $javacArgs = @(
        "-cp", $compileClasspath,
        "-d", $outDir,
        "-encoding", "UTF-8",
        "-processorpath", $lombok,
        "-processor", 'lombok.launch.AnnotationProcessorHider$AnnotationProcessor',
        "@$sourcesListFile"
    )

    & javac @javacArgs
    if ($LASTEXITCODE -ne 0) {
        throw "javac compilation failed (exit $LASTEXITCODE)."
    }
}
