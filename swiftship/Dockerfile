FROM eclipse-temurin:17-jre-alpine AS builder

WORKDIR /app

# Add application
COPY target/*.jar app.jar

# Extract layers for better caching
RUN java -Djarmode=layertools -jar app.jar extract

# Final stage
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Create non-root user
RUN addgroup -g 1000 -S swiftship && \
    adduser -u 1000 -S swiftship -G swiftship

# Copy extracted layers
COPY --from=builder /app/dependencies/ ./
COPY --from=builder /app/spring-boot-loader/ ./
COPY --from=builder /app/snapshot-dependencies/ ./
COPY --from=builder /app/application/ ./

# Install curl for health checks
RUN apk --no-cache add curl

# Set user
USER swiftship:swiftship

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health/liveness || exit 1

# Expose port
EXPOSE 8080

# JVM options
ENV JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 \
    -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS org.springframework.boot.loader.JarLauncher"]