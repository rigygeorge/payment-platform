# ── Build stage ──────────────────────────────────────────────────────────────
FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /app

# Copy root POM first
COPY pom.xml .

# Copy all module POMs before downloading deps (layer-cache friendly)
COPY merchant-service/pom.xml merchant-service/pom.xml
COPY payment-service/pom.xml payment-service/pom.xml
COPY transaction-service/pom.xml transaction-service/pom.xml

RUN mvn dependency:go-offline -B --no-transfer-progress

# Copy source and build only transaction-service
COPY merchant-service/src merchant-service/src
COPY payment-service/src payment-service/src
COPY transaction-service/src transaction-service/src

RUN mvn clean package -pl transaction-service -am -DskipTests -B --no-transfer-progress

# ── Runtime stage ─────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre

WORKDIR /app

RUN addgroup --system appgroup && adduser --system --ingroup appgroup appuser

COPY --from=build /app/transaction-service/target/*.jar app.jar

RUN chown appuser:appgroup app.jar
USER appuser

EXPOSE 8083

ENTRYPOINT ["java", \
  "--enable-preview", \
  "-XX:+UseZGC", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]