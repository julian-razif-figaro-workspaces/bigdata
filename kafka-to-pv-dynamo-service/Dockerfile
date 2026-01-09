# Multi-stage build for optimized Spring Boot application
# Stage 1: Build stage (optional, using pre-built JAR from Maven)
FROM eclipse-temurin:25-jre-alpine AS runtime

# Set metadata
LABEL maintainer="julian.razif@figaro.com"
LABEL application="kafka-to-pv-dynamo-service"
LABEL version="1.0.0"

# Create application directory and non-root user for security
RUN addgroup -S spring && adduser -S spring -G spring && \
    mkdir -p /app /var/log && \
    chown -R spring:spring /app /var/log

# Set working directory
WORKDIR /app

# Copy the built JAR file from Maven target directory
COPY --chown=spring:spring target/kafka-to-pv-dynamo-service.jar app.jar

# Configure JVM options for optimal performance with Java 25 and Virtual Threads
# Using ZGC (Z Garbage Collector) for low-latency and high-throughput
ENV JAVA_TOOL_OPTIONS="\
-XX:+UseZGC \
-XX:+ZGenerational \
-Xmx8g \
-Xms4g \
-XX:MaxRAMPercentage=75.0 \
-XX:InitialRAMPercentage=50.0 \
-XX:+UseStringDeduplication \
-XX:+OptimizeStringConcat \
-XX:+HeapDumpOnOutOfMemoryError \
-XX:HeapDumpPath=/var/log/heapdump.hprof \
-XX:+UseCompressedOops \
-XX:+UseCompressedClassPointers \
-XX:+AlwaysPreTouch \
-Djava.security.egd=file:/dev/./urandom \
-Djdk.tracePinnedThreads=short \
-XX:+UnlockDiagnosticVMOptions \
-XX:+DebugNonSafepoints \
-Dcom.sun.management.jmxremote=true \
-Dcom.sun.management.jmxremote.port=9090 \
-Dcom.sun.management.jmxremote.rmi.port=9090 \
-Dcom.sun.management.jmxremote.authenticate=false \
-Dcom.sun.management.jmxremote.ssl=false \
-Djava.rmi.server.hostname=localhost"

# Expose application port and JMX monitoring port
EXPOSE 8087 9090

# Switch to non-root user
USER spring:spring

# Health check to ensure service is running
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8087/actuator/health || exit 1

# Use exec form for proper signal handling
ENTRYPOINT ["java", "-jar", "app.jar"]

