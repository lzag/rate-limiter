FROM gradle:8.10-jdk21 AS builder

WORKDIR /app

COPY . .

RUN gradle shadowJar --no-daemon

FROM openjdk:21-jdk-slim

WORKDIR /app

COPY --from=builder /app/build/libs/rate-limiter-1.0.0-SNAPSHOT-fat.jar app.jar

EXPOSE 8888

ENTRYPOINT ["java", "-jar", "app.jar"]
