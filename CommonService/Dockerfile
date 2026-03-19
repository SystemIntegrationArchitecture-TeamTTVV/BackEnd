## Multi-stage Dockerfile for CommonService (Spring Boot, Java 21)

# ====== BUILD STAGE ======
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

# Copy Maven descriptor and wrapper (if any) first to leverage Docker layer cache
COPY pom.xml ./
COPY mvnw mvnw.cmd* . || true
COPY .mvn .mvn || true

# Download dependencies (will be cached if pom.xml does not change)
RUN mvn -q -B dependency:go-offline

# Copy source code
COPY src ./src

# Build the application (skip tests for faster image builds)
RUN mvn -q -B clean package -DskipTests


# ====== RUNTIME STAGE ======
FROM eclipse-temurin:21-jre

WORKDIR /app

# Copy the built jar from the build stage
COPY --from=build /app/target/CommonService-0.0.1-SNAPSHOT.jar app.jar

# Expose default port used in application.properties
EXPOSE 8081

# Allow overriding port via PORT env var (Render sets this automatically)
ENV PORT=8081

# Use exec form to ensure proper signal handling
CMD ["sh", "-c", "java -Dserver.port=${PORT} -jar app.jar"]

