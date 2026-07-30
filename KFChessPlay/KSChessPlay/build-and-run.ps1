# Shared build/run logic for run_client.ps1 (and, historically, run_server.ps1). Portable on purpose:
# everything is resolved relative to this script's own location ($PSScriptRoot) or the user's home
# directory ($env:USERPROFILE), never a hardcoded machine-specific path.
#
# Prefers a real `mvn package` (uses the shaded jars from pom.xml, so Maven stays the source of truth
# for the build). Falls back to a plain javac compile against jars already cached in the local
# ~/.m2/repository only if `mvn` isn't on PATH.
#
# DEPRECATED as the primary way to run the *backend* (PLAN_split_6_services.md §3.7): since §3.3-§3.5,
# there is no single "the server" process to launch this way anymore - it's 5 independent processes
# (Shard/Gateway/Matchmaker/Allocator/API Gateway) plus NATS/Redis/Postgres, which is exactly what
# `docker compose up --build` now runs together (see docker-compose.yml). This script is kept, not
# deleted, because `Invoke-KungFuChess` is still the right way to run the Swing *client* locally
# (see run_client.ps1) - a real display isn't a fit for a container - against a backend started via
# Docker Compose (or, in a pinch, by invoking this function once per backend process/classifier
# manually - it still works for that, just not as a single "run the server" entry point anymore).

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
    # Added with the internal NATS bus (PLAN_split_6_services.md §3.0) - every server-side process
    # imports io.nats.client.* now, so this fallback needs it on the compile/run classpath too, even
    # though the client-only "Main" entry point this script mostly runs today never touches it.
    $nats      = Resolve-M2Jar "io\nats" "jnats" "2.26.0"
    # Added with Redis (Server_Design.md step 4 - RedisActiveRoomRegistry/RedisWaitingPlayersQueue/
    # RedisConnectionProvider). Build-WithJavac compiles every *.java under src, not just the client's
    # own files, so this fallback classpath needs it too even though "Main" itself never touches Redis -
    # missing it fails with "package redis.clients.jedis does not exist" on those server-side files.
    $jedis     = Resolve-M2Jar "redis\clients" "jedis" "5.1.0"
    # jedis's own compile-time dependency (JedisPool's constructor/getResource() signatures reference
    # commons-pool2's pooling classes directly) - javac needs it even though our own code never imports
    # org.apache.commons.pool2.* itself. Version pinned to exactly what jedis-5.1.0's POM declares.
    $commonsPool2 = Resolve-M2Jar "org\apache\commons" "commons-pool2" "2.12.0"
    $classesDir = Join-Path $ProjectRoot "target\classes"
    return ($classesDir, $gson, $websocket, $sqlite, $slf4j, $nats, $jedis, $commonsPool2) -join ";"
}

function Build-WithJavac {
    param([string]$ProjectRoot)

    $srcDir = Join-Path $ProjectRoot "src"
    $outDir = Join-Path $ProjectRoot "target\classes"
    New-Item -ItemType Directory -Force -Path $outDir | Out-Null

    $sourceFiles = Get-ChildItem -Path $srcDir -Recurse -Filter "*.java" | ForEach-Object { $_.FullName }
    if (-not $sourceFiles) { throw "No .java files found under $srcDir." }

    $sourcesListFile = Join-Path $env:TEMP "kfchess-sources.txt"
    # NOT `Set-Content -Encoding utf8`: Windows PowerShell's "utf8" always writes a BOM, and javac
    # reads an @argfile using the OS's default (non-UTF-8) codepage - on a Hebrew-locale Windows box
    # that mis-decodes the BOM's bytes into a stray leading character, corrupting the very first path
    # in the file ("Invalid filename: ?C:\...\Main.java"). Writing plain UTF-8 with no BOM avoids this
    # regardless of the system's locale/codepage.
    [System.IO.File]::WriteAllLines($sourcesListFile, $sourceFiles, [System.Text.UTF8Encoding]::new($false))

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
