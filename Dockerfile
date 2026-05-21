FROM gradle:8.14.3-jdk17-alpine AS builder

WORKDIR /app

COPY gradlew gradlew
COPY gradle gradle
COPY build.gradle settings.gradle ./
RUN chmod +x gradlew

COPY . .
RUN ./gradlew --no-daemon clean bootJar

FROM eclipse-temurin:17-jre-alpine

WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
