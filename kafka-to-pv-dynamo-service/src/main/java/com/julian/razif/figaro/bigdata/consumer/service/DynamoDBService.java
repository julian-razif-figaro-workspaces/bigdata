package com.julian.razif.figaro.bigdata.consumer.service;

import com.julian.razif.figaro.bigdata.appconfig.DynamonDBConfigData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Service for asynchronous DynamoDB operations.
 * <p>
 * This service provides high-performance, non-blocking methods for persisting session data
 * to DynamoDB tables. It uses the AWS SDK V2 asynchronous client for optimal throughput
 * in high-concurrency scenarios.
 * </p>
 * <p>
 * <strong>Performance Characteristics:</strong>
 * <ul>
 * <li>Non-blocking I/O for high throughput (>10K req/sec)</li>
 * <li>Automatic retry handling with exponential backoff</li>
 * <li>Connection pooling and reuse</li>
 * <li>Structured logging for monitoring and debugging</li>
 * </ul>
 * </p>
 *
 * @author Julian Razif Figaro
 * @version 1.0
 * @since 1.0
 */
@Service
public class DynamoDBService {

  private static final Logger logger = LoggerFactory.getLogger(DynamoDBService.class);

  private final DynamoDbAsyncClient dynamoDbAsyncClient;
  private final DynamonDBConfigData configData;

  /**
   * Constructs a new DynamoDB service.
   *
   * @param dynamoDbAsyncClient AWS DynamoDB asynchronous client for non-blocking operations
   * @param configData          DynamoDB configuration properties including table names and timeouts
   */
  public DynamoDBService(
    DynamoDbAsyncClient dynamoDbAsyncClient, DynamonDBConfigData configData) {

    this.dynamoDbAsyncClient = dynamoDbAsyncClient;
    this.configData = configData;
  }

  /**
   * Asynchronously saves a session record to DynamoDB.
   * <p>
   * This method creates a new session item with the provided attributes and persists it
   * to the configured DynamoDB table. The operation is non-blocking and returns a
   * {@link CompletableFuture} that completes when the write operation finishes.
   * </p>
   * <p>
   * <strong>Error Handling:</strong>
   * If the write operation fails, the error is logged and the future completes with
   * {@code false}. Callers should check the returned boolean to verify success.
   * </p>
   *
   * @param date           session date in {@code yyyy-MM-dd} format
   * @param id             unique session identifier
   * @param userId         user identifier associated with the session
   * @param memberUsername username of the member in the session
   * @return a {@link CompletableFuture} that completes with {@code true} if the save was successful,
   * {@code false} otherwise
   */
  public CompletableFuture<Boolean> saveSessionAsync(
    String date, String id, String userId, String memberUsername) {

    Map<String, AttributeValue> item = new HashMap<>();
    item.put("date", AttributeValue.builder().s(date).build());
    item.put("id", AttributeValue.builder().n(id).build());
    item.put("user_id", AttributeValue.builder().n(userId).build());
    item.put("member_username", AttributeValue.builder().s(memberUsername).build());

    PutItemRequest request = PutItemRequest.builder().tableName(configData.tableNameSessionProviderMember()).item(item).build();

    return dynamoDbAsyncClient.putItem(request).thenApply(response -> {
      logger.info("Session saved asynchronously: id={}, userId={}, memberUsername={},  httpStatus={}", id, userId, memberUsername, response.sdkHttpResponse().statusCode());
      return response.sdkHttpResponse().isSuccessful();
    }).exceptionally(throwable -> {
      logger.error("Failed to save session asynchronously: id={}, userId={}, memberUsername={}", id, userId, memberUsername, throwable);
      return false;
    });
  }

}
