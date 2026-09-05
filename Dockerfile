# ---- build stage ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
# Cache dependencies before copying source.
RUN mvn -q -DskipTests dependency:go-offline || true
COPY src ./src
RUN mvn -q -DskipTests package

# ---- run stage ----
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
# Use most of the container's memory as heap - the JVM defaults to 25%, too little on a small tier.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
