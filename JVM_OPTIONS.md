# JVM Configuration Guide for Production

This document provides comprehensive JVM tuning options for high-throughput Kafka to DynamoDB microservices running on Java 25 with virtual threads.

## Table of Contents

1. [Garbage Collection Options](#garbage-collection-options)
2. [Memory Configuration](#memory-configuration)
3. [Virtual Thread Monitoring](#virtual-thread-monitoring)
4. [Deployment Examples](#deployment-examples)
5. [Performance Tuning](#performance-tuning)
6. [Monitoring and Diagnostics](#monitoring-and-diagnostics)

---

## Garbage Collection Options

### Option 1: ZGC (Recommended for Low-Latency Requirements)

**Z Garbage Collector** is a scalable, low-latency garbage collector designed for applications requiring consistent sub-10ms pause times.

#### Key Features
- **Pause Times:** Sub-10ms regardless of heap size (tested up to 16TB heaps)
- **Concurrent:** Performs all expensive work concurrently with application threads
- **Generational Mode:** Java 21+ introduces generational ZGC for better throughput
- **Region-Based:** Divides heap into regions for concurrent compaction

#### JVM Options

```bash
# Core ZGC Options
-XX:+UseZGC                          # Enable ZGC
-XX:+ZGenerational                   # Enable generational mode (Java 21+, default in Java 23+)
-XX:ZUncommitDelay=300               # Uncommit unused memory after 300 seconds
-Xmx8g                               # Maximum heap size (adjust based on workload)
-Xms4g                               # Initial heap size (50% of max recommended)

# String Optimizations
-XX:+UseStringDeduplication          # Deduplicate identical strings
-XX:+OptimizeStringConcat            # Optimize string concatenation

# Diagnostics
-XX:+HeapDumpOnOutOfMemoryError      # Create heap dump on OOM
-XX:HeapDumpPath=/var/log/heapdump.hprof
```

#### When to Use ZGC
- ✅ Ultra-low latency requirements (< 10ms pause times)
- ✅ Large heap sizes (> 4GB)
- ✅ Real-time or near-real-time applications
- ✅ Services with strict SLA requirements
- ✅ High-throughput streaming applications

#### Performance Characteristics
| Metric | Value |
|--------|-------|
| Max Pause Time | < 10ms |
| Heap Size Support | 8MB - 16TB |
| Throughput Overhead | 5-15% vs G1GC |
| CPU Overhead | Moderate (concurrent operations) |

---

### Option 2: G1GC (Recommended for Balanced Throughput/Latency)

**Garbage First Garbage Collector** is the default GC in Java 9+, designed for predictable pause times with excellent throughput.

#### Key Features
- **Predictable Pause Times:** Target-based pause time goals
- **Region-Based:** Divides heap into regions for incremental collection
- **Generational:** Separates young and old generations
- **Automatic Tuning:** Self-adjusts to meet pause time targets

#### JVM Options

```bash
# Core G1GC Options
-XX:+UseG1GC                         # Enable G1GC (default in Java 9+)
-XX:MaxGCPauseMillis=200             # Target max pause time (milliseconds)
-XX:G1HeapRegionSize=16m             # Region size (auto-calculated if omitted)
-XX:InitiatingHeapOccupancyPercent=45 # Trigger concurrent cycle at 45% heap occupancy
-Xmx8g                               # Maximum heap size
-Xms4g                               # Initial heap size

# Young Generation Tuning
-XX:G1NewSizePercent=30              # Minimum size of young generation (% of heap)
-XX:G1MaxNewSizePercent=60           # Maximum size of young generation (% of heap)

# String Optimizations
-XX:+UseStringDeduplication          # Deduplicate identical strings
-XX:+OptimizeStringConcat            # Optimize string concatenation

# Diagnostics
-XX:+HeapDumpOnOutOfMemoryError
-XX:HeapDumpPath=/var/log/heapdump.hprof
```

#### When to Use G1GC
- ✅ Balanced latency and throughput requirements
- ✅ Medium to large heap sizes (2GB - 64GB)
- ✅ Predictable pause times (50-200ms acceptable)
- ✅ Batch processing with soft real-time constraints
- ✅ General-purpose microservices

#### Performance Characteristics
| Metric | Value |
|--------|-------|
| Target Pause Time | 50-200ms (configurable) |
| Heap Size Support | 2GB - 64GB (optimal) |
| Throughput | Excellent (5-10% better than ZGC) |
| CPU Overhead | Low to moderate |

---

### GC Comparison Matrix

| Feature | ZGC | G1GC |
|---------|-----|------|
| **Max Pause Time** | < 10ms | 50-200ms |
| **Heap Size** | 8MB - 16TB | 2GB - 64GB |
| **Throughput** | Good | Excellent |
| **CPU Usage** | Moderate | Low-Moderate |
| **Memory Overhead** | ~2-3% | ~1-2% |
| **Concurrent Operations** | Maximum | High |
| **Tuning Complexity** | Low | Medium |
| **Best For** | Low-latency apps | Balanced workloads |

---

## Memory Configuration

### Heap Sizing Guidelines

#### For Kafka Consumer Applications

```bash
# Small Scale (< 1000 msg/sec)
-Xmx2g -Xms1g

# Medium Scale (1000-10000 msg/sec)
-Xmx4g -Xms2g

# High Scale (> 10000 msg/sec) - Recommended for Production
-Xmx8g -Xms4g

# Very High Scale (> 50000 msg/sec)
-Xmx16g -Xms8g
```

#### Heap Sizing Best Practices

1. **Initial Size (Xms):** Set to 50% of max heap to reduce early garbage collections
2. **Maximum Size (Xmx):** Leave 25-30% of system memory for OS and off-heap
3. **Container Memory:** Set container limit to 150% of Xmx (e.g., 12GB limit for 8GB heap)

### Off-Heap Memory Considerations

Java virtual threads and NIO buffers consume off-heap memory:

```bash
# MetaSpace (class metadata)
-XX:MetaspaceSize=256m               # Initial metaspace size
-XX:MaxMetaspaceSize=512m            # Maximum metaspace size

# Direct Memory (NIO buffers, virtual threads)
-XX:MaxDirectMemorySize=2g           # Maximum direct memory allocation
```

### Memory Compression

```bash
# Reduce memory footprint (enabled by default on 64-bit JVMs)
-XX:+UseCompressedOops                # Compress object pointers (< 32GB heap)
-XX:+UseCompressedClassPointers       # Compress class pointers
```

---

## Virtual Thread Monitoring

### Java 21+ Virtual Thread Options

```bash
# Pinned Thread Detection (Debug Mode)
-Djdk.tracePinnedThreads=full        # Log all pinned virtual threads with stack traces
-Djdk.tracePinnedThreads=short       # Log pinned threads without stack traces

# Virtual Thread Pool Sizing (if using pooled executor)
-Djdk.virtualThreadScheduler.parallelism=8  # Default: Runtime.availableProcessors()
-Djdk.virtualThreadScheduler.maxPoolSize=256 # Default: 256
```

### Monitoring Virtual Threads

Use JFR (Java Flight Recorder) to monitor virtual thread activity:

```bash
# Enable JFR with virtual thread events
-XX:+FlightRecorder
-XX:StartFlightRecording=duration=300s,filename=/var/log/recording.jfr,settings=profile
-XX:FlightRecorderOptions=stackdepth=128
```

### Virtual Thread Best Practices

✅ **Do:**
- Use for I/O-bound operations (database, HTTP, file I/O)
- Let JVM manage thread count (unbounded executors)
- Use ReentrantLock instead of synchronized blocks
- Profile for pinning issues in production

❌ **Don't:**
- Use for CPU-bound operations (use ForkJoinPool instead)
- Use synchronized blocks with long critical sections (causes pinning)
- Call native methods that hold monitor locks
- Pool virtual threads (defeats the purpose)

---

## Deployment Examples

### 1. Local Development (mvnw)

Create `.mvn/jvm.config` file:

```bash
# .mvn/jvm.config
-XX:+UseZGC
-XX:+ZGenerational
-Xmx4g
-Xms2g
-XX:+UseStringDeduplication
-XX:+HeapDumpOnOutOfMemoryError
-XX:HeapDumpPath=./logs/heapdump.hprof
-Djdk.tracePinnedThreads=short
```

Run with Maven:

```bash
./mvnw spring-boot:run
```

---

### 2. Docker Container

#### Dockerfile

```dockerfile
FROM eclipse-temurin:25-jre-alpine

# Set JVM options
ENV JAVA_TOOL_OPTIONS="\
-XX:+UseZGC \
-XX:+ZGenerational \
-Xmx8g \
-Xms4g \
-XX:+UseStringDeduplication \
-XX:+OptimizeStringConcat \
-XX:+HeapDumpOnOutOfMemoryError \
-XX:HeapDumpPath=/var/log/heapdump.hprof \
-XX:+UseCompressedOops \
-XX:+AlwaysPreTouch \
-Djava.security.egd=file:/dev/./urandom \
-Djdk.tracePinnedThreads=short"

COPY target/kafka-to-pv-dynamo-service.jar /app/app.jar

WORKDIR /app
ENTRYPOINT ["java", "-jar", "app.jar"]
```

#### Docker Compose

```yaml
version: '3.8'
services:
  kafka-to-dynamo:
    image: kafka-to-pv-dynamo-service:latest
    container_name: kafka-to-dynamo
    environment:
      JAVA_TOOL_OPTIONS: >
        -XX:+UseZGC
        -XX:+ZGenerational
        -Xmx8g
        -Xms4g
        -XX:+UseStringDeduplication
        -Djdk.tracePinnedThreads=short
      SPRING_PROFILES_ACTIVE: prod
    mem_limit: 12g           # 150% of heap size
    mem_reservation: 10g      # Minimum memory
    cpus: 4                   # CPU limit
    volumes:
      - ./logs:/var/log
```

---

### 3. Kubernetes Deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: kafka-to-pv-dynamo-service
spec:
  replicas: 3
  template:
    spec:
      containers:
      - name: app
        image: kafka-to-pv-dynamo-service:2.0
        env:
        - name: JAVA_TOOL_OPTIONS
          value: >
            -XX:+UseZGC
            -XX:+ZGenerational
            -Xmx8g
            -Xms4g
            -XX:+UseStringDeduplication
            -XX:+OptimizeStringConcat
            -XX:+HeapDumpOnOutOfMemoryError
            -XX:HeapDumpPath=/var/log/heapdump.hprof
            -XX:+AlwaysPreTouch
            -Djdk.tracePinnedThreads=short
        - name: SPRING_PROFILES_ACTIVE
          value: "prod"
        resources:
          requests:
            memory: "10Gi"
            cpu: "2000m"
          limits:
            memory: "12Gi"
            cpu: "4000m"
        volumeMounts:
        - name: logs
          mountPath: /var/log
      volumes:
      - name: logs
        emptyDir: {}
```

---

### 4. AWS ECS/Fargate

```json
{
  "family": "kafka-to-pv-dynamo-service",
  "containerDefinitions": [
    {
      "name": "app",
      "image": "kafka-to-pv-dynamo-service:2.0",
      "memory": 12288,
      "cpu": 4096,
      "environment": [
        {
          "name": "JAVA_TOOL_OPTIONS",
          "value": "-XX:+UseZGC -XX:+ZGenerational -Xmx8g -Xms4g -XX:+UseStringDeduplication -Djdk.tracePinnedThreads=short"
        },
        {
          "name": "SPRING_PROFILES_ACTIVE",
          "value": "prod"
        }
      ],
      "logConfiguration": {
        "logDriver": "awslogs",
        "options": {
          "awslogs-group": "/ecs/kafka-to-dynamo",
          "awslogs-region": "us-east-1",
          "awslogs-stream-prefix": "ecs"
        }
      }
    }
  ]
}
```

---

## Performance Tuning

### Startup Optimization

```bash
# Faster Startup (Spring Boot 4)
-Dspring.main.lazy-initialization=true        # Lazy bean initialization
-XX:TieredStopAtLevel=1                       # Faster startup (C1 compiler only)
-XX:+TieredCompilation                        # Enable tiered compilation
-noverify                                     # Skip bytecode verification (use with caution)

# Class Data Sharing (CDS) for faster startup
# Step 1: Generate class list
java -Xshare:off -XX:DumpLoadedClassList=classes.lst -jar app.jar

# Step 2: Create shared archive
java -Xshare:dump -XX:SharedClassListFile=classes.lst -XX:SharedArchiveFile=app-cds.jsa

# Step 3: Use shared archive
-XX:SharedArchiveFile=app-cds.jsa -Xshare:on
```

### Throughput Optimization

```bash
# Maximize Throughput (G1GC)
-XX:+UseG1GC
-XX:MaxGCPauseMillis=500                     # Higher pause time tolerance
-XX:G1HeapRegionSize=32m                     # Larger regions
-XX:ParallelGCThreads=8                      # Parallel GC threads (CPU cores)
-XX:ConcGCThreads=2                          # Concurrent GC threads (25% of parallel)

# Aggressive Optimizations
-XX:+AggressiveOpts                          # Enable experimental optimizations
-XX:+UseStringCache                          # Cache string literals
```

### Latency Optimization

```bash
# Minimize Latency (ZGC)
-XX:+UseZGC
-XX:+ZGenerational
-XX:ZCollectionInterval=5                    # Force GC every 5 seconds
-XX:+AlwaysPreTouch                          # Pre-touch all pages at startup
-XX:+DisableExplicitGC                       # Ignore System.gc() calls
```

---

## Monitoring and Diagnostics

### GC Logging

```bash
# Unified GC Logging (Java 9+)
-Xlog:gc*:file=/var/log/gc.log:time,level,tags:filecount=10,filesize=50M

# Detailed GC Logging
-Xlog:gc*=debug:file=/var/log/gc-debug.log:time,level,tags

# ZGC Specific Logging
-Xlog:gc,gc+init,gc+heap,gc+phases:file=/var/log/zgc.log:time
```

### JMX Monitoring

```bash
# Enable JMX
-Dcom.sun.management.jmxremote
-Dcom.sun.management.jmxremote.port=9010
-Dcom.sun.management.jmxremote.local.only=false
-Dcom.sun.management.jmxremote.authenticate=false
-Dcom.sun.management.jmxremote.ssl=false
```

### Java Flight Recorder (JFR)

```bash
# Continuous Recording
-XX:StartFlightRecording=disk=true,duration=24h,filename=/var/log/recording.jfr,maxsize=1G

# On-Demand Recording
jcmd <pid> JFR.start duration=60s filename=/tmp/recording.jfr
```

### Heap Dump Analysis

```bash
# Automatic Heap Dump on OOM
-XX:+HeapDumpOnOutOfMemoryError
-XX:HeapDumpPath=/var/log/heapdump.hprof

# Manual Heap Dump
jcmd <pid> GC.heap_dump /tmp/heapdump.hprof

# Analyze with VisualVM, Eclipse MAT, or JProfiler
```

---

## Recommended Configurations by Environment

### Development

```bash
-XX:+UseZGC
-Xmx2g
-Xms1g
-Djdk.tracePinnedThreads=full
-Dspring.main.lazy-initialization=true
```

### Staging

```bash
-XX:+UseZGC
-XX:+ZGenerational
-Xmx4g
-Xms2g
-XX:+UseStringDeduplication
-XX:+HeapDumpOnOutOfMemoryError
-XX:HeapDumpPath=/var/log/heapdump.hprof
-Djdk.tracePinnedThreads=short
```

### Production (Low-Latency)

```bash
-XX:+UseZGC
-XX:+ZGenerational
-XX:ZUncommitDelay=300
-Xmx8g
-Xms4g
-XX:+UseStringDeduplication
-XX:+OptimizeStringConcat
-XX:+HeapDumpOnOutOfMemoryError
-XX:HeapDumpPath=/var/log/heapdump.hprof
-XX:+UseCompressedOops
-XX:+AlwaysPreTouch
-Djava.security.egd=file:/dev/./urandom
-Xlog:gc*:file=/var/log/gc.log:time:filecount=10,filesize=50M
```

### Production (High-Throughput)

```bash
-XX:+UseG1GC
-XX:MaxGCPauseMillis=200
-XX:G1HeapRegionSize=16m
-XX:InitiatingHeapOccupancyPercent=45
-Xmx8g
-Xms4g
-XX:+UseStringDeduplication
-XX:+OptimizeStringConcat
-XX:+HeapDumpOnOutOfMemoryError
-XX:HeapDumpPath=/var/log/heapdump.hprof
-XX:+UseCompressedOops
-Djava.security.egd=file:/dev/./urandom
-Xlog:gc*:file=/var/log/gc.log:time:filecount=10,filesize=50M
```

---

## Performance Testing Checklist

- [ ] Load test with production-like data volume
- [ ] Monitor GC pause times and frequency
- [ ] Check for virtual thread pinning events
- [ ] Measure throughput (messages/second)
- [ ] Monitor heap usage patterns
- [ ] Validate 99th percentile latency
- [ ] Test under sustained high load (1+ hours)
- [ ] Simulate OOM scenarios
- [ ] Review GC logs for anomalies
- [ ] Profile with JFR for hotspots

---

## References

- [JEP 444: Virtual Threads](https://openjdk.org/jeps/444)
- [JEP 439: Generational ZGC](https://openjdk.org/jeps/439)
- [G1GC Tuning Guide](https://docs.oracle.com/en/java/javase/21/gctuning/garbage-first-g1-garbage-collector1.html)
- [ZGC Tuning Guide](https://wiki.openjdk.org/display/zgc/Main)
- [Spring Boot 4 Documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/)
