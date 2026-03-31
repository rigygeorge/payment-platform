# Stage 1 — Build
FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml ./pom.xml
COPY merchant-service/pom.xml ./merchant-service/pom.xml
COPY payment-service/pom.xml ./payment-service/pom.xml
RUN mvn dependency:go-offline -B -pl payment-service -am
COPY payment-service/src ./payment-service/src
RUN mvn clean package -DskipTests -B -pl payment-service -am

# Stage 2 — Run
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
RUN groupadd -r appgroup && useradd -r -g appgroup appuser
COPY --from=builder /app/payment-service/target/*.jar app.jar
USER appuser
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]