# syntax=docker/dockerfile:1

# --- Build stage: compile + package the fat JAR (tests run in CI, not here) ---
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Warm the dependency cache before copying sources for better layer caching.
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
RUN ./gradlew --no-daemon dependencies >/dev/null 2>&1 || true

COPY src ./src
RUN ./gradlew --no-daemon clean bootJar -x test

# --- Runtime stage: slim JRE running the JAR as a non-root user ---
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

RUN groupadd --system app && useradd --system --gid app --uid 1001 app
COPY --from=build /workspace/build/libs/*.jar app.jar
USER 1001

EXPOSE 8080
ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
