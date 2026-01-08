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

@Service
public class DynamoDBService {

  private static final Logger logger = LoggerFactory.getLogger(DynamoDBService.class);

  private final DynamoDbAsyncClient dynamoDbAsyncClient;
  private final DynamonDBConfigData configData;

  public DynamoDBService(
    DynamoDbAsyncClient dynamoDbAsyncClient,
    DynamonDBConfigData configData) {

    this.dynamoDbAsyncClient = dynamoDbAsyncClient;
    this.configData = configData;
  }

  public CompletableFuture<Boolean> saveSessionAsync(
    String date,
    String id,
    String userId,
    String memberUsername) {

    Map<String, AttributeValue> item = new HashMap<>();
    item.put("date", AttributeValue.builder().s(date).build());
    item.put("id", AttributeValue.builder().n(id).build());
    item.put("user_id", AttributeValue.builder().n(userId).build());
    item.put("member_username", AttributeValue.builder().s(memberUsername).build());

    PutItemRequest request = PutItemRequest
      .builder()
      .tableName(configData.tableNameSessionProviderMember())
      .item(item)
      .build();

    return dynamoDbAsyncClient
      .putItem(request)
      .thenApply(response -> {
        logger.info("Session saved asynchronously: id={}, userId={}, memberUsername={},  httpStatus={}", id, userId, memberUsername, response.sdkHttpResponse().statusCode());
        return response.sdkHttpResponse().isSuccessful();
      })
      .exceptionally(throwable -> {
        logger.error("Failed to save session asynchronously: id={}, userId={}, memberUsername={}", id, userId, memberUsername, throwable);
        return false;
      });
  }

}
