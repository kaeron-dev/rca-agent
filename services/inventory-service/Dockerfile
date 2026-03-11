FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
COPY services/inventory-service ./services/inventory-service
RUN ./gradlew :services:inventory-service:bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/services/inventory-service/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
