# Stage 1: Build fat jar
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Pre-fetch dependencies to leverage Docker layer caching
COPY pom.xml .
RUN mvn -B dependency:resolve dependency:resolve-plugins

# Build application fat jar (skipping tests since Testcontainers requires host Docker daemon)
COPY src ./src
RUN mvn -B -DskipTests clean package

# Stage 2: Minimal hardened runtime image
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Run as non-root user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY --from=build /build/target/smartdroneinspection-*.jar app.jar
RUN chown -R appuser:appgroup /app

USER appuser

EXPOSE 8080

# Configure JVM container memory awareness and G1 GC
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC"

HEALTHCHECK --interval=10s --timeout=5s --start-period=40s --retries=5 \
  CMD wget --quiet --tries=1 --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
