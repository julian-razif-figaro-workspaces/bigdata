# Recommended Code Improvements

**Project:** BigData Kafka to DynamoDB Service  
**Priority:** High to Low  
**Status:** Actionable Recommendations

This document provides specific, actionable code improvements based on the comprehensive code review completed on January 9, 2026.

---

## 🔴 High Priority (Before Production)

### 1. Fix Typo in Class Name ⚠️

**Issue:** Configuration class has typo: `DynamonDBConfigData` should be `DynamoDBConfigData`

**Impact:** Medium (naming consistency, documentation accuracy)

**Files to Change:**
- `app-config-data/src/main/java/com/julian/razif/figaro/bigdata/appconfig/DynamonDBConfigData.java`
- All references in other files

**Implementation:**
```java
// BEFORE (app-config-data/src/main/java/.../DynamonDBConfigData.java)
@ConfigurationProperties(prefix = "dynamo-config-data")
public record DynamonDBConfigData(...) { }

// AFTER (rename file to DynamoDBConfigData.java)
@ConfigurationProperties(prefix = "dynamo-config-data")
public record DynamoDBConfigData(...) { }
```

**Steps:**
1. Rename file: `DynamonDBConfigData.java` → `DynamoDBConfigData.java`
2. Update all imports across the codebase
3. Run tests to ensure no breakage
4. Update documentation

**Estimated Time:** 15 minutes

---

### 2. Refactor Complex `filter()` Method ⚠️

**Issue:** The `filter()` method in `KafkaToPvDynamoConsumer` is 60+ lines with high complexity

**Impact:** High (maintainability, readability, testability)

**File:** `kafka-to-pv-dynamo-service/src/main/java/com/julian/razif/figaro/bigdata/consumer/KafkaToPvDynamoConsumer.java`

**Current Method (Lines 331-398):**
```java
public CompletableFuture<JsonObject> filter(String message) {
  Timer.Sample sample = Timer.start();
  
  return CompletableFuture.supplyAsync(() -> {
    // 60+ lines of complex validation logic
    // Multiple nested conditions
    // Validation mixed with business logic
  });
}
```

**Recommended Refactoring:**

```java
// New structure - KafkaToPvDynamoConsumer.java

public CompletableFuture<JsonObject> filter(String message) {
  Timer.Sample sample = Timer.start();
  
  return CompletableFuture.supplyAsync(() -> {
    // Fast-fail validations
    if (!isValidMessageSize(message)) {
      return emptyJson();
    }
    
    JsonObject parsed = parseAndValidateJson(message);
    if (parsed.isJsonNull()) {
      return emptyJson();
    }
    
    if (!isValidMessageStructure(parsed)) {
      return emptyJson();
    }
    
    return enrichWithTimestamp(parsed, sample);
  });
}

/**
 * Validates message size constraints.
 * 
 * @param message raw message string
 * @return true if size is within limits
 */
private boolean isValidMessageSize(String message) {
  if (message == null || message.isEmpty()) {
    logger.debug("Received null or empty message, skipping");
    return false;
  }
  
  if (message.length() > MAX_JSON_SIZE) {
    logger.warn("Message size {} exceeds maximum allowed size {}, rejecting",
      message.length(), MAX_JSON_SIZE);
    return false;
  }
  
  return true;
}

/**
 * Parses and validates JSON syntax and structure.
 * 
 * @param message raw JSON message
 * @return parsed JsonObject or empty JsonObject if invalid
 */
private JsonObject parseAndValidateJson(String message) {
  try {
    JsonObject objectMessage = gson.fromJson(message, JsonElement.class).getAsJsonObject();
    
    // Validate JSON depth to prevent deeply nested attacks
    if (getJsonDepth(objectMessage) > MAX_JSON_DEPTH) {
      logger.warn("JSON depth exceeds maximum allowed depth {}, rejecting", MAX_JSON_DEPTH);
      return new JsonObject();
    }
    
    return objectMessage;
  } catch (JsonSyntaxException ex) {
    logger.debug("Invalid JSON syntax, skipping message", ex);
    return new JsonObject();
  }
}

/**
 * Validates message structure and required fields.
 * 
 * @param objectMessage parsed JSON object
 * @return true if structure is valid
 */
private boolean isValidMessageStructure(JsonObject objectMessage) {
  if (objectMessage.isJsonNull()) {
    return false;
  }
  
  // Validate action type
  if (!objectMessage.has(FIELD_ACT) || 
      !Objects.equals(objectMessage.get(FIELD_ACT).getAsString(), ACTION_BIG_DATA_SESSION)) {
    logger.debug("Invalid or missing action type");
    return false;
  }
  
  // Validate data object
  if (!objectMessage.has(FIELD_DATA) || objectMessage.get(FIELD_DATA).isJsonNull()) {
    logger.debug("Missing or null data field");
    return false;
  }
  
  JsonObject data = objectMessage.get(FIELD_DATA).getAsJsonObject();
  
  // Validate required fields
  if (data.isJsonNull() || 
      !data.has(FIELD_ID) || 
      !data.has(FIELD_USER_ID) || 
      !data.has(FIELD_MEMBER_USERNAME)) {
    logger.debug("Message missing required fields, skipping");
    return false;
  }
  
  return true;
}

/**
 * Enriches message with timestamp and returns.
 * 
 * @param objectMessage valid parsed message
 * @param sample timer sample for metrics
 * @return enriched JsonObject
 */
private JsonObject enrichWithTimestamp(JsonObject objectMessage, Timer.Sample sample) {
  JsonObject data = objectMessage.get(FIELD_DATA).getAsJsonObject();
  long currentDateMilliseconds = System.currentTimeMillis();
  data.addProperty(FIELD_DATE, currentDateMilliseconds);
  
  logger.debug("Filtered and validated message successfully");
  sample.stop(jsonFilteringTimer);
  
  return objectMessage;
}

/**
 * Returns empty JsonObject for filtered messages.
 * 
 * @return empty JsonObject
 */
private JsonObject emptyJson() {
  return new JsonObject();
}
```

**Benefits:**
1. ✅ Each method has single responsibility
2. ✅ Easier to test individual validation steps
3. ✅ Better readability and maintainability
4. ✅ Clear separation of concerns
5. ✅ Reduced cognitive complexity

**Estimated Time:** 2 hours

---

### 3. Add Profile-Specific Logging Configuration ⚠️

**Issue:** Debug logs might expose sensitive data in production

**Impact:** Medium (security, information disclosure)

**File:** `kafka-to-pv-dynamo-service/src/main/resources/application-prod.yaml`

**Implementation:**

```yaml
# Add to application-prod.yaml

logging:
  level:
    root: INFO
    com.julian.razif.figaro: INFO  # Disable DEBUG in production
    org.springframework: WARN
    org.apache.kafka: WARN
    software.amazon.awssdk: WARN
  
  pattern:
    console: "%d{ISO8601} [%thread] %-5level %logger{36} - %msg%n"
  
  file:
    name: /var/log/kafka-dynamo-service/application.log
    max-size: 100MB
    max-history: 30
    total-size-cap: 3GB
```

```yaml
# Add to application-dev.yaml

logging:
  level:
    root: INFO
    com.julian.razif.figaro: DEBUG  # Allow DEBUG in dev
    org.springframework: INFO
    org.apache.kafka: INFO
    software.amazon.awssdk: INFO
```

**Estimated Time:** 15 minutes

---

### 4. Run Dependency Vulnerability Scan ⚠️

**Issue:** Need to verify no known vulnerabilities in dependencies

**Impact:** High (security compliance)

**Implementation:**

```bash
# Add to pom.xml (in root)
<build>
  <plugins>
    <plugin>
      <groupId>org.owasp</groupId>
      <artifactId>dependency-check-maven</artifactId>
      <version>10.0.4</version>
      <configuration>
        <failBuildOnCVSS>7</failBuildOnCVSS>
        <suppressionFiles>
          <suppressionFile>dependency-check-suppressions.xml</suppressionFile>
        </suppressionFiles>
      </configuration>
      <executions>
        <execution>
          <goals>
            <goal>check</goal>
          </goals>
        </execution>
      </executions>
    </plugin>
  </plugins>
</build>
```

```bash
# Run scan
mvnw dependency-check:check

# View report
start target/dependency-check-report.html
```

**Estimated Time:** 30 minutes (first run takes longer)

---

## 🟡 Medium Priority (Next Sprint)

### 5. Extract Validation Logic into Separate Classes 💡

**Issue:** Validation logic mixed with business logic

**Impact:** Medium (maintainability, reusability, testability)

**Implementation:**

**Create New File:** `kafka-to-pv-dynamo-service/src/main/java/com/julian/razif/figaro/bigdata/consumer/validation/MessageValidator.java`

```java
package com.julian.razif.figaro.bigdata.consumer.validation;

import com.google.gson.JsonObject;
import org.springframework.stereotype.Component;

/**
 * Validator for Kafka message validation.
 */
@Component
public class MessageValidator {
  
  private static final int MAX_JSON_SIZE = 1_048_576;
  private static final int MAX_JSON_DEPTH = 10;
  private static final int MAX_USERNAME_LENGTH = 255;
  
  /**
   * Validates message structure and content.
   * 
   * @param message raw message string
   * @return validation result
   */
  public ValidationResult validate(String message) {
    // Size validation
    if (!isValidSize(message)) {
      return ValidationResult.invalid(
        ValidationError.OVERSIZED_MESSAGE,
        "Message exceeds size limit: " + message.length()
      );
    }
    
    // Structure validation
    JsonObject parsed = parseJson(message);
    if (parsed == null) {
      return ValidationResult.invalid(
        ValidationError.INVALID_JSON,
        "Failed to parse JSON"
      );
    }
    
    // Depth validation
    if (getDepth(parsed) > MAX_JSON_DEPTH) {
      return ValidationResult.invalid(
        ValidationError.EXCESSIVE_DEPTH,
        "JSON depth exceeds limit"
      );
    }
    
    // Field validation
    if (!hasRequiredFields(parsed)) {
      return ValidationResult.invalid(
        ValidationError.MISSING_REQUIRED_FIELD,
        "Missing required fields"
      );
    }
    
    return ValidationResult.valid(parsed);
  }
  
  private boolean isValidSize(String message) {
    return message != null && 
           !message.isEmpty() && 
           message.length() <= MAX_JSON_SIZE;
  }
  
  // ... other validation methods
}
```

**Create New File:** `kafka-to-pv-dynamo-service/src/main/java/com/julian/razif/figaro/bigdata/consumer/validation/ValidationResult.java`

```java
package com.julian.razif.figaro.bigdata.consumer.validation;

import com.google.gson.JsonObject;

/**
 * Sealed interface for validation results.
 */
public sealed interface ValidationResult 
  permits ValidationResult.Valid, ValidationResult.Invalid {
  
  /**
   * Valid validation result containing parsed data.
   */
  record Valid(JsonObject data) implements ValidationResult {
    public boolean isValid() { return true; }
  }
  
  /**
   * Invalid validation result containing error information.
   */
  record Invalid(ValidationError errorType, String message) 
    implements ValidationResult {
    public boolean isValid() { return false; }
  }
  
  /**
   * Creates a valid result.
   */
  static Valid valid(JsonObject data) {
    return new Valid(data);
  }
  
  /**
   * Creates an invalid result.
   */
  static Invalid invalid(ValidationError errorType, String message) {
    return new Invalid(errorType, message);
  }
}
```

**Create New File:** `kafka-to-pv-dynamo-service/src/main/java/com/julian/razif/figaro/bigdata/consumer/validation/ValidationError.java`

```java
package com.julian.razif.figaro.bigdata.consumer.validation;

/**
 * Validation error types.
 */
public enum ValidationError {
  OVERSIZED_MESSAGE("Message exceeds size limit"),
  INVALID_JSON("Invalid JSON syntax"),
  EXCESSIVE_DEPTH("JSON depth exceeds limit"),
  MISSING_REQUIRED_FIELD("Required field is missing"),
  INVALID_FIELD_FORMAT("Field format is invalid"),
  INVALID_ACTION_TYPE("Action type is invalid");
  
  private final String description;
  
  ValidationError(String description) {
    this.description = description;
  }
  
  public String getDescription() {
    return description;
  }
}
```

**Usage in Consumer:**

```java
@Component
public class KafkaToPvDynamoConsumer implements KafkaConsumer<String> {
  
  private final MessageValidator validator;
  
  public KafkaToPvDynamoConsumer(
    DynamoDBBatchService dynamoDBBatchService,
    @Qualifier("virtualThreadScheduler") Scheduler virtualThreadScheduler,
    MeterRegistry meterRegistry,
    MessageValidator validator) {  // Inject validator
    
    this.validator = validator;
    // ... other initialization
  }
  
  public CompletableFuture<JsonObject> filter(String message) {
    return CompletableFuture.supplyAsync(() -> {
      ValidationResult result = validator.validate(message);
      
      if (result instanceof ValidationResult.Invalid invalid) {
        logger.debug("Validation failed: {} - {}", 
          invalid.errorType(), invalid.message());
        return new JsonObject();
      }
      
      ValidationResult.Valid valid = (ValidationResult.Valid) result;
      return enrichWithTimestamp(valid.data());
    });
  }
}
```

**Benefits:**
1. ✅ Reusable validation logic
2. ✅ Easier to test validation separately
3. ✅ Clear error types with sealed interfaces
4. ✅ Single responsibility principle
5. ✅ Better maintainability

**Estimated Time:** 3-4 hours

---

### 6. Add Custom Exception Types 💡

**Issue:** Generic exceptions make error handling less precise

**Impact:** Low-Medium (error handling, debugging)

**Implementation:**

**Create New File:** `kafka-to-pv-dynamo-service/src/main/java/com/julian/razif/figaro/bigdata/consumer/exception/MessageProcessingException.java`

```java
package com.julian.razif.figaro.bigdata.consumer.exception;

/**
 * Base exception for message processing errors.
 */
public class MessageProcessingException extends RuntimeException {
  
  private final String messageId;
  private final ErrorType errorType;
  
  public MessageProcessingException(String message, String messageId, ErrorType errorType) {
    super(message);
    this.messageId = messageId;
    this.errorType = errorType;
  }
  
  public MessageProcessingException(String message, Throwable cause, 
                                    String messageId, ErrorType errorType) {
    super(message, cause);
    this.messageId = messageId;
    this.errorType = errorType;
  }
  
  public String getMessageId() {
    return messageId;
  }
  
  public ErrorType getErrorType() {
    return errorType;
  }
  
  public enum ErrorType {
    VALIDATION_FAILED,
    PARSING_FAILED,
    PERSISTENCE_FAILED,
    TIMEOUT
  }
}
```

**Create New File:** `kafka-to-pv-dynamo-service/src/main/java/com/julian/razif/figaro/bigdata/consumer/exception/MessageValidationException.java`

```java
package com.julian.razif.figaro.bigdata.consumer.exception;

import com.julian.razif.figaro.bigdata.consumer.validation.ValidationError;

/**
 * Exception for message validation failures.
 */
public class MessageValidationException extends MessageProcessingException {
  
  private final ValidationError validationError;
  
  public MessageValidationException(String message, String messageId, 
                                    ValidationError validationError) {
    super(message, messageId, ErrorType.VALIDATION_FAILED);
    this.validationError = validationError;
  }
  
  public ValidationError getValidationError() {
    return validationError;
  }
}
```

**Usage:**

```java
private JsonObject parseAndValidate(String message) {
  try {
    JsonObject parsed = gson.fromJson(message, JsonElement.class).getAsJsonObject();
    return parsed;
  } catch (JsonSyntaxException ex) {
    throw new MessageValidationException(
      "Invalid JSON syntax",
      extractMessageId(message),
      ValidationError.INVALID_JSON
    );
  }
}

// In error handler
.doOnError(error -> {
  if (error instanceof MessageValidationException validationEx) {
    logger.warn("Validation failed for message {}: {}", 
      validationEx.getMessageId(), 
      validationEx.getValidationError());
    validationErrorCounter.increment();
  } else {
    logger.error("Unexpected error processing message", error);
    messagesErrorCounter.increment();
  }
})
```

**Estimated Time:** 2 hours

---

### 7. Add Integration Tests 💡

**Issue:** Only unit tests present, no end-to-end testing

**Impact:** Medium (test coverage, confidence in deployments)

**Implementation:**

**Add Dependencies to `kafka-to-pv-dynamo-service/pom.xml`:**

```xml
<dependencies>
  <!-- Embedded Kafka for testing -->
  <dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka-test</artifactId>
    <scope>test</scope>
  </dependency>
  
  <!-- Test containers for DynamoDB Local -->
  <dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers</artifactId>
    <version>1.20.1</version>
    <scope>test</scope>
  </dependency>
  
  <dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>localstack</artifactId>
    <version>1.20.1</version>
    <scope>test</scope>
  </dependency>
</dependencies>
```

**Create New File:** `kafka-to-pv-dynamo-service/src/test/java/com/julian/razif/figaro/bigdata/consumer/KafkaToDynamoIntegrationTest.java`

```java
package com.julian.razif.figaro.bigdata.consumer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Kafka to DynamoDB end-to-end flow.
 */
@SpringBootTest
@EmbeddedKafka(
  partitions = 1,
  topics = {"test-topic"}
)
@Testcontainers
class KafkaToDynamoIntegrationTest {
  
  @Container
  static LocalStackContainer localstack = new LocalStackContainer(
    DockerImageName.parse("localstack/localstack:latest"))
    .withServices(LocalStackContainer.Service.DYNAMODB);
  
  @DynamicPropertySource
  static void overrideProperties(DynamicPropertyRegistry registry) {
    registry.add("dynamo-config-data.dynamodb-endpoint", 
      () -> localstack.getEndpointOverride(LocalStackContainer.Service.DYNAMODB).toString());
    registry.add("dynamo-config-data.aws-region", 
      () -> localstack.getRegion());
  }
  
  @Autowired
  private KafkaTemplate<String, String> kafkaTemplate;
  
  @Test
  void shouldProcessMessageEndToEnd() {
    // Given
    String validMessage = """
      {
        "act": "bigDataSesUsPvMember",
        "data": {
          "id": "12345",
          "user_id": "67890",
          "member_username": "testuser"
        }
      }
      """;
    
    // When
    kafkaTemplate.send("test-topic", validMessage);
    
    // Then
    await()
      .atMost(Duration.ofSeconds(10))
      .untilAsserted(() -> {
        // Verify message was persisted to DynamoDB
        // Add verification logic here
        assertTrue(true); // Placeholder
      });
  }
}
```

**Estimated Time:** 4-6 hours

---

## 🟢 Low Priority / Enhancements

### 8. Implement Rate Limiting 💡

**Issue:** No protection against message flooding

**Impact:** Low (resource protection)

**Implementation:**

**Add Dependency:**
```xml
<dependency>
  <groupId>com.google.guava</groupId>
  <artifactId>guava</artifactId>
  <version>33.3.1-jre</version>
</dependency>
```

**Create Configuration:**
```java
@Configuration
public class RateLimitingConfig {
  
  @Bean
  public RateLimiter messagingRateLimiter(
    @Value("${kafka-consumer-config.rate-limit:10000}") double permitsPerSecond) {
    return RateLimiter.create(permitsPerSecond);
  }
}
```

**Usage in Consumer:**
```java
private final RateLimiter rateLimiter;

@KafkaListener(...)
@Override
public void receive(@Payload List<String> messages) {
  // Check rate limit
  if (!rateLimiter.tryAcquire(messages.size())) {
    logger.warn("Rate limit exceeded, dropping {} messages", messages.size());
    rateLimitExceededCounter.increment(messages.size());
    return;
  }
  
  // Process messages
  messagesReceivedCounter.increment(messages.size());
  // ... rest of processing
}
```

**Estimated Time:** 2 hours

---

### 9. Add Comprehensive Audit Logging 💡

**Issue:** Basic logging, no structured audit trail

**Impact:** Low (compliance, debugging)

**Implementation:**

```java
@Aspect
@Component
public class AuditAspect {
  
  private static final Logger auditLogger = 
    LoggerFactory.getLogger("AUDIT");
  
  @Around("@annotation(Auditable)")
  public Object auditMethod(ProceedingJoinPoint joinPoint) throws Throwable {
    String methodName = joinPoint.getSignature().getName();
    Object[] args = joinPoint.getArgs();
    long startTime = System.currentTimeMillis();
    
    auditLogger.info("AUDIT_START: method={}, args={}", 
      methodName, sanitizeArgs(args));
    
    try {
      Object result = joinPoint.proceed();
      long duration = System.currentTimeMillis() - startTime;
      
      auditLogger.info("AUDIT_SUCCESS: method={}, duration={}ms", 
        methodName, duration);
      
      return result;
    } catch (Exception e) {
      auditLogger.error("AUDIT_FAILURE: method={}, error={}", 
        methodName, e.getMessage());
      throw e;
    }
  }
  
  private Object[] sanitizeArgs(Object[] args) {
    // Mask sensitive data
    return args;
  }
}
```

**Estimated Time:** 3 hours

---

## Summary

### Time Estimates

| Priority | Tasks | Total Time |
|----------|-------|------------|
| 🔴 High | 4 tasks | ~3 hours |
| 🟡 Medium | 3 tasks | ~9-12 hours |
| 🟢 Low | 2 tasks | ~5 hours |
| **Total** | **9 tasks** | **17-20 hours** |

### Immediate Actions (This Week)
1. Fix `DynamonDBConfigData` typo → `DynamoDBConfigData` (15 min)
2. Add production logging configuration (15 min)
3. Run OWASP dependency check (30 min)

### Sprint Planning (Next 2 Weeks)
1. Refactor `filter()` method (2 hours)
2. Extract validation logic (3-4 hours)
3. Add custom exceptions (2 hours)
4. Start integration tests (4-6 hours)

### Long-term Improvements (Next Month)
1. Implement rate limiting
2. Add comprehensive audit logging
3. Performance testing framework

---

**Document Created:** January 9, 2026  
**Status:** Ready for Implementation  
**Next Review:** After high-priority items completed
