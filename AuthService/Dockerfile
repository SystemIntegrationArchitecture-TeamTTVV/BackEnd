## Multi-stage Dockerfile for AuthService (Spring Boot + JPA, Java 21)

# ====== BUILD STAGE ======
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

COPY pom.xml ./

RUN mvn -q -B dependency:go-offline

COPY src ./src

RUN mvn -q -B clean package -DskipTests


# ====== RUNTIME STAGE ======
FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /app/target/AuthService-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8083

ENV PORT=8083
# Profile set via env var on deployment platform
# ENV SPRING_PROFILES_ACTIVE=docker

CMD ["sh", "-c", "java -Dserver.port=${PORT} -jar app.jar"]
