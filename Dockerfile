# Multi-stage Dockerfile for Spring Boot furniture-api
# Placed at woodfurni/Dockerfile (root) — Render searches for Dockerfile at repo root.
# Build context is the entire woodfurni/ repo.
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app

# Build backend from the subdirectory
COPY backend/furniture-api/pom.xml ./pom.xml
COPY backend/furniture-api/src ./src
RUN mvn -B -e -ntp clean package -DskipTests && \
    cp target/furniture-api-1.0.0-SNAPSHOT.jar app.jar && \
    rm -rf ~/.m2/repository && \
    rm -rf target

# Runtime stage: minimal JRE 17
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app
USER app

COPY --from=build /app/app.jar /app/app.jar

EXPOSE 8080

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
