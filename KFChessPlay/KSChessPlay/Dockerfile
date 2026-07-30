# Unified process image (PLAN_split_6_services.md §3.7): one build recipe for all 5 backend
# processes - Game Server Shard, WS Gateway, Matchmaker, Game Allocator, API Gateway - replacing the
# near-duplicate per-process Dockerfiles from §3.1-§3.5 (Dockerfile.shard/.gateway/.matchmaker/
# .allocator, and the Dockerfile.apigateway that adding a 5th one would have meant). docker-compose.yml
# passes JAR_CLASSIFIER as a build arg to pick which shaded jar (see pom.xml's shade-plugin executions -
# the classifier names match exactly: shard/gateway/matchmaker/allocator/apigateway) ends up as app.jar.
#
# The Swing client (classifier "client") deliberately isn't wired up here - it's not a fit for a
# headless container at all (needs a real display) - build-and-run.ps1/run_client.ps1 remain the way
# to run it locally.

# --- build stage ---
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
COPY test ./test
COPY assets ./assets
RUN mvn -q package -DskipTests

# --- runtime stage ---
FROM eclipse-temurin:17-jre
WORKDIR /app
ARG JAR_CLASSIFIER
# maven-shade-plugin names each attached artifact "<artifactId>-<version>-<classifier>.jar" - wildcard
# so a version bump doesn't break this; exactly one match exists per classifier.
COPY --from=build /build/target/kungfu-chess-*-${JAR_CLASSIFIER}.jar app.jar
# Only server.shard.GameServerShardMain's GameSession actually reads assets/board.csv (relative path,
# starting position) at runtime - copying it into every image regardless is simpler than templating
# this Dockerfile further just to skip a few KB for the other 4 processes.
COPY --from=build /build/assets/board.csv assets/board.csv

ENTRYPOINT ["java", "-jar", "app.jar"]
