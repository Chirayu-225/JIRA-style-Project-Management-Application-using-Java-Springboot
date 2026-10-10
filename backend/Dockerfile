# ---------- Stage 1: build the JAR (has Maven + full JDK, ~600 MB, thrown away after) ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
# Copy pom.xml first: Docker caches the dependency download layer until pom.xml changes.
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B package -DskipTests

# ---------- Stage 2: run it (only a JRE + the JAR, ~200 MB) ----------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
# Don't run as root inside the container
RUN addgroup -S app && adduser -S app -G app
USER app
COPY --from=build /app/target/issue-tracker-api.jar app.jar
EXPOSE 8080
# MaxRAMPercentage: let the JVM size its heap from the container limit (Render free = 512 MB)
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+UseSerialGC", "-Xss512k", "-jar", "app.jar"]
