# Quick Start Guide - Kafka to DynamoDB Service

## Build & Run Commands

### Fast Build (Skip Tests & OWASP)
```bash
mvn clean install -DskipTests
```

### Full Build with Tests
```bash
mvn clean test
```

### Security Scan (First run takes ~25 min to download NVD database)
```bash
mvn dependency-check:check -Dowasp.skip=false
```

### Run Service Locally
```bash
cd kafka-to-pv-dynamo-service
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### Package for Production
```bash
mvn clean package -DskipTests
java -jar kafka-to-pv-dynamo-service/target/kafka-to-pv-dynamo-service.jar --spring.profiles.active=prod
```

---

## Project Structure

```
bigdata/
├── app-config-data/              # Shared configuration POJOs
│   └── DynamoDBConfigData.java   # ✅ FIXED: Was "DynamonDBConfigData"
├── dynamo-config/                # DynamoDB client configuration
│   └── DynamoDBConfig.java       # AWS SDK v2 async client
├── kafka-consumer-config/        # Kafka consumer setup
│   └── KafkaConsumerConfig.java  # Batch listener config
├── kafka-to-pv-dynamo-service/   # Main application
│   ├── KafkaToPvDynamoConsumer.java   # ✅ REFACTORED: Simplified filter() method
│   ├── DynamoDBBatchService.java      # Batch write operations
│   ├── application.yaml          # Base configuration
│   ├── application-dev.yaml      # ✅ NEW: Dev logging config
│   └── application-prod.yaml     # ✅ NEW: Prod logging config
└── pom.xml                       # ✅ UPDATED: Added OWASP plugin
```

---

## Recent Code Changes (2026-01-09)

### 1. Class Rename
- **Before**: `DynamonDBConfigData.java`
- **After**: `DynamoDBConfigData.java`
- **Impact**: All references updated across modules

### 2. Refactored Consumer Logic
[KafkaToPvDynamoConsumer.java](../kafka-to-pv-dynamo-service/src/main/java/com/julian/razif/figaro/bigdata/consumer/KafkaToPvDynamoConsumer.java) now has cleaner structure:

```java
// Old: 68-line monolithic filter() method
// New: 5 focused methods
private JsonObject filter(String message)
private boolean isValidMessageSize(String message)
private JsonObject parseAndValidateJson(String message)
private boolean isValidMessageStructure(JsonObject json)
private JsonObject enrichWithTimestamp(JsonObject json)
```

### 3. Logging Configuration
- **Dev**: DEBUG level logging
- **Prod**: INFO level with file rotation (10MB max, 30 days retention)

### 4. Security Scanning
OWASP Dependency Check plugin added (disabled by default for speed)

---

## Configuration Reference

### Environment Variables (Required)

```bash
# Application Config
APP_CONFIG_DATA_MAX_MESSAGE_SIZE=262144
APP_CONFIG_DATA_MAX_JSON_DEPTH=10

# Kafka Config
KAFKA_CONSUMER_CONFIG_TOPIC_NAME=user-pv-tracking-events
KAFKA_CONSUMER_CONFIG_CONSUMER_GROUP_ID=kafka-to-pv-dynamo-consumer-group
KAFKA_CONSUMER_CONFIG_BOOTSTRAP_SERVERS=localhost:9092
KAFKA_CONSUMER_CONFIG_BATCH_LISTENER=true
KAFKA_CONSUMER_CONFIG_AUTO_STARTUP=true
KAFKA_CONSUMER_CONFIG_CONCURRENCY_LEVEL=3
KAFKA_CONSUMER_CONFIG_SESSION_TIMEOUT_MS=10000
KAFKA_CONSUMER_CONFIG_HEARTBEAT_INTERVAL_MS=3000
KAFKA_CONSUMER_CONFIG_MAX_POLL_INTERVAL_MS=300000
KAFKA_CONSUMER_CONFIG_MAX_POLL_RECORDS=50
KAFKA_CONSUMER_CONFIG_MAX_PARTITION_FETCH_BYTES_DEFAULT=1048576
KAFKA_CONSUMER_CONFIG_MAX_PARTITION_FETCH_BYTES_BOOST_FACTOR=1

# DynamoDB Config
DYNAMO_CONFIG_TABLE_NAME=user-pv-tracking
DYNAMO_CONFIG_TABLE_PARTITION_KEY=id
DYNAMO_CONFIG_TABLE_SORT_KEY=date
DYNAMO_CONFIG_AWS_REGION=ap-southeast-1
DYNAMO_CONFIG_AWS_ACCESS_KEY_ID=test
DYNAMO_CONFIG_AWS_SECRET_ACCESS_KEY=test
DYNAMO_CONFIG_BATCH_WRITE_SIZE=25
DYNAMO_CONFIG_EXPONENTIAL_BACKOFF_BASE_DELAY_MS=100
DYNAMO_CONFIG_EXPONENTIAL_BACKOFF_MAX_BACKOFF_MS=20000
DYNAMO_CONFIG_EXPONENTIAL_BACKOFF_MAX_ATTEMPTS=3
DYNAMO_CONFIG_FIXED_DELAY_RETRY_DELAY_MS=100
DYNAMO_CONFIG_FIXED_DELAY_RETRY_MAX_ATTEMPTS=3
DYNAMO_CONFIG_THROTTLING_BACKOFF_BASE_DELAY_MS=500
DYNAMO_CONFIG_THROTTLING_BACKOFF_MAX_BACKOFF_MS=10000
DYNAMO_CONFIG_THROTTLING_BACKOFF_MAX_ATTEMPTS=5
```

### Spring Profiles

- **default**: Base configuration
- **dev**: Development (verbose logging, local endpoints)
- **prod**: Production (INFO logging, file rotation)

---

## Common Tasks

### Add New Validation Rule

Edit [KafkaToPvDynamoConsumer.java:58](../kafka-to-pv-dynamo-service/src/main/java/com/julian/razif/figaro/bigdata/consumer/KafkaToPvDynamoConsumer.java#L58):

```java
private boolean isValidMessageStructure(JsonObject json) {
    return json.has("id")
        && json.has("username")
        && json.has("user_id")
        && json.has("data")
        && json.has("action")
        && json.get("action").getAsString().equals("page_view")
        && json.get("data").isJsonObject()
        // ADD NEW VALIDATION HERE
        && yourNewValidation(json);
}
```

### Modify Batch Size

Update [application.yaml](../kafka-to-pv-dynamo-service/src/main/resources/application.yaml):

```yaml
dynamo-config:
  batch-write-size: 25  # Change this value (max 25 for DynamoDB)
```

### Change Kafka Consumer Concurrency

Update [application.yaml](../kafka-to-pv-dynamo-service/src/main/resources/application.yaml):

```yaml
kafka-consumer-config:
  concurrency-level: 3  # Number of consumer threads
```

---

## Troubleshooting

### Build Fails with "Class not found: DynamonDBConfigData"

**Cause**: Stale compiled artifacts  
**Fix**:
```bash
mvn clean install -DskipTests
```

### Tests Fail with Mockito Error

**Cause**: Known issue with Mockito + Java 25 (under investigation)  
**Workaround**: Skip tests during build
```bash
mvn clean install -DskipTests
```

### OWASP Scan Takes Too Long

**Cause**: First run downloads NVD database (~326k records)  
**Fix**: Skip for regular builds
```bash
mvn clean install  # OWASP skipped by default
```

To enable when needed:
```bash
mvn install -Dowasp.skip=false
```

### OutOfMemory Error During Build

**Fix**: Increase Maven heap size
```bash
export MAVEN_OPTS="-Xmx2g"
mvn clean install
```

---

## Testing Locally

### 1. Start Infrastructure (Docker)

```bash
docker-compose up -d
```

This starts:
- Kafka (localhost:9092)
- DynamoDB Local (localhost:8000)
- Zookeeper

### 2. Run Service

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### 3. Send Test Message

```bash
kafka-console-producer --broker-list localhost:9092 --topic user-pv-tracking-events

# Paste this JSON:
{
  "id": "test-123",
  "username": "testuser",
  "user_id": "user-456",
  "data": {"page": "home"},
  "action": "page_view"
}
```

### 4. Verify DynamoDB Write

```bash
aws dynamodb scan \
  --table-name user-pv-tracking \
  --endpoint-url http://localhost:8000
```

---

## Performance Tuning

### High Throughput Scenario

```yaml
kafka-consumer-config:
  concurrency-level: 10        # More threads
  max-poll-records: 100        # Larger batches
  
dynamo-config:
  batch-write-size: 25         # Max allowed
  throttling-backoff-max-attempts: 10  # More retries
```

### Low Latency Scenario

```yaml
kafka-consumer-config:
  concurrency-level: 1         # Single thread
  max-poll-records: 10         # Smaller batches
  
dynamo-config:
  batch-write-size: 10         # Smaller writes
```

---

## Monitoring & Metrics

Metrics are exposed via Micrometer at:
```
http://localhost:8080/actuator/metrics
http://localhost:8080/actuator/prometheus  # For Prometheus scraping
```

Key metrics:
- `kafka.consumer.records.consumed.total`
- `dynamodb.batch.write.success.total`
- `message.filter.rejected.total`

---

## Next Steps (For New Developers)

1. Read [CODE_QUALITY_REPORT.md](CODE_QUALITY_REPORT.md) for design decisions
2. Review [SECURITY_REVIEW.md](SECURITY_REVIEW.md) for security considerations
3. Check [RECOMMENDED_IMPROVEMENTS.md](RECOMMENDED_IMPROVEMENTS.md) for future tasks
4. Fix test infrastructure (see [IMPLEMENTATION_COMPLETE.md](IMPLEMENTATION_COMPLETE.md#test-status))

---

## Code Review Checklist

Before submitting PR:

- [ ] Code compiles: `mvn clean compile`
- [ ] Tests pass: `mvn test` (once test infra is fixed)
- [ ] No security vulnerabilities: `mvn dependency-check:check -Dowasp.skip=false`
- [ ] Code formatted (Google Java Style)
- [ ] Logging uses correct level (DEBUG/INFO/WARN/ERROR)
- [ ] Configuration externalized (no hardcoded values)
- [ ] Documentation updated

---

## Useful Links

- [Spring Boot 4.0 Docs](https://docs.spring.io/spring-boot/docs/4.0.x/reference/html/)
- [AWS SDK v2 for Java](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/home.html)
- [Kafka Streams Docs](https://kafka.apache.org/documentation/streams/)
- [OWASP Dependency Check](https://jeremylong.github.io/DependencyCheck/dependency-check-maven/)

---

**Last Updated**: 2026-01-09  
**Build Status**: ✅ SUCCESS  
**Version**: 0.0.1-SNAPSHOT
