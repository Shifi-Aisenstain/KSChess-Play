# Step 1 of Server_Design.md's build order (§3.1): containerize the current
# monolith server as-is - no architectural change, just proves the build is
# reproducible. Builds only the server jar; the Swing client is not a fit for
# a headless container.

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
# Wildcard: maven-shade-plugin names the attached artifact
# "<artifactId>-<version>-server.jar", not "kungfu-chess-server.jar" as
# build-and-run.ps1/README assume (see ARCHITECTURE.md note) - only one
# match exists so the wildcard is safe.
COPY --from=build /build/target/kungfu-chess-*-server.jar app.jar
# GameSession reads assets/board.csv (relative path) for the starting
# position. The per-piece sprite folders under assets/ are client-only.
COPY --from=build /build/assets/board.csv assets/board.csv

EXPOSE 8887
ENTRYPOINT ["java", "-jar", "app.jar"]
