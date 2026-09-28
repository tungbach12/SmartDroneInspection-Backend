# Stage 1: Build fat jar
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Pre-fetch dependencies to leverage Docker layer caching.
# dependency:go-offline resolves the full build classpath without running any lifecycle
# phase; unlike dependency:resolve it tolerates artifacts whose remote metadata is
# temporarily unreachable because it stops at the first missing optional file.
COPY pom.xml .
RUN mvn -B dependency:go-offline

# Build application fat jar (skipping tests since Testcontainers requires host Docker daemon).
# Spotless is also skipped here: Windows CRLF checkouts fail the format check inside the
# Linux container, and formatting remains gated by ./mvnw verify on the host/CI.
COPY src ./src
RUN mvn -B -DskipTests -Dspotless.check.skip=true clean package

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
  CMD wget --quiet --tries=1 --spider http://127.0.0.1:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
