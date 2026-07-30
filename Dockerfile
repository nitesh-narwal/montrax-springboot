# Simple Dockerfile that uses pre-built JAR
# Build JAR locally first: ./mvnw clean package -DskipTests
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Create non-root user for security
RUN addgroup -g 1001 -S appgroup && \
    adduser -u 1001 -S appuser -G appgroup

# Copy the pre-built JAR
COPY target/moneymanager-0.0.1-SNAPSHOT.jar app.jar

# Change ownership to non-root user
RUN chown -R appuser:appgroup /app

USER appuser

# Expose the application port
EXPOSE 8090

# Health check (uses actuator health endpoint)
HEALTHCHECK --interval=30s --timeout=10s --start-period=120s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8090/actuator/health || exit 1

# JVM optimization for Spring Boot + Hibernate
# -Xmx384m: Max heap size 384MB
# -Xms256m: Initial heap size 256MB
# -XX:MaxMetaspaceSize=256m: Increased for Hibernate/Spring/Lombok classes
# -XX:+UseSerialGC: Serial GC for low memory footprint
ENV JAVA_OPTS="-Xmx384m -Xms256m -XX:MaxMetaspaceSize=256m -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom"

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
