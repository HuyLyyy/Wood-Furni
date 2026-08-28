# =============================================================================
# WOODFURNI Backend — multi-stage build
#   Stage 1: build fat JAR bằng maven:3.9-eclipse-temurin-17
#   Stage 2: copy JAR sang eclipse-temurin:17-jre (JRE only, ~250MB vs full JDK ~500MB)
# =============================================================================

# ---------- Stage 1: build ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace

# Cache dependency layer — chỉ re-resolve khi pom.xml đổi
COPY backend/furniture-api/pom.xml ./pom.xml
RUN mvn -B -q dependency:go-offline

# Copy source & build (skip tests để image build nhanh; CI chạy test riêng)
COPY backend/furniture-api/src ./src
RUN mvn -B -q -DskipTests package

# ---------- Stage 2: runtime ----------
FROM eclipse-temurin:17-jre
WORKDIR /app

# Tạo user non-root để chạy app
RUN groupadd --system --gid 1001 woodfurni \
    && useradd --system --uid 1001 --gid woodfurni --no-create-home woodfurni

# Copy fat JAR (Spring Boot bootJar tên = artifact-version.jar)
COPY --from=build /workspace/target/furniture-api-*.jar /app/app.jar

# Tạo thư mục upload và chown cho user woodfurni
# (volume mount sẽ override, nhưng docker init container sẽ fix quyền lại)
RUN mkdir -p /app/uploads && chown -R woodfurni:woodfurni /app/uploads

USER woodfurni
EXPOSE 8080

# Healthcheck & JVM tuning
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

# shell form để JAVA_OPTS (env var) được expand
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]