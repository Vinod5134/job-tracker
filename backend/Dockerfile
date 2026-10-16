# ---- Build stage: compile and package the Spring Boot jar ----
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
# Download dependencies first so they're cached between builds
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

# ---- Run stage: small JRE image, non-root user ----
FROM eclipse-temurin:17-jre
WORKDIR /app
RUN useradd --system --uid 1001 app
COPY --from=build /app/target/job-tracker.jar app.jar
USER app
# Render's free plan has 512 MB of memory
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
