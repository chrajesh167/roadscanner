# syntax=docker/dockerfile:1

# --- Build stage --------------------------------------------------------
# Separate dependency-resolution layer from source compilation so a source-only change doesn't
# invalidate the (large, slow) dependency download layer on rebuild.
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline -B

COPY src ./src
RUN ./mvnw package -DskipTests -B \
    && mv target/auth-service-*.jar target/app.jar

# --- Runtime stage -------------------------------------------------------
# JRE only, not JDK — the runtime image never needs a compiler.
FROM eclipse-temurin:21-jre-alpine

# Non-root by default — no service on this platform runs as root, per
# docs/architecture/high-level-design.md's security posture.
RUN addgroup -S roadscanner && adduser -S authservice -G roadscanner
USER authservice:roadscanner

WORKDIR /app
COPY --from=build --chown=authservice:roadscanner /app/target/app.jar app.jar

EXPOSE 8081

# Actuator health, not a raw port check — verifies the app is actually serving, not just bound.
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD wget -qO- http://localhost:8081/actuator/health/liveness || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
