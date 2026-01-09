# AWS SDK V2 DynamoDB Setup Guide

## Overview

This project has been successfully configured to use AWS SDK for Java V2 (version 2.41.0) with DynamoDB support. The setup includes:

- **Synchronous DynamoDB Client** - Traditional blocking operations
- **Asynchronous DynamoDB Client** - Non-blocking, high-throughput operations
- **Enhanced DynamoDB Client** - Object mapping with annotations

## Project Structure

```
bigdata/
├── app-config-data/         # Configuration properties
├── dynamo-config/           # DynamoDB client configuration beans
└── kafka-to-pv-dynamo-service/  # Main service application
```

## Dependencies Added

### Root POM (`pom.xml`)

The AWS SDK BOM (Bill of Materials) is configured in dependency management:

```xml
<properties>
    <awsjavasdk.version>2.41.0</awsjavasdk.version>
</properties>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>software.amazon.awssdk</groupId>
            <artifactId>bom</artifactId>
            <version>${awsjavasdk.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

### Dynamo Config Module (`dynamo-config/pom.xml`)

```xml
<dependencies>
    <!-- AWS SDK V2 DynamoDB -->
    <dependency>
        <groupId>software.amazon.awssdk</groupId>
        <artifactId>dynamodb</artifactId>
    </dependency>
    
    <!-- DynamoDB Enhanced Client for object mapping -->
    <dependency>
        <groupId>software.amazon.awssdk</groupId>
        <artifactId>dynamodb-enhanced</artifactId>
    </dependency>
    
    <!-- HTTP clients for connection pooling -->
    <dependency>
        <groupId>software.amazon.awssdk</groupId>
        <artifactId>apache-client</artifactId>
    </dependency>
    
    <dependency>
        <groupId>software.amazon.awssdk</groupId>
        <artifactId>netty-nio-client</artifactId>
    </dependency>
</dependencies>
```

## Configuration

### Application Configuration (`application-dev.yaml`)

```yaml
dynamo-config-data:
  dynamodb-endpoint: dynamodb.ap-southeast-1.amazonaws.com
  aws-region: ap-southeast-1
  aws-accesskey: ${DYNAMO-CONFIG-DATA_AWS-ACCESSKEY}  # From environment
  aws-secretkey: ${DYNAMO-CONFIG-DATA_AWS-SECRETKEY}  # From environment
  
  # Table names
  table-name-session-provider-member: BigDataSession_ProviderMember
  table-name-session-user: BigDataSession_User
  table-name-persistence-provider-member: BigDataPersistence_ProviderMember
  table-name-persistence-user: BigDataPersistence_User
  
  # Connection settings
  connection-ttl: 20000
  connection-timeout: 4000
  client-execution-timeout: 10000
  request-timeout: 2000
  socket-timeout: 1800
  max-retry: 100
  max-connections: 2000
  tcp-keep-alive: false
```

## AWS Credentials Configuration

### Option 1: Environment Variables (Recommended for Production)

Set these environment variables:

**Windows:**
```powershell
$env:DYNAMO-CONFIG-DATA_AWS-ACCESSKEY="your-access-key"
$env:DYNAMO-CONFIG-DATA_AWS-SECRETKEY="your-secret-key"
```

**Linux/Mac:**
```bash
export DYNAMO-CONFIG-DATA_AWS-ACCESSKEY="your-access-key"
export DYNAMO-CONFIG-DATA_AWS-SECRETKEY="your-secret-key"
```

### Option 2: AWS Credentials File

Create `~/.aws/credentials`:

```ini
[default]
aws_access_key_id = your-access-key
aws_secret_access_key = your-secret-key
```

### Option 3: IAM Role (Best for AWS Environments)

When running on EC2, ECS, or Lambda, the SDK automatically uses IAM roles. No credentials needed!

## Available DynamoDB Clients

### 1. Synchronous Client (`DynamoDbClient`)

Use for:
- Traditional blocking operations
- Simple CRUD operations
- Low-concurrency scenarios

**Example:**
```java
@Service
public class UserService {
    private final DynamoDbClient dynamoDbClient;
    
    public void saveUser(User user) {
        PutItemRequest request = PutItemRequest.builder()
            .tableName("Users")
            .item(toAttributeMap(user))
            .build();
        
        dynamoDbClient.putItem(request);
    }
}
```

### 2. Asynchronous Client (`DynamoDbAsyncClient`)

Use for:
- High-throughput scenarios (>10K req/sec)
- Kafka consumer message processing
- Reactive Spring WebFlux applications
- Non-blocking I/O operations

**Example:**
```java
@Service
public class OrderService {
    private final DynamoDbAsyncClient dynamoDbAsyncClient;
    
    public CompletableFuture<Void> saveOrderAsync(Order order) {
        PutItemRequest request = PutItemRequest.builder()
            .tableName("Orders")
            .item(toAttributeMap(order))
            .build();
        
        return dynamoDbAsyncClient.putItem(request)
            .thenAccept(response -> 
                log.info("Order saved: {}", order.getId())
            );
    }
}
```

### 3. Enhanced Client (`DynamoDbEnhancedClient`)

Use for:
- Object-relational mapping (ORM-like)
- Type-safe operations with POJOs
- Automatic serialization/deserialization

**Example:**
```java
@DynamoDbBean
public class Product {
    private String id;
    private String name;
    private BigDecimal price;
    
    @DynamoDbPartitionKey
    public String getId() { return id; }
    
    // Getters and setters...
}

@Service
public class ProductService {
    private final DynamoDbEnhancedClient enhancedClient;
    private final DynamoDbTable<Product> productTable;
    
    public ProductService(DynamoDbEnhancedClient enhancedClient) {
        this.enhancedClient = enhancedClient;
        this.productTable = enhancedClient.table("Products", 
            TableSchema.fromBean(Product.class));
    }
    
    public void saveProduct(Product product) {
        productTable.putItem(product);
    }
    
    public Product getProduct(String id) {
        return productTable.getItem(Key.builder().partitionValue(id).build());
    }
}
```

## Sample Service

A complete sample service is available at:
`kafka-to-pv-dynamo-service/src/main/java/com/julian/razif/figaro/bigdata/consumer/service/DynamoDBService.java`

This service demonstrates:
- ✅ Synchronous read/write operations
- ✅ Asynchronous read/write operations
- ✅ Batch processing with CompletableFuture
- ✅ Error handling and logging
- ✅ Performance optimization patterns

## Performance Optimization

### Connection Pooling

The configuration includes optimized connection pooling:

**Apache HTTP Client (Sync):**
- Max connections: 2000
- Connection TTL: 20 seconds
- Connection timeout: 4 seconds
- Socket timeout: 1.8 seconds

**Netty NIO Client (Async):**
- Max concurrency: 2000
- Non-blocking I/O for high throughput
- Event loop-based architecture

### Retry Strategy

The configuration includes a sophisticated retry strategy:

**RetryStrategy Configuration:**
- **Strategy Type**: `StandardRetryStrategy` (modern API)
- **Max Attempts**: Configurable via `max-retry` property (default: 100)
- **Backoff Strategy**: Exponential backoff with jitter
  - Base delay: 100ms
  - Max backoff: 20 seconds
  - Prevents thundering herd with randomized delays
- **Throttling Handling**: Separate backoff strategy for throttling errors
- **Error Types**: Automatically retries transient errors, network failures, timeouts, and 5xx responses

**Benefits:**
- ✅ Modern and Non-deprecated AWS SDK V2 APIs (`StandardRetryStrategy` instead of legacy `RetryPolicy`)
- ✅ Intelligent exponential backoff prevents overwhelming the service
- ✅ Jitter randomization distributes retry attempts across time
- ✅ Separate throttling backoff for DynamoDB provisioned throughput limits
- ✅ Configurable max attempts based on your application needs

**Configuration:**
```yaml
dynamo-config-data:
  max-retry: 100  # Maximum retry attempts
  client-execution-timeout: 10000  # Total timeout including all retries
  request-timeout: 2000  # Timeout per individual attempt
```

### Best Practices

1. **Use Async Client for Kafka Consumers:**
   ```java
   CompletableFuture<?>[] futures = messages.stream()
       .map(msg -> dynamoDbService.saveSessionAsync(msg.getKey(), msg.getValue()))
       .toArray(CompletableFuture[]::new);
   
   CompletableFuture.allOf(futures).join();
   ```

2. **Batch Operations:**
   Use `BatchWriteItem` for multiple items instead of individual `PutItem` calls.

3. **Consistent Reads:**
   Only use `consistentRead(true)` when necessary - it costs 2x read capacity.

4. **Projection Expressions:**
   Fetch only needed attributes to reduce network overhead:
   ```java
   GetItemRequest.builder()
       .projectionExpression("id, name, email")
       .build();
   ```

## Build and Run

### Build the Project

```bash
# Windows
.\mvnw.cmd clean package

# Linux/Mac
./mvnw clean package
```

### Run the Application

```bash
# Windows
.\mvnw.cmd spring-boot:run -pl kafka-to-pv-dynamo-service

# Linux/Mac
./mvnw spring-boot:run -pl kafka-to-pv-dynamo-service
```

### Run with Specific Profile

```bash
# Windows
.\mvnw.cmd spring-boot:run -pl kafka-to-pv-dynamo-service -Dspring-boot.run.profiles=dev

# Linux/Mac
./mvnw spring-boot:run -pl kafka-to-pv-dynamo-service -Dspring-boot.run.profiles=dev
```

## Testing DynamoDB Connection

### Using AWS CLI

```bash
# List tables
aws dynamodb list-tables --region ap-southeast-1

# Describe table
aws dynamodb describe-table --table-name BigDataSession_User --region ap-southeast-1

# Scan table (limited items)
aws dynamodb scan --table-name BigDataSession_User --limit 10 --region ap-southeast-1
```

### Using DynamoDB Local (for Development)

```bash
# Download and run DynamoDB Local
docker run -p 8000:8000 amazon/dynamodb-local

# Update application-dev.yaml
dynamo-config-data:
  dynamodb-endpoint: localhost:8000
```

## Monitoring and Logging

The configuration includes:
- SLF4J logging for all DynamoDB operations
- Success/failure logging with request details
- Exception handling with contextual information

**Log examples:**
```
INFO  - Session saved successfully: sessionId=abc123, userId=user456, httpStatus=200
ERROR - Failed to save session: sessionId=abc123, userId=user456
```

## Security Best Practices

1. **Never commit credentials to Git**
   - Use environment variables
   - Use AWS Secrets Manager
   - Use IAM roles when possible

2. **Rotate credentials regularly**
   - Set up AWS IAM credential rotation
   - Use temporary credentials (STS)

3. **Apply least privilege principle**
   - Grant only required DynamoDB permissions
   - Use table-level resource policies

4. **Enable encryption**
   - DynamoDB encryption at rest (enabled by default)
   - TLS for data in transit (configured in clients)

## Troubleshooting

### Issue: `ProvisionedThroughputExceededException`

**Solution:** Increase table capacity or enable auto-scaling

```bash
aws dynamodb update-table \
  --table-name BigDataSession_User \
  --provisioned-throughput ReadCapacityUnits=100,WriteCapacityUnits=100
```

### Issue: Connection timeout

**Solution:** Adjust timeout settings in `application.yaml`:

```yaml
dynamo-config-data:
  connection-timeout: 8000  # Increase from 4000
  socket-timeout: 3600      # Increase from 1800
```

### Issue: Too many connections

**Solution:** Reduce max connections or enable connection pooling metrics:

```yaml
dynamo-config-data:
  max-connections: 500  # Reduce from 2000
```

## References

- [AWS SDK for Java V2 Developer Guide](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/home.html)
- [DynamoDB Developer Guide](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/)
- [AWS SDK Java V2 API Reference](https://sdk.amazonaws.com/java/api/latest/)
- [DynamoDB Best Practices](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/best-practices.html)
- [Spring Boot with AWS](https://spring.io/projects/spring-cloud-aws)

## Support

For issues or questions:
1. Check AWS SDK Java GitHub: https://github.com/aws/aws-sdk-java-v2
2. AWS Developer Forums: https://forums.aws.amazon.com/
3. Stack Overflow tag: `aws-sdk-java-2`

---

**Author:** Julian Razif Figaro  
**Version:** 1.0  
**Last Updated:** January 8, 2026
