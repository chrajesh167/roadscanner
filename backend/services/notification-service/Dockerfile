# syntax=docker/dockerfile:1

# --- Build stage --------------------------------------------------------
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline -B

COPY src ./src
RUN ./mvnw package -DskipTests -B \
    && mv target/notification-service-*.jar target/app.jar

# --- Runtime stage -------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S roadscanner && adduser -S notificationservice -G roadscanner
USER notificationservice:roadscanner

WORKDIR /app
COPY --from=build --chown=notificationservice:roadscanner /app/target/app.jar app.jar

# application.yml deliberately declares no datasource or broker, so a container started without a
# profile fails at startup rather than connecting somewhere unintended. `dev` is the deployed
# profile (application-dev.yml), which reads every environment-specific value from the environment.
# Override at run time (-e SPRING_PROFILES_ACTIVE=...) for any other environment.
ENV SPRING_PROFILES_ACTIVE=dev

EXPOSE 8087

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD wget -qO- http://localhost:8087/actuator/health/liveness || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
