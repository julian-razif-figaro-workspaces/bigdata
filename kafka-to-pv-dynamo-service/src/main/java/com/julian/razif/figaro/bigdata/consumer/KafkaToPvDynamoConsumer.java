package com.julian.razif.figaro.bigdata.consumer;

import com.google.gson.*;
import com.julian.razif.figaro.bigdata.consumer.config.KafkaConsumer;
import com.julian.razif.figaro.bigdata.consumer.service.DynamoDBService;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Kafka consumer for processing session data and persisting to DynamoDB.
 * <p>
 * This consumer listens to Kafka topics containing session events, filters relevant messages,
 * and asynchronously persists them to DynamoDB tables. It uses reactive streams with Project
 * Reactor for efficient parallel processing.
 * </p>
 * <p>
 * <strong>Message Processing Flow:</strong>
 * <ol>
 * <li>Receives batch of messages from Kafka</li>
 * <li>Filters messages in parallel using {@link CompletableFuture}</li>
 * <li>Processes valid messages reactively using {@link Flux}</li>
 * <li>Persists session data to DynamoDB asynchronously</li>
 * </ol>
 * </p>
 * <p>
 * <strong>Performance Optimizations:</strong>
 * <ul>
 * <li>Batch message processing for higher throughput</li>
 * <li>Parallel stream processing for CPU-bound filtering</li>
 * <li>Reactive streams for non-blocking I/O operations</li>
 * <li>Asynchronous DynamoDB writes</li>
 * </ul>
 * </p>
 *
 * @author Julian Razif Figaro
 * @version 1.0
 * @since 1.0
 */
@Component
public class KafkaToPvDynamoConsumer implements KafkaConsumer<String> {

  private static final Logger logger = LoggerFactory.getLogger(KafkaToPvDynamoConsumer.class);

  /**
   * Maximum allowed JSON message size in bytes (1MB).
   */
  private static final int MAX_JSON_SIZE = 1_048_576;

  /**
   * Maximum allowed JSON depth to prevent deeply nested attacks.
   */
  private static final int MAX_JSON_DEPTH = 10;

  /**
   * Action type constant for big data session provider member.
   */
  private static final String ACTION_BIG_DATA_SESSION = "bigDataSesUsPvMember";

  /**
   * Required JSON field names.
   */
  private static final String FIELD_ACT = "act";
  private static final String FIELD_DATA = "data";
  private static final String FIELD_ID = "id";
  private static final String FIELD_USER_ID = "user_id";
  private static final String FIELD_MEMBER_USERNAME = "member_username";
  private static final String FIELD_DATE = "date";

  /**
   * Thread-safe date formatter for date formatting.
   */
  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault());

  /**
   * Gson instance configured with date format for JSON parsing.
   */
  public static final Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd HH:mm:ss").create();

  private final DynamoDBService dynamoDBService;

  /**
   * Constructs a new Kafka to DynamoDB consumer.
   *
   * @param dynamoDBService service for persisting data to DynamoDB
   */
  public KafkaToPvDynamoConsumer(
    DynamoDBService dynamoDBService) {

    this.dynamoDBService = dynamoDBService;
  }

  /**
   * Receives and processes messages from a Kafka topic.
   * <p>
   * This method is invoked by the Kafka listener container when messages are available.
   * Messages are processed in parallel using reactive streams and persisted to DynamoDB
   * asynchronously for optimal performance.
   * </p>
   * <p>
   * <strong>Processing Steps:</strong>
   * <ol>
   * <li>Filter messages asynchronously in parallel streams</li>
   * <li>Extract session data from valid JSON messages</li>
   * <li>Format date and session attributes</li>
   * <li>Save to DynamoDB asynchronously</li>
   * </ol>
   * </p>
   * <p>
   * Only messages with {@code act} field set to {@code "bigDataSesUsPvMember"} are processed.
   * Invalid or incomplete messages are logged and skipped.
   * </p>
   *
   * @param messages list of JSON message strings received from Kafka
   */
  @KafkaListener(
    id = "kafkaListenerContainerFactoryBigDataSession",
    containerFactory = "kafkaListenerContainerFactoryBigDataSession",
    topics = {"${kafka-consumer-config.redis-temp-data-topic}"},
    clientIdPrefix = "${kafka-consumer-config.client-id}" + "-BigDataProviderMember",
    groupId = "${kafka-consumer-config.group-id}" + "-BigDataProviderMember"
  )
  @Override
  public void receive(
    @Payload List<String> messages) {

    AtomicInteger processedCount = new AtomicInteger(0);
    AtomicInteger errorCount = new AtomicInteger(0);

    logger.info("Received batch of {} messages for processing", messages.size());

    Flux
      .fromIterable(messages)
      .flatMap(message -> Mono.fromFuture(filter(message)), 10) // Process 10 messages concurrently
      .filter(json -> !json.isJsonNull() && json.has(FIELD_DATA))
      .flatMap(json -> {
        try {
          JsonObject data = json.get(FIELD_DATA).getAsJsonObject();
          long dateMillis = data.get(FIELD_DATE).getAsLong();
          String dateString = DATE_FORMATTER.format(Instant.ofEpochMilli(dateMillis));
          String sessionId = data.get(FIELD_ID).getAsString();
          String userId = data.get(FIELD_USER_ID).getAsString();
          String memberUsername = data.get(FIELD_MEMBER_USERNAME).getAsString();

          logger.debug("Processing session: id={}, userId={}, username={}", sessionId, userId, memberUsername);

          return Mono
            .fromFuture(dynamoDBService.saveSessionAsync(dateString, sessionId, userId, memberUsername))
            .map(success -> {
              if (Boolean.TRUE.equals(success)) {
                processedCount.incrementAndGet();
              } else {
                errorCount.incrementAndGet();
                logger.warn("Failed to save session: id={}", sessionId);
              }
              return success;
            });
        } catch (Exception e) {
          errorCount.incrementAndGet();
          logger.error("Error extracting session data from JSON", e);
          return Mono.just(false);
        }
      })
      .doOnError(error -> {
        errorCount.incrementAndGet();
        logger.error("Error processing message batch", error);
      })
      .doOnComplete(() -> logger.info("Batch processing completed: processed={}, errors={}, total={}", processedCount.get(), errorCount.get(), messages.size()))
      .subscribeOn(Schedulers.boundedElastic())
      .subscribe();
  }

  /**
   * Filters and validates Kafka messages asynchronously.
   * <p>
   * This method parses the JSON message and validates that it contains the expected structure
   * and fields. Only messages with action type {@code "bigDataSesUsPvMember"} and complete
   * session data are accepted.
   * </p>
   * <p>
   * <strong>Validation Criteria:</strong>
   * <ul>
   * <li>Message must be valid JSON</li>
   * <li>{@code act} field must equal {@code "bigDataSesUsPvMember"}</li>
   * <li>{@code data} object must contain {@code id}, {@code user_id}, and {@code member_username}</li>
   * </ul>
   * </p>
   * <p>
   * A {@code date} field is added to the data object with the current date in
   * {@code yyyy-MM-dd} format before returning.
   * </p>
   *
   * @param message raw JSON message string from Kafka
   * @return a {@link CompletableFuture} containing the validated {@link JsonObject},
   * or an empty {@link JsonObject} if validation fails
   * @throws CompletionException if JSON parsing fails or the message structure is invalid
   */
  public CompletableFuture<JsonObject> filter(
    String message) {

    return CompletableFuture
      .supplyAsync(() -> {

        JsonObject x = getJsonObject(message);
        if (x != null) return x;

        JsonObject objectMessage;
        try {
          objectMessage = gson.fromJson(message, JsonElement.class).getAsJsonObject();

          // Validate JSON depth to prevent deeply nested attacks
          if (getJsonDepth(objectMessage) > MAX_JSON_DEPTH) {
            logger.warn("JSON depth exceeds maximum allowed depth {}, rejecting", MAX_JSON_DEPTH);
            return new JsonObject();
          }
        } catch (JsonSyntaxException ex) {
          logger.debug("Invalid JSON syntax, skipping message", ex);
          return new JsonObject();
        }

        if (!objectMessage.isJsonNull()
          && objectMessage.has(FIELD_ACT)
          && Objects.equals(objectMessage.get(FIELD_ACT).getAsString(), ACTION_BIG_DATA_SESSION)
          && objectMessage.has(FIELD_DATA)
          && !objectMessage.get(FIELD_DATA).isJsonNull()) {

          JsonObject data = objectMessage.get(FIELD_DATA).getAsJsonObject();

          logger.debug("Received valid message from Kafka: action={}", ACTION_BIG_DATA_SESSION);

          if (data.isJsonNull()
            || !data.has(FIELD_ID)
            || !data.has(FIELD_USER_ID)
            || !data.has(FIELD_MEMBER_USERNAME)) {

            logger.debug("Message missing required fields, skipping");
            return new JsonObject();
          }

          // Use thread-safe DateTimeFormatter instead of SimpleDateFormat
          long currentDateMilliseconds = System.currentTimeMillis();
          data.addProperty(FIELD_DATE, currentDateMilliseconds);

          logger.debug("Filtered and validated message successfully");

          return objectMessage;
        } else {
          return new JsonObject();
        }
      });
  }

  @Nullable
  private static JsonObject getJsonObject(
    String message) {

    // Validate message size to prevent memory exhaustion attacks
    if (message == null || message.isEmpty()) {
      logger.debug("Received null or empty message, skipping");
      return new JsonObject();
    }

    if (message.length() > MAX_JSON_SIZE) {
      logger.warn("Message size {} exceeds maximum allowed size {}, rejecting", message.length(), MAX_JSON_SIZE);
      return new JsonObject();
    }

    return null;
  }

  /**
   * Calculates the depth of a JSON object to prevent deeply nested attacks.
   * <p>
   * This method recursively traverses the JSON structure and returns the maximum
   * depth found. Objects and arrays contribute to depth, while primitive values do not.
   * </p>
   *
   * @param element the JSON element to measure
   * @return the maximum depth of the JSON structure
   */
  private int getJsonDepth(
    JsonElement element) {

    if (element == null || element.isJsonNull() || element.isJsonPrimitive()) {
      return 1;
    }

    if (element.isJsonArray()) {
      int maxDepth = 1;
      for (JsonElement child : element.getAsJsonArray()) {
        maxDepth = Math.max(maxDepth, getJsonDepth(child));
      }
      return 1 + maxDepth;
    }

    if (element.isJsonObject()) {
      int maxDepth = 1;
      for (var entry : element.getAsJsonObject().entrySet()) {
        maxDepth = Math.max(maxDepth, getJsonDepth(entry.getValue()));
      }
      return 1 + maxDepth;
    }

    return 1;
  }

}
