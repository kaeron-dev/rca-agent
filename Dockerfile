FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
COPY services/ ./services/
COPY agent/ ./agent/
ARG SERVICE
RUN ./gradlew :services:${SERVICE}:bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
ARG SERVICE
COPY --from=builder /app/services/${SERVICE}/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
