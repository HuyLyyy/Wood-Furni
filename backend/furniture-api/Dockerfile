# Multi-stage Dockerfile for Spring Boot furniture-api
# Build stage: compile with Maven + JDK 17
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app

# Build directly — skip dependency:go-offline to save disk on Render Free Tier
COPY pom.xml ./
COPY src ./src
RUN mvn -B -e -ntp clean package -DskipTests && \
    cp target/furniture-api-1.0.0-SNAPSHOT.jar app.jar && \
    rm -rf ~/.m2/repository && \
    rm -rf target

# Runtime stage: minimal JRE 17
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Non-root user for the runtime
RUN addgroup -S app && adduser -S app -G app
USER app

COPY --from=build /app/app.jar /app/app.jar

EXPOSE 8080

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
