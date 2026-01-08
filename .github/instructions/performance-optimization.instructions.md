---
description: 'Comprehensive performance optimization guidelines for Java JDK 25, Spring Boot 4, Kafka, Redis, and PostgreSQL backend systems'
applyTo: '**/*.java, **/*.yaml, **/*.yml, **/*.properties'
---

# Performance Optimization Instructions

Engineer-authored, production-tested performance optimization guidelines for building high-performance Java backend systems using JDK 25, Spring Boot 4, Apache Kafka, Redis, and PostgreSQL.

## Project Context

- **Language**: Java 21+ (targeting JDK 25)
- **Framework**: Spring Boot 4.x
- **Message Broker**: Apache Kafka 3.x
- **Cache**: Redis 7.x
- **Database**: PostgreSQL 16+
- **Target**: High-throughput, low-latency backend services
- **Focus**: Production-grade performance with real-world optimization patterns

---

## General Performance Principles

- Measure first, optimize second - always profile before optimizing
- Focus on algorithmic efficiency before micro-optimizations
- Optimize for the common case, not edge cases
- Balance performance with code maintainability and readability
- Consider total cost: CPU, memory, network, and developer time
- Use appropriate data structures for access patterns
- Minimize object allocation in hot paths
- Leverage JDK 25 features: Virtual Threads, Structured Concurrency, Record Patterns
- Monitor and measure in production: latency percentiles (p50, p95, p99), throughput, resource utilization

---

## Java JDK 25 Performance Best Practices

### Virtual Threads (Project Loom)

Use Virtual Threads for I/O-bound operations to maximize throughput:

```java
// Good: Virtual Thread Executor for I/O operations
@Configuration
public class AsyncConfig {
    @Bean(name = "virtualThreadExecutor")
    public ExecutorService virtualThreadExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}

@Service
public class UserService {
    @Async("virtualThreadExecutor")
    public CompletableFuture<User> fetchUserAsync(String userId) {
        // I/O-bound operation - perfect for virtual threads
        return CompletableFuture.completedFuture(userRepository.findById(userId));
    }
}
```

```java
// Bad: Platform threads for high-concurrency I/O
@Bean
public ExecutorService executor() {
    return Executors.newFixedThreadPool(200); // Wastes memory, limited scalability
}
```

**When to use Virtual Threads**:
- REST API calls to external services
- Database queries (with proper connection pooling)
- File I/O operations
- Message broker operations

**When NOT to use Virtual Threads**:
- CPU-intensive computations (use ForkJoinPool)
- Pinning scenarios: synchronized blocks, native calls
- When you need precise thread control

### Structured Concurrency

Manage concurrent operations with automatic cleanup and error propagation:

```java
// Good: Structured Concurrency with StructuredTaskScope
public UserProfile getUserProfile(String userId) throws Exception {
    try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
        Subtask<User> userTask = scope.fork(() -> userService.getUser(userId));
        Subtask<List<Order>> ordersTask = scope.fork(() -> orderService.getOrders(userId));
        Subtask<AccountSettings> settingsTask = scope.fork(() -> settingsService.getSettings(userId));
        
        scope.join()           // Wait for all tasks
             .throwIfFailed(); // Propagate exceptions
        
        return new UserProfile(userTask.get(), ordersTask.get(), settingsTask.get());
    }
}
```

### Record Patterns and Pattern Matching

Reduce object overhead and improve readability:

```java
// Good: Record for immutable DTOs (zero-overhead abstraction)
public record UserDto(String id, String name, String email, Instant createdAt) {}

// Pattern matching for instanceof (JDK 25)
public double calculateDiscount(Order order) {
    return switch (order) {
        case PremiumOrder po -> po.amount() * 0.20;
        case StandardOrder so -> so.amount() * 0.10;
        case BulkOrder bo when bo.quantity() > 100 -> bo.amount() * 0.15;
        default -> 0.0;
    };
}
```

### String Templates (JDK 25 Preview)

```java
// Good: String Templates for efficient string formatting
String query = STR."SELECT * FROM users WHERE id = \{userId} AND status = \{status}";

// Bad: String concatenation in loops or repeated operations
String query = "SELECT * FROM users WHERE id = " + userId + " AND status = " + status;
```

### Garbage Collection Tuning

**For low-latency applications (< 100ms p99)**:
```bash
# ZGC - Sub-millisecond pause times
-XX:+UseZGC 
-XX:ZCollectionInterval=60 
-Xmx16g 
-Xms16g
```

**For high-throughput applications**:
```bash
# G1GC - Balanced performance
-XX:+UseG1GC 
-XX:MaxGCPauseMillis=200 
-XX:G1HeapRegionSize=16m
-Xmx16g 
-Xms16g
```

**For extremely memory-constrained environments**:
```bash
# Generational ZGC (JDK 25)
-XX:+UseZGC 
-XX:+ZGenerational
-Xmx4g 
-Xms4g
```

### JVM Performance Flags (JDK 25)

```properties
# Essential production flags
-XX:+UseStringDeduplication           # Reduce memory for duplicate strings
-XX:+AlwaysPreTouch                   # Touch memory at startup (reduces runtime allocation overhead)
-XX:+DisableExplicitGC                # Prevent System.gc() calls
-XX:+UseCompressedOops                # Compress object pointers (< 32GB heap)
-XX:+UseCompressedClassPointers       # Compress class metadata
-XX:+OptimizeStringConcat             # Optimize string concatenation
-XX:+UseNUMA                          # NUMA-aware allocation (multi-socket servers)

# Monitoring and diagnostics
-XX:+UnlockDiagnosticVMOptions
-XX:+DebugNonSafepoints
-XX:+PrintGCDetails
-XX:+PrintGCDateStamps
-Xlog:gc*:file=gc.log:time,uptime,level,tags
```

---

## Spring Boot 4 Performance Optimization

### Application Startup Optimization

```yaml
# application.yaml - Production configuration
spring:
  main:
    lazy-initialization: false  # Set to true only if startup time is critical
    cloud-platform: kubernetes   # Enable platform-specific optimizations
  
  jmx:
    enabled: false  # Disable JMX if not needed
  
  devtools:
    restart:
      enabled: false  # Never enable in production
  
  threads:
    virtual:
      enabled: true  # Enable virtual threads globally (Spring Boot 4+)
  
  aot:
    enabled: true  # Enable AOT compilation for faster startup
```

### Connection Pool Optimization (HikariCP)

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20  # Formula: ((core_count * 2) + effective_spindle_count)
      minimum-idle: 10
      connection-timeout: 30000  # 30 seconds
      idle-timeout: 600000       # 10 minutes
      max-lifetime: 1800000      # 30 minutes
      leak-detection-threshold: 60000  # 60 seconds
      pool-name: HikariPool-Primary
      
      # Performance tuning
      auto-commit: false  # Manual transaction management is faster
      connection-test-query: SELECT 1  # Keep-alive query
      
      # PostgreSQL-specific optimizations
      data-source-properties:
        cachePrepStmts: true
        prepStmtCacheSize: 250
        prepStmtCacheSqlLimit: 2048
        useServerPrepStmts: true
        reWriteBatchedInserts: true  # Critical for batch performance
        defaultRowFetchSize: 100
```

**Connection Pool Sizing Guidelines**:
- **OLTP workloads**: `connections = (core_count * 2) + effective_spindle_count`
- **High concurrency**: Start with 20-50 connections, measure, adjust
- **Virtual Threads**: Can use smaller pool sizes due to efficient blocking
- **Monitor**: Connection wait time, active connections, idle connections

### REST Controller Optimization

```java
// Good: Efficient REST controller with caching and compression
@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    
    @GetMapping("/{id}")
    @Cacheable(value = "users", key = "#id", unless = "#result == null")
    public ResponseEntity<UserDto> getUser(@PathVariable String id) {
        return userService.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
    
    @GetMapping
    public ResponseEntity<List<UserDto>> getUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        
        // Validate pagination limits
        size = Math.min(size, 100); // Prevent excessive data transfer
        
        Page<UserDto> users = userService.findAll(PageRequest.of(page, size));
        return ResponseEntity.ok()
            .header("X-Total-Count", String.valueOf(users.getTotalElements()))
            .body(users.getContent());
    }
    
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public UserDto createUser(@Valid @RequestBody CreateUserRequest request) {
        return userService.create(request);
    }
}

// Enable compression for responses
@Configuration
public class WebConfig {
    @Bean
    public TomcatServletWebServerFactory tomcatFactory() {
        TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory();
        factory.addConnectorCustomizers(connector -> {
            connector.setProperty("compression", "on");
            connector.setProperty("compressionMinSize", "1024");
            connector.setProperty("compressibleMimeType", 
                "application/json,application/xml,text/html,text/xml,text/plain");
        });
        return factory;
    }
}
```

### Async Processing and CompletableFuture

```java
// Good: Non-blocking async processing
@Service
public class OrderService {
    
    @Async("virtualThreadExecutor")
    public CompletableFuture<OrderDto> processOrder(CreateOrderRequest request) {
        // Parallel execution of independent operations
        CompletableFuture<Inventory> inventoryFuture = 
            CompletableFuture.supplyAsync(() -> inventoryService.checkAvailability(request.items()));
        
        CompletableFuture<PaymentResult> paymentFuture = 
            CompletableFuture.supplyAsync(() -> paymentService.authorize(request.payment()));
        
        return inventoryFuture.thenCombine(paymentFuture, (inventory, payment) -> {
            if (inventory.isAvailable() && payment.isAuthorized()) {
                return orderRepository.save(new Order(request, inventory, payment));
            }
            throw new OrderProcessingException("Order validation failed");
        }).thenApply(orderMapper::toDto);
    }
}
```

### Database Query Optimization (JPA/Hibernate)

```java
// Good: Optimized JPA queries with fetch joins and DTOs
@Repository
public interface UserRepository extends JpaRepository<User, String> {
    
    // Use DTO projections to fetch only needed fields
    @Query("SELECT new com.example.dto.UserSummaryDto(u.id, u.name, u.email) " +
           "FROM User u WHERE u.status = :status")
    List<UserSummaryDto> findActiveUsers(@Param("status") UserStatus status);
    
    // Fetch join to avoid N+1 queries
    @Query("SELECT DISTINCT u FROM User u " +
           "LEFT JOIN FETCH u.roles " +
           "LEFT JOIN FETCH u.profile " +
           "WHERE u.id = :id")
    Optional<User> findByIdWithDetails(@Param("id") String id);
    
    // Batch fetching for collections
    @EntityGraph(attributePaths = {"orders", "orders.items"})
    List<User> findByStatusIn(List<UserStatus> statuses);
}

// Bad: N+1 query problem
@GetMapping("/users")
public List<UserDto> getUsers() {
    List<User> users = userRepository.findAll();
    return users.stream()
        .map(user -> {
            // This triggers separate query for each user!
            List<Order> orders = user.getOrders(); 
            return new UserDto(user, orders);
        })
        .toList();
}
```

**Hibernate Configuration for Performance**:

```yaml
spring:
  jpa:
    properties:
      hibernate:
        # Query optimization
        jdbc:
          batch_size: 50  # Batch INSERT/UPDATE statements
          fetch_size: 100  # Rows fetched per round trip
        
        # Second-level cache (use with Redis)
        cache:
          use_second_level_cache: true
          use_query_cache: true
          region:
            factory_class: org.hibernate.cache.jcache.JCacheRegionFactory
        
        # Performance settings
        order_inserts: true
        order_updates: true
        jdbc.batch_versioned_data: true
        
        # Query optimization
        query:
          in_clause_parameter_padding: true  # Optimize IN clause
          plan_cache_max_size: 2048
          fail_on_pagination_over_collection_fetch: true
        
        # Statistics for monitoring
        generate_statistics: true
        
    # Show SQL in development only
    show-sql: false
    open-in-view: false  # Disable OSIV anti-pattern
```

### Caching Strategy

```java
// Good: Multi-level caching with Redis
@Configuration
@EnableCaching
public class CacheConfig {
    
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(10))
            .serializeKeysWith(RedisSerializationContext.SerializationPair
                .fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(RedisSerializationContext.SerializationPair
                .fromSerializer(new GenericJackson2JsonRedisSerializer()))
            .disableCachingNullValues();
        
        // Per-cache configuration
        Map<String, RedisCacheConfiguration> cacheConfigs = Map.of(
            "users", defaultConfig.entryTtl(Duration.ofMinutes(30)),
            "products", defaultConfig.entryTtl(Duration.ofHours(1)),
            "sessions", defaultConfig.entryTtl(Duration.ofMinutes(15))
        );
        
        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(defaultConfig)
            .withInitialCacheConfigurations(cacheConfigs)
            .build();
    }
}

// Cache usage with proper eviction
@Service
public class ProductService {
    
    @Cacheable(value = "products", key = "#id")
    public ProductDto getProduct(String id) {
        return productRepository.findById(id)
            .map(productMapper::toDto)
            .orElseThrow(() -> new ProductNotFoundException(id));
    }
    
    @CachePut(value = "products", key = "#result.id")
    public ProductDto updateProduct(String id, UpdateProductRequest request) {
        Product product = productRepository.findById(id)
            .orElseThrow(() -> new ProductNotFoundException(id));
        productMapper.updateEntity(product, request);
        return productMapper.toDto(productRepository.save(product));
    }
    
    @CacheEvict(value = "products", key = "#id")
    public void deleteProduct(String id) {
        productRepository.deleteById(id);
    }
    
    // Bulk eviction
    @CacheEvict(value = "products", allEntries = true)
    public void refreshProductCache() {
        // Triggered by scheduled job or admin action
    }
}
```

---

## Kafka Performance Optimization

### Producer Configuration

```yaml
spring:
  kafka:
    bootstrap-servers: kafka-1:9092,kafka-2:9092,kafka-3:9092
    
    producer:
      # Serialization
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      
      # Batching for throughput (tune based on latency requirements)
      batch-size: 32768  # 32KB batches
      linger-ms: 10      # Wait 10ms to fill batch (higher = more throughput, higher latency)
      buffer-memory: 67108864  # 64MB buffer
      
      # Compression (improves throughput, adds CPU overhead)
      compression-type: lz4  # Options: none, gzip, snappy, lz4, zstd
      
      # Reliability vs Performance
      acks: 1  # 0=no ack, 1=leader ack, all=all replicas ack
      retries: 3
      
      # Idempotence for exactly-once semantics
      enable-idempotence: true
      max-in-flight-requests-per-connection: 5
      
      properties:
        # Connection pooling
        connections.max.idle.ms: 540000
        
        # Request optimization
        max.request.size: 1048576  # 1MB max message size
```

**Producer Optimization Patterns**:

```java
// Good: Async fire-and-forget for high throughput
@Service
public class EventPublisher {
    
    private final KafkaTemplate<String, EventDto> kafkaTemplate;
    
    public void publishEvent(String key, EventDto event) {
        kafkaTemplate.send("events-topic", key, event)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    // Log and monitor failures
                    log.error("Failed to send event: key={}, event={}", key, event, ex);
                    // Consider dead letter queue or retry logic
                } else {
                    log.debug("Event sent: partition={}, offset={}", 
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
                }
            });
    }
    
    // Batch publishing for better throughput
    public void publishBatch(List<EventDto> events) {
        events.forEach(event -> 
            kafkaTemplate.send("events-topic", event.getId(), event)
        );
        
        // Explicitly flush if needed (usually automatic)
        kafkaTemplate.flush();
    }
}
```

### Consumer Configuration

```yaml
spring:
  kafka:
    consumer:
      # Deserialization
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      
      # Consumer group
      group-id: order-processing-service
      
      # Offset management
      auto-offset-reset: earliest  # earliest | latest | none
      enable-auto-commit: false    # Manual commit for reliability
      
      # Fetch optimization
      fetch-min-size: 1024         # Min bytes to fetch (1KB)
      fetch-max-wait: 500          # Max wait time (500ms)
      max-poll-records: 500        # Records per poll (tune based on processing time)
      max-poll-interval-ms: 300000 # 5 minutes max processing time
      
      properties:
        # Session management
        session.timeout.ms: 30000
        heartbeat.interval.ms: 3000
        
        # Deserialization
        spring.json.trusted.packages: "com.julian.razif.figaro.bigdata.*"
        spring.json.type.mapping: "event:com.example.dto.EventDto"
```

**Consumer Optimization Patterns**:

```java
// Good: Concurrent consumers with manual commit
@Configuration
public class KafkaConsumerConfig {
    
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, EventDto> kafkaListenerContainerFactory(
            ConsumerFactory<String, EventDto> consumerFactory) {
        
        ConcurrentKafkaListenerContainerFactory<String, EventDto> factory = 
            new ConcurrentKafkaListenerContainerFactory<>();
        
        factory.setConsumerFactory(consumerFactory);
        
        // Concurrency = number of consumer threads (scale with partitions)
        factory.setConcurrency(3);  // Should match or be less than partition count
        
        // Batch listener for higher throughput
        factory.setBatchListener(true);
        
        // Manual commit for control
        factory.getContainerProperties().setAckMode(AckMode.MANUAL_IMMEDIATE);
        
        // Error handling
        factory.setCommonErrorHandler(new DefaultErrorHandler(
            new FixedBackOff(1000L, 3L)  // 1s backoff, 3 retries
        ));
        
        return factory;
    }
}

@Service
public class EventConsumer {
    
    // Batch processing for throughput
    @KafkaListener(topics = "events-topic", groupId = "event-processor")
    public void consumeBatch(List<EventDto> events, Acknowledgment ack) {
        try {
            // Process in batch for better database performance
            List<ProcessedEvent> processed = events.stream()
                .map(this::processEvent)
                .toList();
            
            // Batch insert
            eventRepository.saveAll(processed);
            
            // Commit offset after successful processing
            ack.acknowledge();
            
        } catch (Exception e) {
            log.error("Batch processing failed", e);
            // Don't ack - message will be redelivered
            throw e;
        }
    }
    
    private ProcessedEvent processEvent(EventDto event) {
        // Business logic
        return new ProcessedEvent(event);
    }
}
```

### Kafka Partitioning Strategy

```java
// Good: Consistent key-based partitioning
@Service
public class OrderEventPublisher {
    
    public void publishOrderEvent(Order order) {
        // Use customer ID as key for ordering guarantees
        String partitionKey = order.getCustomerId();
        
        OrderEvent event = new OrderEvent(order);
        
        kafkaTemplate.send("orders-topic", partitionKey, event);
        // All events for same customer go to same partition = ordered processing
    }
}
```

**Partitioning Best Practices**:
- **Number of partitions**: Start with `2 * number of consumer instances`
- **Key selection**: Use keys that distribute evenly (customer ID, user ID)
- **Avoid hot partitions**: Monitor partition-level metrics
- **Rebalancing**: More partitions = faster rebalancing, but more overhead

---

## Redis Performance Optimization

### Redis Configuration

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
      password: ${REDIS_PASSWORD}
      
      # Connection pooling (Lettuce)
      lettuce:
        pool:
          max-active: 20   # Max connections
          max-idle: 10     # Max idle connections
          min-idle: 5      # Min idle connections
          max-wait: 2000   # Max wait for connection (ms)
        
        # Timeouts
        shutdown-timeout: 100ms
      
      # Command timeout
      timeout: 2000ms
      
      # Clustering (if using Redis Cluster)
      cluster:
        nodes:
          - redis-1:6379
          - redis-2:6379
          - redis-3:6379
        max-redirects: 3
```

### Redis Usage Patterns

```java
// Good: Efficient Redis operations with proper data structures
@Service
public class RedisCacheService {
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    
    // Simple key-value caching
    public void cacheUser(String userId, UserDto user) {
        String key = "user:" + userId;
        redisTemplate.opsForValue().set(key, user, Duration.ofMinutes(30));
    }
    
    public Optional<UserDto> getCachedUser(String userId) {
        String key = "user:" + userId;
        UserDto user = (UserDto) redisTemplate.opsForValue().get(key);
        return Optional.ofNullable(user);
    }
    
    // Hash for storing multiple fields (more memory efficient than separate keys)
    public void cacheUserProfile(String userId, Map<String, String> profile) {
        String key = "profile:" + userId;
        redisTemplate.opsForHash().putAll(key, profile);
        redisTemplate.expire(key, Duration.ofHours(1));
    }
    
    // Sorted set for leaderboards/rankings
    public void updateScore(String userId, double score) {
        String key = "leaderboard:global";
        redisTemplate.opsForZSet().add(key, userId, score);
    }
    
    public Set<Object> getTopUsers(int count) {
        String key = "leaderboard:global";
        return redisTemplate.opsForZSet().reverseRange(key, 0, count - 1);
    }
    
    // Pipelining for batch operations
    public void batchCache(Map<String, UserDto> users) {
        redisTemplate.executePipelined(new SessionCallback<Object>() {
            @Override
            public Object execute(RedisOperations operations) {
                users.forEach((key, user) -> 
                    operations.opsForValue().set("user:" + key, user, Duration.ofMinutes(30))
                );
                return null;
            }
        });
    }
    
    // Distributed lock for critical sections
    public boolean executeWithLock(String lockKey, Duration lockTimeout, Runnable task) {
        Boolean acquired = stringRedisTemplate.opsForValue()
            .setIfAbsent(lockKey, "locked", lockTimeout);
        
        if (Boolean.TRUE.equals(acquired)) {
            try {
                task.run();
                return true;
            } finally {
                stringRedisTemplate.delete(lockKey);
            }
        }
        return false;
    }
}

// Bad: Large objects in Redis without expiration
public void badCaching() {
    // Never do this!
    redisTemplate.opsForValue().set("huge-data", hugeObject); // No TTL = memory leak
}
```

**Redis Best Practices**:
- **Use appropriate data structures**: Hashes for objects, Sorted Sets for rankings, Sets for uniqueness
- **Always set TTL**: Prevent memory leaks with expiration
- **Pipeline batch operations**: Reduce round trips for multiple commands
- **Monitor memory usage**: Use `maxmemory-policy allkeys-lru`
- **Key naming convention**: Use colons for namespacing (e.g., `user:123:profile`)
- **Avoid large values**: Keep values under 100KB when possible

### Session Management with Redis

```java
// Good: Redis-backed sessions for distributed systems
@Configuration
@EnableRedisHttpSession(maxInactiveIntervalInSeconds = 1800)  // 30 minutes
public class SessionConfig {
    
    @Bean
    public RedisSerializer<Object> springSessionDefaultRedisSerializer() {
        return new GenericJackson2JsonRedisSerializer();
    }
}
```

---

## PostgreSQL Performance Optimization

### Connection and Query Configuration

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/mydb?reWriteBatchedInserts=true&prepareThreshold=0
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    
    hikari:
      maximum-pool-size: 20
      connection-timeout: 30000
      
      data-source-properties:
        # PostgreSQL-specific performance settings
        ApplicationName: order-service
        cachePrepStmts: true
        prepStmtCacheSize: 250
        prepStmtCacheSqlLimit: 2048
        reWriteBatchedInserts: true  # Critical for batch INSERT performance
        defaultRowFetchSize: 100     # Reduce memory for large result sets
        
  jpa:
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        jdbc:
          batch_size: 50
          lob.non_contextual_creation: true
```

### Indexing Strategy

```sql
-- Good: Covering index for common queries
CREATE INDEX idx_users_email_status ON users(email, status) 
INCLUDE (name, created_at);  -- PostgreSQL 11+ covering index

-- Partial index for filtering
CREATE INDEX idx_active_users ON users(created_at) 
WHERE status = 'ACTIVE';

-- Multi-column index (order matters!)
CREATE INDEX idx_orders_customer_date ON orders(customer_id, order_date DESC);

-- GIN index for JSONB columns
CREATE INDEX idx_user_preferences ON users USING GIN(preferences);

-- Full-text search index
CREATE INDEX idx_products_search ON products USING GIN(to_tsvector('english', name || ' ' || description));
```

**Indexing Best Practices**:
- Create indexes for foreign keys
- Use covering indexes to avoid table lookups
- Partial indexes for filtered queries (e.g., `WHERE status = 'ACTIVE'`)
- Monitor index usage with `pg_stat_user_indexes`
- Remove unused indexes - they slow down writes
- Index column order: high cardinality first, range filters last

### Query Optimization

```java
// Good: Optimized queries with proper indexing
@Repository
public interface OrderRepository extends JpaRepository<Order, String> {
    
    // Index on (customer_id, order_date) makes this fast
    @Query("SELECT o FROM Order o WHERE o.customerId = :customerId " +
           "AND o.orderDate BETWEEN :startDate AND :endDate " +
           "ORDER BY o.orderDate DESC")
    List<Order> findRecentOrders(
        @Param("customerId") String customerId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );
    
    // DTO projection to fetch only needed columns
    @Query("SELECT new com.example.dto.OrderSummary(o.id, o.totalAmount, o.status) " +
           "FROM Order o WHERE o.customerId = :customerId")
    List<OrderSummary> findOrderSummaries(@Param("customerId") String customerId);
    
    // Native query for complex aggregations
    @Query(value = """
        SELECT DATE_TRUNC('day', order_date) as day,
               COUNT(*) as order_count,
               SUM(total_amount) as revenue
        FROM orders
        WHERE order_date >= :startDate
        GROUP BY DATE_TRUNC('day', order_date)
        ORDER BY day DESC
        """, nativeQuery = true)
    List<Object[]> getDailySalesReport(@Param("startDate") LocalDateTime startDate);
}

// Bad: N+1 query anti-pattern
public List<OrderDto> getOrdersWithItems(String customerId) {
    List<Order> orders = orderRepository.findByCustomerId(customerId);
    return orders.stream()
        .map(order -> {
            // This triggers a separate query for EACH order!
            List<OrderItem> items = order.getItems();
            return new OrderDto(order, items);
        })
        .toList();
}
```

### Batch Operations

```java
// Good: Efficient batch insert with JDBC batch and reWriteBatchedInserts
@Service
@Transactional
public class OrderService {
    
    @PersistenceContext
    private EntityManager entityManager;
    
    public void createOrders(List<CreateOrderRequest> requests) {
        int batchSize = 50;
        
        for (int i = 0; i < requests.size(); i++) {
            Order order = new Order(requests.get(i));
            entityManager.persist(order);
            
            if (i > 0 && i % batchSize == 0) {
                entityManager.flush();
                entityManager.clear();  // Clear persistence context to prevent memory issues
            }
        }
        
        entityManager.flush();
        entityManager.clear();
    }
    
    // Batch update using native query
    public int updateOrderStatus(List<String> orderIds, OrderStatus newStatus) {
        String sql = "UPDATE orders SET status = :status WHERE id = ANY(:ids)";
        return entityManager.createNativeQuery(sql)
            .setParameter("status", newStatus.name())
            .setParameter("ids", orderIds.toArray(new String[0]))
            .executeUpdate();
    }
}
```

### JSONB Column Optimization

```java
// Good: Using JSONB for flexible schemas
@Entity
@Table(name = "users")
public class User {
    
    @Id
    private String id;
    
    // Store preferences as JSONB
    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> preferences;
    
    // Metadata as JSONB
    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private JsonNode metadata;
}

// Query JSONB fields
@Query(value = """
    SELECT * FROM users 
    WHERE preferences->>'theme' = :theme
      AND (metadata->'subscription'->>'plan')::text = :plan
    """, nativeQuery = true)
List<User> findByPreferencesAndPlan(
    @Param("theme") String theme, 
    @Param("plan") String plan
);
```

### PostgreSQL-Specific Features

**Partitioning for large tables**:
```sql
-- Range partitioning by date
CREATE TABLE orders (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL,
    order_date TIMESTAMP NOT NULL,
    total_amount DECIMAL(10, 2)
) PARTITION BY RANGE (order_date);

CREATE TABLE orders_2025_01 PARTITION OF orders
    FOR VALUES FROM ('2025-01-01') TO ('2025-02-01');

CREATE TABLE orders_2025_02 PARTITION OF orders
    FOR VALUES FROM ('2025-02-01') TO ('2025-03-01');

-- Automatically improved query performance for date range queries
```

**Materialized Views for complex aggregations**:
```sql
-- Create materialized view
CREATE MATERIALIZED VIEW daily_sales_summary AS
SELECT 
    DATE_TRUNC('day', order_date) as sale_date,
    COUNT(*) as order_count,
    SUM(total_amount) as total_revenue,
    AVG(total_amount) as avg_order_value
FROM orders
WHERE order_date >= CURRENT_DATE - INTERVAL '90 days'
GROUP BY DATE_TRUNC('day', order_date);

-- Create index on materialized view
CREATE INDEX idx_daily_sales_date ON daily_sales_summary(sale_date);

-- Refresh periodically (scheduled job)
REFRESH MATERIALIZED VIEW CONCURRENTLY daily_sales_summary;
```

---

## Performance Monitoring and Observability

### Application Metrics (Micrometer + Prometheus)

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus,info
  
  metrics:
    enable:
      jvm: true
      process: true
      system: true
      jdbc: true
      hikaricp: true
    
    export:
      prometheus:
        enabled: true
    
    distribution:
      percentiles-histogram:
        http.server.requests: true
      percentiles:
        http.server.requests: 0.5, 0.95, 0.99
      slo:
        http.server.requests: 100ms, 200ms, 500ms, 1s
```

**Custom Metrics**:
```java
@Service
public class OrderService {
    
    private final Counter orderCounter;
    private final Timer orderProcessingTimer;
    private final DistributionSummary orderValueDistribution;
    
    public OrderService(MeterRegistry registry) {
        this.orderCounter = Counter.builder("orders.created")
            .description("Number of orders created")
            .tag("service", "order-service")
            .register(registry);
        
        this.orderProcessingTimer = Timer.builder("orders.processing.time")
            .description("Order processing time")
            .register(registry);
        
        this.orderValueDistribution = DistributionSummary.builder("orders.value")
            .description("Order value distribution")
            .baseUnit("USD")
            .register(registry);
    }
    
    public Order createOrder(CreateOrderRequest request) {
        return orderProcessingTimer.record(() -> {
            Order order = processOrder(request);
            orderCounter.increment();
            orderValueDistribution.record(order.getTotalAmount());
            return order;
        });
    }
}
```

### Logging Best Practices

```java
// Good: Structured logging with context
@Slf4j
@Service
public class PaymentService {
    
    public PaymentResult processPayment(PaymentRequest request) {
        // Use MDC for correlation ID
        MDC.put("paymentId", request.getId());
        MDC.put("customerId", request.getCustomerId());
        
        try {
            log.info("Processing payment: amount={}, method={}", 
                request.getAmount(), request.getMethod());
            
            PaymentResult result = executePayment(request);
            
            log.info("Payment processed successfully: transactionId={}, status={}", 
                result.getTransactionId(), result.getStatus());
            
            return result;
            
        } catch (PaymentException e) {
            log.error("Payment processing failed: reason={}", e.getReason(), e);
            throw e;
        } finally {
            MDC.clear();
        }
    }
}

// Bad: Excessive logging in hot paths
public void processHighFrequencyEvent(Event event) {
    log.debug("Processing event: {}", event); // Don't log every event at debug level!
    // Processing logic
    log.debug("Event processed: {}", event);  // Causes performance degradation
}
```

**Logging Configuration**:
```yaml
logging:
  level:
    root: INFO
    com.julian.razif.figaro.bigdata: DEBUG
    org.hibernate.SQL: WARN  # Only log SQL in development
    org.hibernate.type.descriptor.sql.BasicBinder: WARN
  
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg %X{paymentId} %X{customerId}%n"
  
  file:
    name: logs/application.log
    max-size: 100MB
    max-history: 30
```

---

## Code Review Checklist for Performance

Use this comprehensive checklist during code reviews to identify performance issues before they reach production:

### Algorithmic Efficiency

- [ ] Are there any obvious algorithmic inefficiencies (O(n²) or worse)?
  - Look for nested loops operating on same collections
  - Check for recursive algorithms without memoization
  - Verify sorting/searching using appropriate algorithms
  - Example bad pattern: `for (Item a : items) { for (Item b : items) { ... } }`
  
- [ ] Are data structures appropriate for their use?
  - Use `HashSet` for O(1) lookups, not `ArrayList.contains()`
  - Use `HashMap` for key-value operations, not linear search
  - Use `LinkedList` only for frequent insertions at head, otherwise use `ArrayList`
  - Use `TreeMap` for sorted operations
  - Consider specialized collections (IntSet, LongSet from fastutil for primitives)

- [ ] Are there unnecessary computations or repeated work?
  - Check for redundant calculations in loops
  - Look for repeated method calls that return same value
  - Verify object creation/allocation happens only when needed
  - Example bad pattern: `for (int i = 0; i < list.size(); i++)` instead of `for (Item item : list)`

### Caching and Data Retrieval

- [ ] Is caching used where appropriate, and is invalidation handled correctly?
  - Verify caches have TTL/expiration
  - Check for cache invalidation on updates/deletes
  - Ensure cache size is bounded (prevent unbounded growth)
  - Verify cache keys are specific enough to avoid collisions
  - Look for stale cache issues

- [ ] Are database queries optimized, indexed, and free of N+1 issues?
  - Check for N+1 query patterns (use fetch joins or DTO projections)
  - Verify indexes exist on foreign keys and frequently filtered columns
  - Look for SELECT * queries (should select specific columns)
  - Check for missing WHERE clauses or excessive result sets
  - Verify batch operations use batch processing (not individual statements)
  - Example bad pattern: `order.getItems()` in loop when loading orders

- [ ] Are large payloads paginated, streamed, or chunked?
  - Verify large lists return paginated results
  - Check for Stream usage for large result sets
  - Look for pagination limits on API endpoints
  - Verify batch processing for large data imports

### Resource Management

- [ ] Are there any memory leaks or unbounded resource usage?
  - Check for unclosed streams, connections, or readers
  - Look for static collections that grow without bounds
  - Verify resources are properly cleaned up in finally blocks or try-with-resources
  - Check for proper resource cleanup in exception handlers
  - Look for circular references preventing garbage collection

- [ ] Are network requests minimized, batched, and retried on failure?
  - Verify requests to external services are batched when possible
  - Check for circuit breakers on external dependencies
  - Look for retry logic with exponential backoff
  - Verify requests have appropriate timeouts
  - Check for connection reuse/pooling

### Concurrency and I/O

- [ ] Are there any blocking operations in hot paths?
  - Look for Thread.sleep() or blocking operations
  - Verify I/O operations use non-blocking/async patterns
  - Check database operations don't block request threads
  - Look for heavy computation in request handling (should be async)
  - Verify external API calls are non-blocking

- [ ] Is logging in hot paths minimized and structured?
  - Avoid logging in tight loops or frequently called methods
  - Check log levels (DEBUG/TRACE in production code paths)
  - Look for expensive log operations (object serialization, method calls)
  - Verify structured logging with MDC for tracing
  - Example bad pattern: `log.debug("Processing: {}", expensiveToStringMethod())`

### Code Organization and Testing

- [ ] Are performance-critical code paths documented and tested?
  - Verify critical paths have performance documentation
  - Check for comments explaining optimization decisions
  - Look for performance assumptions documented in code
  - Verify developers understand performance implications

- [ ] Are there automated tests or benchmarks for performance-sensitive code?
  - Look for JMH benchmarks for critical algorithms
  - Verify load tests for API endpoints
  - Check for database query performance tests
  - Look for memory leak detection tests

- [ ] Are there alerts for performance regressions?
  - Verify continuous profiling is in place
  - Check for performance regression detection
  - Look for SLA violations alerting
  - Verify metrics are monitored (latency percentiles, throughput)

### Anti-Patterns and Common Issues

- [ ] Are there any anti-patterns present?
  - [ ] Avoid `SELECT *` in queries (specify needed columns)
  - [ ] Avoid blocking I/O in request handlers (use async)
  - [ ] Avoid global variables/static state (thread safety, GC pressure)
  - [ ] Avoid String concatenation in loops (use StringBuilder)
  - [ ] Avoid creating new ExecutorServices in request handlers
  - [ ] Avoid unbounded thread pools (`newCachedThreadPool`)
  - [ ] Avoid synchronized blocks in hot paths (use concurrent structures)
  - [ ] Avoid reflection in performance-critical code
  - [ ] Avoid catching generic Exception in hot paths (exception handling has overhead)
  - [ ] Avoid repeated Pattern compilation (cache compiled patterns)
  - [ ] Avoid Date/Time parsing in hot paths (cache or use instant)
  - [ ] Avoid toString() calls in production logging

### Review Guidelines

**Performance Review Focus Areas** (in order of impact):
1. Algorithmic complexity (O(n²) can be worse than 10x resource usage)
2. N+1 query patterns and database optimization
3. Memory leaks and unbounded resource usage
4. Unnecessary object allocation in hot paths
5. Blocking operations in concurrent contexts
6. Missing caching for expensive operations
7. Inefficient synchronization patterns
8. Excessive logging in hot paths

**Performance Measurement**:
- Require benchmarks or load tests for performance-critical changes
- Profile code changes to verify improvements
- Monitor production metrics before/after deployments
- Use flame graphs to identify bottlenecks

**Performance Documentation**:
- Document performance assumptions in code comments
- Include complexity analysis (Big O) for algorithms
- Document expected resource usage (memory, CPU, network)
- Maintain performance-related ADRs (Architecture Decision Records)

---

## Scenario-Based Performance Checklists

### High-Throughput API (>10K req/sec)

- [ ] Enable virtual threads for I/O operations
- [ ] Configure response compression (gzip/brotli)
- [ ] Implement multi-level caching (Redis L1, application L2)
- [ ] Use connection pooling with appropriate sizing
- [ ] Enable HTTP/2 or HTTP/3
- [ ] Implement rate limiting to prevent overload
- [ ] Use async/non-blocking operations for external calls
- [ ] Optimize database queries with proper indexes
- [ ] Monitor GC pause times (< 10ms for low latency)
- [ ] Use CDN for static content
- [ ] Implement circuit breakers for external dependencies

### Low-Latency Application (p99 < 100ms)

- [ ] Use ZGC for sub-millisecond GC pauses
- [ ] Minimize object allocation in hot paths
- [ ] Use primitive collections (Eclipse Collections, Fastutil)
- [ ] Avoid reflection in critical paths
- [ ] Use method handles instead of reflection
- [ ] Pre-allocate and reuse objects when possible
- [ ] Use off-heap memory for large caches
- [ ] Optimize database queries (< 10ms query time)
- [ ] Use database connection pooling (minimum idle connections)
- [ ] Monitor and optimize thread contention
- [ ] Use lock-free data structures when appropriate

### High-Volume Data Processing (Kafka/Batch)

- [ ] Use batch processing for Kafka consumers
- [ ] Configure appropriate partition count (2x consumer instances)
- [ ] Enable compression for Kafka messages (lz4/zstd)
- [ ] Use batch inserts for database operations
- [ ] Implement idempotent consumers
- [ ] Configure proper consumer concurrency
- [ ] Use bulk operations for Redis
- [ ] Monitor consumer lag continuously
- [ ] Implement backpressure mechanisms
- [ ] Use parallel streams for CPU-bound transformations
- [ ] Configure appropriate fetch sizes for large result sets

### Memory-Constrained Environment (< 2GB heap)

- [ ] Use Generational ZGC or Shenandoah
- [ ] Minimize object retention in caches
- [ ] Use weak/soft references for caches
- [ ] Avoid large object graphs in memory
- [ ] Use streaming for large data sets
- [ ] Configure proper heap size (Xms = Xmx)
- [ ] Monitor and tune survivor spaces
- [ ] Use primitive types instead of wrappers
- [ ] Implement pagination for large queries
- [ ] Offload caching to Redis instead of heap

---

## Troubleshooting Guide

### High CPU Usage

**Symptoms**: CPU at 80-100%, slow response times

**Diagnosis**:
```bash
# Take thread dump
jstack <pid> > threaddump.txt

# Profile with async-profiler
java -agentpath:/path/to/libasyncProfiler.so=start,event=cpu,file=profile.html -jar app.jar
```

**Common Causes**:
- Inefficient algorithms (O(n²) loops)
- Excessive garbage collection
- CPU-intensive operations in request threads
- Regex compilation in hot paths
- Blocking operations on virtual threads

**Solutions**:
- Move CPU-intensive work to background threads
- Use more efficient algorithms and data structures
- Cache compiled regex patterns
- Optimize database queries to reduce processing
- Use ForkJoinPool for parallel processing

### High Memory Usage / OutOfMemoryError

**Symptoms**: Increasing heap usage, OOMEs, frequent GC

**Diagnosis**:
```bash
# Take heap dump on OOM
java -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp/heapdump.hprof -jar app.jar

# Analyze heap dump with MAT (Memory Analyzer Tool)
```

**Common Causes**:
- Memory leaks (unclosed resources, static collections)
- Unbounded caches without eviction
- Large object retention in HTTP sessions
- N+1 query problems loading large object graphs
- Incorrect GC tuning

**Solutions**:
- Implement proper cache eviction policies
- Use weak/soft references for caches
- Fix resource leaks (close JDBC connections, streams)
- Optimize queries to fetch only needed data
- Increase heap size or optimize object retention

### Database Connection Pool Exhaustion

**Symptoms**: Timeout waiting for database connection

**Diagnosis**:
```java
// Monitor HikariCP metrics
hikariDataSource.getHikariPoolMXBean().getActiveConnections();
hikariDataSource.getHikariPoolMXBean().getIdleConnections();
hikariDataSource.getHikariPoolMXBean().getThreadsAwaitingConnection();
```

**Common Causes**:
- Long-running transactions
- Missing @Transactional annotations
- Connection leaks (not closing connections)
- Pool size too small for load
- Database performance issues

**Solutions**:
- Reduce transaction scope
- Increase connection pool size
- Add leak detection: `leak-detection-threshold: 60000`
- Optimize slow queries
- Use read replicas for read operations

### Kafka Consumer Lag

**Symptoms**: Messages not processed in time, increasing lag

**Diagnosis**:
```bash
# Check consumer lag
kafka-consumer-groups --bootstrap-server localhost:9092 --describe --group order-processing-service
```

**Common Causes**:
- Slow message processing
- Insufficient consumer instances
- Database bottleneck
- Too few partitions

**Solutions**:
- Increase consumer concurrency
- Optimize message processing logic
- Use batch processing
- Add more partitions to topic
- Scale horizontally (add consumer instances)

### Redis Connection Timeouts

**Symptoms**: `RedisConnectionException`, slow cache operations

**Diagnosis**:
```bash
# Monitor Redis
redis-cli INFO stats
redis-cli CLIENT LIST
redis-cli SLOWLOG GET 10
```

**Common Causes**:
- Connection pool exhausted
- Redis server overloaded
- Network latency
- Large value sizes
- Blocking commands (KEYS *)

**Solutions**:
- Increase connection pool size
- Use pipelining for batch operations
- Avoid KEYS command (use SCAN)
- Optimize value sizes
- Monitor Redis memory and eviction policy

---

## Pro Tips and Advanced Techniques

### 1. Virtual Thread Pinning Prevention

```java
// Bad: synchronized blocks pin virtual threads
public synchronized void processRequest() {
    // This blocks the carrier thread!
}

// Good: Use ReentrantLock for virtual thread-friendly locking
private final ReentrantLock lock = new ReentrantLock();

public void processRequest() {
    lock.lock();
    try {
        // Processing logic
    } finally {
        lock.unlock();
    }
}
```

### 2. Efficient Collection Sizing

```java
// Good: Pre-size collections to avoid resizing
List<User> users = new ArrayList<>(expectedSize);
Map<String, User> userMap = new HashMap<>(expectedSize * 4 / 3); // account for load factor
```

### 3. String Optimization

```java
// Bad: String concatenation in loops
String result = "";
for (String item : items) {
    result += item; // Creates new String object each iteration
}

// Good: Use StringBuilder
StringBuilder sb = new StringBuilder(items.size() * 20); // estimate capacity
for (String item : items) {
    sb.append(item);
}
String result = sb.toString();

// Better: Use String.join for simple cases
String result = String.join("", items);
```

### 4. Lazy Initialization Pattern

```java
// Good: Double-checked locking with volatile (for singleton-like scenarios)
private volatile ExpensiveObject instance;

public ExpensiveObject getInstance() {
    if (instance == null) {
        synchronized (this) {
            if (instance == null) {
                instance = new ExpensiveObject();
            }
        }
    }
    return instance;
}

// Better: Use Supplier for lazy initialization
private final Supplier<ExpensiveObject> lazyInstance = 
    Suppliers.memoize(() -> new ExpensiveObject());

public ExpensiveObject getInstance() {
    return lazyInstance.get();
}
```

### 5. Database Query Result Streaming

```java
// Good: Stream large result sets to avoid loading all into memory
@Query("SELECT u FROM User u WHERE u.status = :status")
Stream<User> streamActiveUsers(@Param("status") UserStatus status);

// Usage with try-with-resources
@Transactional(readOnly = true)
public void processAllUsers() {
    try (Stream<User> userStream = userRepository.streamActiveUsers(UserStatus.ACTIVE)) {
        userStream
            .filter(user -> user.getCreatedAt().isAfter(LocalDateTime.now().minusDays(30)))
            .forEach(this::processUser);
    }
}
```

### 6. Efficient DTO Mapping

```java
// Good: Use MapStruct for compile-time DTO mapping (no reflection)
@Mapper(componentModel = "spring")
public interface UserMapper {
    
    UserDto toDto(User user);
    
    List<UserDto> toDtoList(List<User> users);
    
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    void updateEntity(@MappingTarget User entity, UpdateUserRequest request);
}
```

### 7. Read-Through Cache Pattern

```java
// Good: Implement read-through cache with Spring Cache
@Service
public class UserService {
    
    @Cacheable(value = "users", key = "#id", unless = "#result == null")
    public UserDto getUser(String id) {
        return userRepository.findById(id)
            .map(userMapper::toDto)
            .orElse(null);
    }
    
    // Write-through: update cache and database
    @CachePut(value = "users", key = "#id")
    public UserDto updateUser(String id, UpdateUserRequest request) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new UserNotFoundException(id));
        userMapper.updateEntity(user, request);
        return userMapper.toDto(userRepository.save(user));
    }
}
```

### 8. Circuit Breaker for External Services

```java
// Good: Resilience4j circuit breaker
@Service
public class ExternalApiService {
    
    private final CircuitBreaker circuitBreaker;
    
    public ExternalApiService(CircuitBreakerRegistry registry) {
        this.circuitBreaker = registry.circuitBreaker("external-api");
    }
    
    public ApiResponse callExternalApi(ApiRequest request) {
        return circuitBreaker.executeSupplier(() -> {
            // External API call
            return restTemplate.postForObject(apiUrl, request, ApiResponse.class);
        });
    }
}

// Configuration
resilience4j:
  circuitbreaker:
    instances:
      external-api:
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
        sliding-window-size: 10
        minimum-number-of-calls: 5
```

### 9. Database Read Replicas

```java
// Good: Route read queries to replicas
@Configuration
public class DataSourceConfig {
    
    @Bean
    public DataSource dataSource() {
        Map<Object, Object> targetDataSources = new HashMap<>();
        targetDataSources.put(DataSourceType.PRIMARY, primaryDataSource());
        targetDataSources.put(DataSourceType.REPLICA, replicaDataSource());
        
        ReplicationRoutingDataSource routingDataSource = new ReplicationRoutingDataSource();
        routingDataSource.setTargetDataSources(targetDataSources);
        routingDataSource.setDefaultTargetDataSource(primaryDataSource());
        
        return routingDataSource;
    }
}

// Use @Transactional(readOnly = true) to route to replica
@Transactional(readOnly = true)
public List<User> findAllUsers() {
    return userRepository.findAll(); // Executes on replica
}
```

### 10. Scheduled Cache Warming

```java
// Good: Pre-populate cache during off-peak hours
@Service
public class CacheWarmingService {
    
    @Scheduled(cron = "0 0 2 * * ?")  // 2 AM daily
    public void warmCache() {
        log.info("Starting cache warming");
        
        // Load frequently accessed data
        List<Product> popularProducts = productRepository.findPopularProducts();
        popularProducts.forEach(product -> 
            cacheManager.getCache("products").put(product.getId(), product)
        );
        
        log.info("Cache warming completed: {} products cached", popularProducts.size());
    }
}
```

---

## Validation Commands

```bash
# Build project
./mvnw clean package -DskipTests

# Run with performance tuning
java -XX:+UseZGC \
     -XX:+UseStringDeduplication \
     -XX:+AlwaysPreTouch \
     -Xmx8g -Xms8g \
     -jar target/app.jar

# Load testing
# Apache Bench
ab -n 10000 -c 100 http://localhost:8080/api/users

# Gatling for comprehensive load testing
./mvnw gatling:test

# Monitor JVM metrics
jcmd <pid> VM.info
jcmd <pid> GC.heap_info
jcmd <pid> Thread.print

# Profile application
java -agentpath:/path/to/libasyncProfiler.so=start,event=cpu,file=cpu-profile.html -jar app.jar
```

---

## Additional Resources

- [Java Performance: The Definitive Guide](https://www.oreilly.com/library/view/java-performance-the/9781449363512/)
- [Spring Boot Performance Tuning](https://spring.io/blog/2023/10/16/runtime-efficiency-with-spring)
- [Kafka Performance Optimization](https://kafka.apache.org/documentation/#producerconfigs)
- [PostgreSQL Performance Wiki](https://wiki.postgresql.org/wiki/Performance_Optimization)
- [Redis Best Practices](https://redis.io/docs/management/optimization/)
- [JDK 21+ Features Guide](https://docs.oracle.com/en/java/javase/21/core/java-core-libraries1.html)
- [Async-Profiler](https://github.com/async-profiler/async-profiler)
- [Micrometer Metrics](https://micrometer.io/docs)

---

## Maintenance Notes

- Review and update GC flags when upgrading JDK versions
- Benchmark connection pool sizes after infrastructure changes
- Update Kafka configurations based on throughput requirements
- Regularly analyze slow query logs and add indexes
- Monitor Redis memory usage and adjust eviction policies
- Profile application under realistic load periodically
- Keep dependencies updated for performance improvements and security patches
