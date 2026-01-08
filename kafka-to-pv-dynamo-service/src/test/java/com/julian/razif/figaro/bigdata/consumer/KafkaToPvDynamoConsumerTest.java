package com.julian.razif.figaro.bigdata.consumer;

import com.google.gson.JsonObject;
import com.julian.razif.figaro.bigdata.consumer.service.DynamoDBService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link KafkaToPvDynamoConsumer}.
 * <p>
 * Tests cover:
 * <ul>
 * <li>Message filtering and validation</li>
 * <li>JSON parsing and structure validation</li>
 * <li>Size and depth limit enforcement</li>
 * <li>Message processing flow</li>
 * </ul>
 * </p>
 *
 * @author Julian Razif Figaro
 * @version 1.0
 * @since 1.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Kafka to DynamoDB Consumer Tests")
class KafkaToPvDynamoConsumerTest {

  @Mock
  private DynamoDBService mockDynamoDBService;

  private KafkaToPvDynamoConsumer consumer;

  @BeforeEach
  void setUp() {
    consumer = new KafkaToPvDynamoConsumer(mockDynamoDBService);
    when(mockDynamoDBService.saveSessionAsync(anyString(), anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(true));
  }

  @Test
  @DisplayName("Should filter valid message successfully")
  void filter_withValidMessage_shouldReturnJsonObject() {
    // Arrange
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

    // Act
    CompletableFuture<JsonObject> result = consumer.filter(validMessage);

    // Assert
    assertNotNull(result);
    JsonObject jsonObject = result.join();
    assertFalse(jsonObject.isJsonNull());
    assertTrue(jsonObject.has("act"));
    assertEquals("bigDataSesUsPvMember", jsonObject.get("act").getAsString());
  }

  @Test
  @DisplayName("Should reject message with wrong action type")
  void filter_withWrongActionType_shouldReturnEmptyObject() {
    // Arrange
    String invalidActionMessage = """
      {
        "act": "wrongAction",
        "data": {
          "id": "12345",
          "user_id": "67890",
          "member_username": "testuser"
        }
      }
      """;

    // Act
    CompletableFuture<JsonObject> result = consumer.filter(invalidActionMessage);

    // Assert
    JsonObject jsonObject = result.join();
    assertTrue(jsonObject.entrySet().isEmpty());
  }

  @Test
  @DisplayName("Should reject message missing required field 'id'")
  void filter_withMissingIdField_shouldReturnEmptyObject() {
    // Arrange
    String missingIdMessage = """
      {
        "act": "bigDataSesUsPvMember",
        "data": {
          "user_id": "67890",
          "member_username": "testuser"
        }
      }
      """;

    // Act
    CompletableFuture<JsonObject> result = consumer.filter(missingIdMessage);

    // Assert
    JsonObject jsonObject = result.join();
    assertTrue(jsonObject.entrySet().isEmpty());
  }

  @Test
  @DisplayName("Should reject message missing required field 'user_id'")
  void filter_withMissingUserIdField_shouldReturnEmptyObject() {
    // Arrange
    String missingUserIdMessage = """
      {
        "act": "bigDataSesUsPvMember",
        "data": {
          "id": "12345",
          "member_username": "testuser"
        }
      }
      """;

    // Act
    CompletableFuture<JsonObject> result = consumer.filter(missingUserIdMessage);

    // Assert
    JsonObject jsonObject = result.join();
    assertTrue(jsonObject.entrySet().isEmpty());
  }

  @Test
  @DisplayName("Should reject message missing required field 'member_username'")
  void filter_withMissingUsernameField_shouldReturnEmptyObject() {
    // Arrange
    String missingUsernameMessage = """
      {
        "act": "bigDataSesUsPvMember",
        "data": {
          "id": "12345",
          "user_id": "67890"
        }
      }
      """;

    // Act
    CompletableFuture<JsonObject> result = consumer.filter(missingUsernameMessage);

    // Assert
    JsonObject jsonObject = result.join();
    assertTrue(jsonObject.entrySet().isEmpty());
  }

  @Test
  @DisplayName("Should reject null message")
  void filter_withNullMessage_shouldReturnEmptyObject() {
    // Act
    CompletableFuture<JsonObject> result = consumer.filter(null);

    // Assert
    JsonObject jsonObject = result.join();
    assertTrue(jsonObject.entrySet().isEmpty());
  }

  @Test
  @DisplayName("Should reject empty message")
  void filter_withEmptyMessage_shouldReturnEmptyObject() {
    // Act
    CompletableFuture<JsonObject> result = consumer.filter("");

    // Assert
    JsonObject jsonObject = result.join();
    assertTrue(jsonObject.entrySet().isEmpty());
  }

  @Test
  @DisplayName("Should reject message exceeding size limit")
  void filter_withOversizedMessage_shouldReturnEmptyObject() {
    // Arrange
    String largeData = "x".repeat(2_000_000); // 2MB, exceeds 1MB limit
    String oversizedMessage = String.format("""
      {
        "act": "bigDataSesUsPvMember",
        "data": {
          "id": "12345",
          "user_id": "67890",
          "member_username": "%s"
        }
      }
      """, largeData);

    // Act
    CompletableFuture<JsonObject> result = consumer.filter(oversizedMessage);

    // Assert
    JsonObject jsonObject = result.join();
    assertTrue(jsonObject.entrySet().isEmpty());
  }

  @Test
  @DisplayName("Should reject invalid JSON syntax")
  void filter_withInvalidJson_shouldReturnEmptyObject() {
    // Arrange
    String invalidJson = "{ invalid json syntax }";

    // Act
    CompletableFuture<JsonObject> result = consumer.filter(invalidJson);

    // Assert
    JsonObject jsonObject = result.join();
    assertTrue(jsonObject.entrySet().isEmpty());
  }

  @Test
  @DisplayName("Should reject deeply nested JSON")
  void filter_withDeeplyNestedJson_shouldReturnEmptyObject() {
    // Arrange - Create JSON with depth > 10
    String deeplyNested = """
      {
        "act": "bigDataSesUsPvMember",
        "data": {
          "id": "12345",
          "user_id": "67890",
          "member_username": "testuser",
          "level1": {
            "level2": {
              "level3": {
                "level4": {
                  "level5": {
                    "level6": {
                      "level7": {
                        "level8": {
                          "level9": {
                            "level10": {
                              "level11": "too deep"
                            }
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
      """;

    // Act
    CompletableFuture<JsonObject> result = consumer.filter(deeplyNested);

    // Assert
    JsonObject jsonObject = result.join();
    assertTrue(jsonObject.entrySet().isEmpty());
  }

  @Test
  @DisplayName("Should add date field to valid message")
  void filter_withValidMessage_shouldAddDateField() {
    // Arrange
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

    // Act
    CompletableFuture<JsonObject> result = consumer.filter(validMessage);

    // Assert
    JsonObject jsonObject = result.join();
    JsonObject data = jsonObject.get("data").getAsJsonObject();
    assertTrue(data.has("date"), "Date field should be added");
    data.get("date").getAsLong();
  }

  @Test
  @DisplayName("Should accept message with data exactly at depth limit")
  void filter_withJsonAtMaxDepth_shouldSucceed() {
    // Arrange - Create JSON with depth = 10 (at limit)
    String atMaxDepth = """
      {
        "act": "bigDataSesUsPvMember",
        "data": {
          "id": "12345",
          "user_id": "67890",
          "member_username": "testuser",
          "level1": {
            "level2": {
              "level3": {
                "level4": {
                  "level5": {
                    "level6": {
                      "level7": {
                        "level8": "ok"
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
      """;

    // Act
    CompletableFuture<JsonObject> result = consumer.filter(atMaxDepth);

    // Assert
    JsonObject jsonObject = result.join();
    assertFalse(jsonObject.entrySet().isEmpty(), "Should accept JSON at max depth");
  }

  @Test
  @DisplayName("Should handle message with null data field")
  void filter_withNullDataField_shouldReturnEmptyObject() {
    // Arrange
    String nullDataMessage = """
      {
        "act": "bigDataSesUsPvMember",
        "data": null
      }
      """;

    // Act
    CompletableFuture<JsonObject> result = consumer.filter(nullDataMessage);

    // Assert
    JsonObject jsonObject = result.join();
    assertTrue(jsonObject.entrySet().isEmpty());
  }

  @Test
  @DisplayName("Should handle message missing data field")
  void filter_withMissingDataField_shouldReturnEmptyObject() {
    // Arrange
    String missingDataMessage = """
      {
        "act": "bigDataSesUsPvMember"
      }
      """;

    // Act
    CompletableFuture<JsonObject> result = consumer.filter(missingDataMessage);

    // Assert
    JsonObject jsonObject = result.join();
    assertTrue(jsonObject.entrySet().isEmpty());
  }

  @Test
  @DisplayName("Should process batch of messages")
  void receive_withValidMessages_shouldProcessBatch() {
    // Arrange
    List<String> messages = List.of(
      """
        {
          "act": "bigDataSesUsPvMember",
          "data": {
            "id": "1",
            "user_id": "100",
            "member_username": "user1"
          }
        }
        """, """
        {
          "act": "bigDataSesUsPvMember",
          "data": {
            "id": "2",
            "user_id": "200",
            "member_username": "user2"
          }
        }
        """
    );

    // Act
    assertDoesNotThrow(() -> consumer.receive(messages));

    // Note: Due to async nature, we can't easily verify DynamoDB calls
    // In integration tests, we would verify the actual persistence
  }

  @Test
  @DisplayName("Should handle empty message batch gracefully")
  void receive_withEmptyBatch_shouldHandleGracefully() {
    // Arrange
    List<String> emptyBatch = List.of();

    // Act & Assert
    assertDoesNotThrow(() -> consumer.receive(emptyBatch));
  }
}
