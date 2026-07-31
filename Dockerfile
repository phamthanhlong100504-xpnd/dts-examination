# ============================
# Stage 1: Build
# ============================
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app

# Copy wrapper and config to cache dependencies
COPY gradle/wrapper/ gradle/wrapper/
COPY gradlew .
COPY build.gradle settings.gradle ./

# Download dependencies (Cache this layer)
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

# Copy source code and build
COPY src/ src/
RUN ./gradlew bootJar --no-daemon -x test

# ============================
# Stage 2: Runtime
# ============================
FROM ibm-semeru-runtimes:open-21-jre AS runtime
WORKDIR /app

# Create non-root user for security (Standard Linux)
RUN groupadd -r dts && useradd -r -g dts -s /bin/false dts

# Copy JAR from builder stage
COPY --from=builder /app/build/libs/*.jar app.jar

RUN chown dts:dts app.jar
USER dts

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
