package com.julian.razif.figaro.bigdata.consumer.service;

import com.julian.razif.figaro.bigdata.appconfig.DynamonDBConfigData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DynamoDBService}.
 * <p>
 * Tests cover:
 * <ul>
 * <li>Successful session persistence</li>
 * <li>Input validation (null, empty, invalid format)</li>
 * <li>Error handling and recovery</li>
 * <li>DynamoDB client interaction</li>
 * </ul>
 * </p>
 *
 * @author Julian Razif Figaro
 * @version 1.0
 * @since 1.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DynamoDB Service Tests")
class DynamoDBServiceTest {

  @Mock
  private DynamoDbAsyncClient mockDynamoDbAsyncClient;

  @Mock
  private DynamonDBConfigData mockConfigData;

  private DynamoDBService dynamoDBService;

  @BeforeEach
  void setUp() {
    when(mockConfigData.tableNameSessionProviderMember()).thenReturn("TestTable");
    dynamoDBService = new DynamoDBService(mockDynamoDbAsyncClient, mockConfigData);
  }

  @Test
  @DisplayName("Should save session successfully with valid data")
  void saveSessionAsync_withValidData_shouldSucceed() {
    // Arrange
    String date = "2026-01-08";
    String id = "12345";
    String userId = "67890";
    String memberUsername = "testuser";

    PutItemResponse successResponse = (PutItemResponse) PutItemResponse.builder().sdkHttpResponse(SdkHttpResponse.builder().statusCode(200).build()).build();

    when(mockDynamoDbAsyncClient.putItem(any(PutItemRequest.class))).thenReturn(CompletableFuture.completedFuture(successResponse));

    // Act
    CompletableFuture<Boolean> result = dynamoDBService.saveSessionAsync(date, id, userId, memberUsername);

    // Assert
    assertTrue(result.join(), "Save should succeed");
    verify(mockDynamoDbAsyncClient, times(1)).putItem(any(PutItemRequest.class));
  }

  @Test
  @DisplayName("Should validate and build correct PutItemRequest")
  void saveSessionAsync_shouldBuildCorrectRequest() {
    // Arrange
    String date = "2026-01-08";
    String id = "12345";
    String userId = "67890";
    String memberUsername = "testuser";

    ArgumentCaptor<PutItemRequest> requestCaptor = ArgumentCaptor.forClass(PutItemRequest.class);

    PutItemResponse successResponse = (PutItemResponse) PutItemResponse.builder().sdkHttpResponse(SdkHttpResponse.builder().statusCode(200).build()).build();

    when(mockDynamoDbAsyncClient.putItem(requestCaptor.capture())).thenReturn(CompletableFuture.completedFuture(successResponse));

    // Act
    dynamoDBService.saveSessionAsync(date, id, userId, memberUsername).join();

    // Assert
    PutItemRequest capturedRequest = requestCaptor.getValue();
    assertEquals("TestTable", capturedRequest.tableName());

    Map<String, AttributeValue> item = capturedRequest.item();
    assertEquals(date, item.get("date").s());
    assertEquals(id, item.get("id").n());
    assertEquals(userId, item.get("user_id").n());
    assertEquals(memberUsername, item.get("member_username").s());
  }

  @Test
  @DisplayName("Should throw IllegalArgumentException when date is null")
  void saveSessionAsync_withNullDate_shouldThrowException() {
    // Act & Assert
    assertThrows(IllegalArgumentException.class, () -> dynamoDBService.saveSessionAsync(null, "123", "456", "user")
    );
    verify(mockDynamoDbAsyncClient, never()).putItem(any(PutItemRequest.class));
  }

  @Test
  @DisplayName("Should throw IllegalArgumentException when date is empty")
  void saveSessionAsync_withEmptyDate_shouldThrowException() {
    // Act & Assert
    assertThrows(IllegalArgumentException.class, () -> dynamoDBService.saveSessionAsync("", "123", "456", "user")
    );
    verify(mockDynamoDbAsyncClient, never()).putItem(any(PutItemRequest.class));
  }

  @Test
  @DisplayName("Should throw IllegalArgumentException when date format is invalid")
  void saveSessionAsync_withInvalidDateFormat_shouldThrowException() {
    // Act & Assert
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> dynamoDBService.saveSessionAsync("2026/01/08", "123", "456", "user")
    );
    assertTrue(exception.getMessage().contains("yyyy-MM-dd"));
    verify(mockDynamoDbAsyncClient, never()).putItem(any(PutItemRequest.class));
  }

  @Test
  @DisplayName("Should throw IllegalArgumentException when id is null")
  void saveSessionAsync_withNullId_shouldThrowException() {
    // Act & Assert
    assertThrows(IllegalArgumentException.class, () -> dynamoDBService.saveSessionAsync("2026-01-08", null, "456", "user")
    );
    verify(mockDynamoDbAsyncClient, never()).putItem(any(PutItemRequest.class));
  }

  @Test
  @DisplayName("Should throw IllegalArgumentException when id is not numeric")
  void saveSessionAsync_withNonNumericId_shouldThrowException() {
    // Act & Assert
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> dynamoDBService.saveSessionAsync("2026-01-08", "abc123", "456", "user")
    );
    assertTrue(exception.getMessage().contains("digits"));
    verify(mockDynamoDbAsyncClient, never()).putItem(any(PutItemRequest.class));
  }

  @Test
  @DisplayName("Should throw IllegalArgumentException when id is negative")
  void saveSessionAsync_withNegativeId_shouldThrowException() {
    // Act & Assert
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> dynamoDBService.saveSessionAsync("2026-01-08", "-123", "456", "user")
    );
    assertTrue(exception.getMessage().contains("digits"));
    verify(mockDynamoDbAsyncClient, never()).putItem(any(PutItemRequest.class));
  }

  @Test
  @DisplayName("Should throw IllegalArgumentException when userId is null")
  void saveSessionAsync_withNullUserId_shouldThrowException() {
    // Act & Assert
    assertThrows(IllegalArgumentException.class, () -> dynamoDBService.saveSessionAsync("2026-01-08", "123", null, "user")
    );
    verify(mockDynamoDbAsyncClient, never()).putItem(any(PutItemRequest.class));
  }

  @Test
  @DisplayName("Should throw IllegalArgumentException when userId is not numeric")
  void saveSessionAsync_withNonNumericUserId_shouldThrowException() {
    // Act & Assert
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> dynamoDBService.saveSessionAsync("2026-01-08", "123", "user456", "user")
    );
    assertTrue(exception.getMessage().contains("digits"));
    verify(mockDynamoDbAsyncClient, never()).putItem(any(PutItemRequest.class));
  }

  @Test
  @DisplayName("Should throw IllegalArgumentException when memberUsername is null")
  void saveSessionAsync_withNullMemberUsername_shouldThrowException() {
    // Act & Assert
    assertThrows(IllegalArgumentException.class, () -> dynamoDBService.saveSessionAsync("2026-01-08", "123", "456", null)
    );
    verify(mockDynamoDbAsyncClient, never()).putItem(any(PutItemRequest.class));
  }

  @Test
  @DisplayName("Should throw IllegalArgumentException when memberUsername exceeds max length")
  void saveSessionAsync_withTooLongMemberUsername_shouldThrowException() {
    // Arrange
    String longUsername = "a".repeat(256);

    // Act & Assert
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> dynamoDBService.saveSessionAsync("2026-01-08", "123", "456", longUsername)
    );
    assertTrue(exception.getMessage().contains("255"));
    verify(mockDynamoDbAsyncClient, never()).putItem(any(PutItemRequest.class));
  }

  @Test
  @DisplayName("Should handle DynamoDB exception gracefully")
  void saveSessionAsync_withDynamoDbException_shouldReturnFalse() {
    // Arrange
    RuntimeException exception = new RuntimeException("Table not found");

    when(mockDynamoDbAsyncClient.putItem(any(PutItemRequest.class))).thenReturn(CompletableFuture.failedFuture(exception));

    // Act
    CompletableFuture<Boolean> result = dynamoDBService.saveSessionAsync(
      "2026-01-08", "123", "456", "user"
    );

    // Assert
    assertFalse(result.join(), "Should return false on DynamoDB error");
    verify(mockDynamoDbAsyncClient, times(1)).putItem(any(PutItemRequest.class));
  }

  @Test
  @DisplayName("Should return false when response is not successful")
  void saveSessionAsync_withNonSuccessResponse_shouldReturnFalse() {
    // Arrange
    PutItemResponse failureResponse = (PutItemResponse) PutItemResponse.builder().sdkHttpResponse(SdkHttpResponse.builder().statusCode(500).build()).build();

    when(mockDynamoDbAsyncClient.putItem(any(PutItemRequest.class))).thenReturn(CompletableFuture.completedFuture(failureResponse));

    // Act
    CompletableFuture<Boolean> result = dynamoDBService.saveSessionAsync(
      "2026-01-08", "123", "456", "user"
    );

    // Assert
    assertFalse(result.join(), "Should return false for non-2xx status");
    verify(mockDynamoDbAsyncClient, times(1)).putItem(any(PutItemRequest.class));
  }

  @Test
  @DisplayName("Should accept valid memberUsername at max length")
  void saveSessionAsync_withMaxLengthUsername_shouldSucceed() {
    // Arrange
    String maxLengthUsername = "a".repeat(255);

    PutItemResponse successResponse = (PutItemResponse) PutItemResponse.builder().sdkHttpResponse(SdkHttpResponse.builder().statusCode(200).build()).build();

    when(mockDynamoDbAsyncClient.putItem(any(PutItemRequest.class))).thenReturn(CompletableFuture.completedFuture(successResponse));

    // Act
    CompletableFuture<Boolean> result = dynamoDBService.saveSessionAsync(
      "2026-01-08", "123", "456", maxLengthUsername
    );

    // Assert
    assertTrue(result.join(), "Should succeed with max length username");
    verify(mockDynamoDbAsyncClient, times(1)).putItem(any(PutItemRequest.class));
  }
}
