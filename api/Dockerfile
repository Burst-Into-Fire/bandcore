# syntax=docker/dockerfile:1

# ---- Build ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Dependencies first, so they are cached until pom.xml changes.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 sh ./mvnw -B -ntp dependency:go-offline

COPY src/ src/
# Tests run in CI (./mvnw verify); the image build only packages.
RUN --mount=type=cache,target=/root/.m2 sh ./mvnw -B -ntp -DskipTests package \
    && cp target/bandcore-api-*.jar /workspace/app.jar

# ---- Runtime ----
FROM eclipse-temurin:21-jre-alpine

# Pick up Alpine security fixes released after the base image was built.
RUN apk upgrade --no-cache
RUN addgroup -S bandcore && adduser -S -G bandcore -H -s /sbin/nologin bandcore
WORKDIR /app
COPY --from=build --chown=bandcore:bandcore /workspace/app.jar app.jar
USER bandcore

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/urandom" \
    TZ=UTC

# 8080: API (routed by the web proxy). 8081: management, never published.
EXPOSE 8080 8081

HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=5 \
  CMD wget -qO- http://127.0.0.1:8081/actuator/health/readiness > /dev/null || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
