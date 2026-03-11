FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
COPY services/order-service ./services/order-service
RUN ./gradlew :services:order-service:bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/services/order-service/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
