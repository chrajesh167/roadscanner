# syntax=docker/dockerfile:1

# --- Build stage --------------------------------------------------------
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline -B

COPY src ./src
RUN ./mvnw package -DskipTests -B \
    && mv target/booking-service-*.jar target/app.jar

# --- Runtime stage -------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S roadscanner && adduser -S bookingservice -G roadscanner
USER bookingservice:roadscanner

WORKDIR /app
COPY --from=build --chown=bookingservice:roadscanner /app/target/app.jar app.jar

EXPOSE 8085

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD wget -qO- http://localhost:8085/actuator/health/liveness || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
