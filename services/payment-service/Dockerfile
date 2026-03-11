FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
COPY services/payment-service ./services/payment-service
RUN ./gradlew :services:payment-service:bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/services/payment-service/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
