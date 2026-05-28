## Multi-stage Dockerfile for ApiGateway (Spring Cloud Gateway, Java 21)

# ====== BUILD STAGE ======
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

# Copy Maven descriptor first to leverage Docker layer cache
COPY pom.xml ./

# Download dependencies (cached if pom.xml unchanged)
RUN mvn -q -B dependency:go-offline

# Copy source code
COPY src ./src

# Build the application (skip tests for faster image builds)
RUN mvn -q -B clean package -DskipTests


# ====== RUNTIME STAGE ======
FROM eclipse-temurin:21-jre

WORKDIR /app

# Copy the built jar from the build stage
COPY --from=build /app/target/ApiGateway-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8088

ENV PORT=8088

# Profile set via env var on deployment platform
# ENV SPRING_PROFILES_ACTIVE=docker

CMD ["sh", "-c", "java -Dserver.port=${PORT} -jar app.jar"]
