package com.julian.razif.figaro.bigdata.dynamodb.config;

import com.julian.razif.figaro.bigdata.appconfig.DynamoDBConfigData;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.retries.StandardRetryStrategy;
import software.amazon.awssdk.retries.api.BackoffStrategy;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.net.URI;
import java.time.Duration;

/**
 * Configuration class for Amazon DynamoDB client setup.
 * <p>
 * This class provides the necessary configuration for connecting to DynamoDB,
 * including endpoint configuration, AWS region, credentials, and connection parameters.
 * The configuration is loaded from {@link DynamoDBConfigData}.
 * </p>
 * <p>
 * This configuration creates three types of DynamoDB clients:
 * <ul>
 * <li>{@link DynamoDbClient} - Synchronous blocking client for traditional operations</li>
 * <li>{@link DynamoDbAsyncClient} - Asynchronous non-blocking client for high-throughput scenarios</li>
 * <li>{@link DynamoDbEnhancedClient} - Enhanced client with object mapping capabilities</li>
 * </ul>
 * </p>
 * <p>
 * <strong>Performance Optimization:</strong> Uses Apache HTTP Client for sync operations and Netty for async
 * operations,
 * with connection pooling, keep-alive, and retry strategies configured for production workloads.
 * </p>
 * <p>
 * <strong>Security Note:</strong> Credentials are provided via environment variables or AWS credentials provider chain.
 * If an access key and secret key are empty in configuration, the default credentials provider chain is used.
 * </p>
 *
 * @author Julian Razif Figaro
 * @version 1.0
 * @see DynamoDBConfigData
 * @since 1.0
 */
@Configuration
public class DynamoDBConfig {

  private final DynamoDBConfigData dynamoConfigData;

  /**
   * Constructs a new DynamoDBConfig with the specified configuration data.
   *
   * @param dynamoConfigData the DynamoDB configuration properties, must not be {@code null}
   */
  public DynamoDBConfig(
    DynamoDBConfigData dynamoConfigData) {

    this.dynamoConfigData = dynamoConfigData;
  }

  /**
   * Creates an AWS credentials provider based on the configuration.
   * <p>
   * If an access key and secret key are provided in configuration, uses static credentials.
   * Otherwise, uses the default credentials provider chain which checks:
   * <ol>
   * <li>Environment variables (AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY)</li>
   * <li>Java system properties</li>
   * <li>AWS credentials file (~/.aws/credentials)</li>
   * <li>IAM role for Amazon EC2/ECS/Lambda</li>
   * </ol>
   * </p>
   *
   * @return configured AWS credentials provider
   */
  @Bean
  public AwsCredentialsProvider awsCredentialsProvider() {
    // Use default credentials provider chain if credentials are not explicitly configured
    if (dynamoConfigData.awsAccesskey() == null || dynamoConfigData.awsAccesskey().isEmpty()) {
      return DefaultCredentialsProvider.builder().build();
    }

    // Use static credentials if provided (not recommended for production)
    return StaticCredentialsProvider.create(
      AwsBasicCredentials.create(
        dynamoConfigData.awsAccesskey(), dynamoConfigData.awsSecretkey()
      )
    );
  }

  /**
   * Creates a backoff strategy for retry operations.
   * <p>
   * Implements exponential backoff with jitter to prevent a thundering herd problem.
   * The delay calculation follows exponential growth capped at maximum backoff time.
   * Jitter is automatically applied to distribute retry attempts across time.
   * </p>
   * <p>
   * <strong>Backoff Parameters:</strong>
   * <ul>
   * <li>Base delay: 100ms (initial retry delay)</li>
   * <li>Max backoff: 20 seconds (cap on maximum delay)</li>
   * <li>Growth: Exponential (doubles with each attempt)</li>
   * </ul>
   * </p>
   *
   * @return configured backoff strategy using the modern API
   */
  @Bean
  public BackoffStrategy backoffStrategy() {
    // Use exponentialDelay, which is the modern API
    return BackoffStrategy.exponentialDelay(
      Duration.ofMillis(100),  // Base delay: 100ms
      Duration.ofSeconds(20)   // Max backoff: 20 seconds
    );
  }

  /**
   * Creates a retry strategy for DynamoDB operations.
   * <p>
   * Uses StandardRetryStrategy with configurable max attempts and backoff strategy.
   * This strategy handles transient errors, throttling, and network issues with
   * intelligent retry logic, including exponential backoff with jitter.
   * </p>
   * <p>
   * <strong>Retry Behavior:</strong>
   * <ul>
   * <li>Max attempts: Configured via
   * {@link com.julian.razif.figaro.bigdata.appconfig.DynamoDBConfigData#maxRetry()}</li>
   * <li>Backoff: Exponential with jitter (prevents thundering herd)</li>
   * <li>Throttling: Automatically handles DynamoDB throttling with adaptive backoff</li>
   * <li>Transient errors: Retries on network errors, timeouts, and 5xx responses</li>
   * </ul>
   * </p>
   * <p>
   * <strong>Performance Note:</strong> Uses {@link StandardRetryStrategy} for predictable behavior.
   * This is the modern API that replaces the legacy RetryPolicy.
   * </p>
   *
   * @param backoffStrategy the backoff strategy for calculating retry delays
   * @return configured retry strategy
   */
  @Bean
  public StandardRetryStrategy retryStrategy(
    BackoffStrategy backoffStrategy) {

    StandardRetryStrategy.Builder builder = StandardRetryStrategy
      .builder()
      .maxAttempts(dynamoConfigData.maxRetry())
      .backoffStrategy(backoffStrategy);

    if (Boolean.TRUE.equals(dynamoConfigData.throttledRetries())) {
      builder.throttlingBackoffStrategy(backoffStrategy);
    }

    return builder.build();
  }

  /**
   * Creates client override configuration with timeouts and retry strategy.
   * <p>
   * Configures API call timeouts and retry behavior for all DynamoDB clients.
   * These settings help prevent indefinite blocking and implement graceful degradation
   * under failure conditions.
   * </p>
   * <p>
   * <strong>Configuration Details:</strong>
   * <ul>
   * <li>API call timeout: Total time for the entire API call including all retry attempts</li>
   * <li>API call attempt timeout: Time limit for each attempt</li>
   * <li>Retry strategy: Max attempts with exponential backoff and jitter</li>
   * </ul>
   * </p>
   * <p>
   * <strong>Implementation Note:</strong> Uses modern AWS SDK V2 retry APIs.
   * The {@link StandardRetryStrategy} replaces the legacy RetryPolicy for better
   * performance and more predictable behavior.
   * </p>
   *
   * @param retryStrategy the retry strategy for handling transient failures
   * @return configured client override settings
   */
  @Bean
  public ClientOverrideConfiguration clientOverrideConfiguration(
    StandardRetryStrategy retryStrategy) {

    // Configures client timeouts and retry behavior with modern APIs
    return ClientOverrideConfiguration
      .builder()
      .apiCallTimeout(Duration.ofMillis(dynamoConfigData.clientExecutionTimeout()))
      .apiCallAttemptTimeout(Duration.ofMillis(dynamoConfigData.requestTimeout()))
      .retryStrategy(retryStrategy)  // Use StandardRetryStrategy
      .build();
  }

  /**
   * Creates a synchronous DynamoDB client with Apache HTTP Client.
   * <p>
   * This client uses Apache HTTP Client for connection pooling and blocking I/O operations.
   * Use this for traditional blocking operations or when working with Spring Boot's synchronous programming model.
   * </p>
   * <p>
   * <strong>Performance Notes:</strong>
   * <ul>
   * <li>Connection pool size: {@link DynamoDBConfigData#maxConnections()}</li>
   * <li>Connection reuse: Configured with keep-alive and TTL</li>
   * <li>Timeout protection: Socket and connection timeouts prevent hanging requests</li>
   * </ul>
   * </p>
   *
   * @param credentialsProvider AWS credentials provider
   * @param clientConfig        client override configuration
   * @return configured synchronous DynamoDB client
   */
  @Bean
  public DynamoDbClient dynamoDbClient(
    AwsCredentialsProvider credentialsProvider,
    ClientOverrideConfiguration clientConfig) {

    // Configure Apache HTTP Client for connection pooling and performance
    ApacheHttpClient.Builder httpClientBuilder = ApacheHttpClient
      .builder()
      .maxConnections(dynamoConfigData.maxConnections())
      .connectionTimeout(Duration.ofMillis(dynamoConfigData.connectionTimeout()))
      .socketTimeout(Duration.ofMillis(dynamoConfigData.socketTimeout()))
      .connectionTimeToLive(Duration.ofMillis(dynamoConfigData.connectionTTL()))
      .connectionMaxIdleTime(Duration.ofMillis(dynamoConfigData.connectionMaxIdleMillis()))
      .tcpKeepAlive(dynamoConfigData.tcpKeepAlive())
      .expectContinueEnabled(true);

    // Builds client with region and endpoint override
    return DynamoDbClient
      .builder()
      .region(Region.of(dynamoConfigData.awsRegion()))
      .endpointOverride(URI.create("https://" + dynamoConfigData.dynamodbEndpoint()))
      .credentialsProvider(credentialsProvider)
      .overrideConfiguration(clientConfig)
      .httpClientBuilder(httpClientBuilder)
      .build();
  }

  /**
   * Creates an asynchronous DynamoDB client with Netty NIO.
   * <p>
   * This client uses Netty's non-blocking I/O for high-throughput, low-latency operations.
   * Prefer this client for high-volume data processing, Kafka consumers, or async Spring WebFlux applications.
   * </p>
   * <p>
   * <strong>Performance Advantages:</strong>
   * <ul>
   * <li>Non-blocking I/O: Single event loop can handle thousands of concurrent requests</li>
   * <li>Lower resource usage: Fewer threads compared to a synchronous client</li>
   * <li>Better scalability: Suitable for high-throughput scenarios (>10K req/sec)</li>
   * </ul>
   * </p>
   *
   * @param credentialsProvider AWS credentials provider
   * @param clientConfig        client override configuration
   * @return configured asynchronous DynamoDB client
   */
  @Bean
  public DynamoDbAsyncClient dynamoDbAsyncClient(
    AwsCredentialsProvider credentialsProvider,
    ClientOverrideConfiguration clientConfig) {

    // Configure Netty NIO client for non-blocking async operations
    NettyNioAsyncHttpClient.Builder asyncHttpClientBuilder = NettyNioAsyncHttpClient
      .builder()
      .maxConcurrency(dynamoConfigData.maxConnections())
      .connectionTimeout(Duration.ofMillis(dynamoConfigData.connectionTimeout()))
      .connectionMaxIdleTime(Duration.ofMillis(dynamoConfigData.connectionMaxIdleMillis()))
      .connectionTimeToLive(Duration.ofMillis(dynamoConfigData.connectionTTL()))
      .readTimeout(Duration.ofMillis(dynamoConfigData.socketTimeout()))
      .writeTimeout(Duration.ofMillis(dynamoConfigData.socketTimeout()))
      .tcpKeepAlive(dynamoConfigData.tcpKeepAlive());

    // Builds client with region and endpoint configuration
    return DynamoDbAsyncClient
      .builder()
      .region(Region.of(dynamoConfigData.awsRegion()))
      .endpointOverride(URI.create("https://" + dynamoConfigData.dynamodbEndpoint()))
      .credentialsProvider(credentialsProvider)
      .overrideConfiguration(clientConfig)
      .httpClientBuilder(asyncHttpClientBuilder)
      .build();
  }

  /**
   * Creates an enhanced DynamoDB client for object mapping.
   * <p>
   * The Enhanced Client provides a high-level API for mapping Java objects to DynamoDB items
   * using annotations like {@code @DynamoDbBean}, {@code @DynamoDbPartitionKey}, etc.
   * </p>
   * <p>
   * <strong>Use Cases:</strong>
   * <ul>
   * <li>Object-relational mapping for DynamoDB</li>
   * <li>Type-safe query and scan operations</li>
   * <li>Automatic serialization/deserialization</li>
   * <li>Transactional operations with Java objects</li>
   * </ul>
   * </p>
   *
   * @param dynamoDbClient the synchronous DynamoDB client
   * @return configured enhanced DynamoDB client
   */
  @Bean
  public DynamoDbEnhancedClient dynamoDbEnhancedClient(
    DynamoDbClient dynamoDbClient) {

    return DynamoDbEnhancedClient
      .builder()
      .dynamoDbClient(dynamoDbClient)
      .build();
  }

}
