package com.julian.razif.figaro.bigdata.consumer;

import com.google.gson.*;
import com.julian.razif.figaro.bigdata.consumer.config.KafkaConsumer;
import com.julian.razif.figaro.bigdata.consumer.service.DynamoDBBatchService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.jspecify.annotations.NonNull;
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

  private static final String TAG_SERVICE = "service";
  private static final String TAG_ACTION = "kafka-to-pv-dynamo";

  /**
   * Thread-safe date formatter for date formatting.
   */
  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault());

  /**
   * Gson instance configured with date format for JSON parsing.
   */
  public static final Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd HH:mm:ss").create();

  private final DynamoDBBatchService dynamoDBBatchService;

  // Performance metrics
  private final Counter messagesReceivedCounter;
  private final Counter messagesProcessedCounter;
  private final Counter messagesFilteredCounter;
  private final Counter messagesErrorCounter;
  private final Timer messageProcessingTimer;
  private final Timer jsonFilteringTimer;
  private final Timer dynamoDbWriteTimer;

  /**
   * Constructs a new Kafka to DynamoDB consumer.
   *
   * @param dynamoDBBatchService batch service for persisting data to DynamoDB with improved performance
   * @param meterRegistry        Micrometer registry for metrics collection
   */
  public KafkaToPvDynamoConsumer(
    DynamoDBBatchService dynamoDBBatchService,
    MeterRegistry meterRegistry) {

    this.dynamoDBBatchService = dynamoDBBatchService;

    // Initialize performance metrics
    this.messagesReceivedCounter = Counter.builder("kafka.messages.received").description("Total number of messages received from Kafka").tag(TAG_SERVICE, TAG_ACTION).register(meterRegistry);

    this.messagesProcessedCounter = Counter.builder("kafka.messages.processed").description("Total number of messages successfully processed").tag(TAG_SERVICE, TAG_ACTION).register(meterRegistry);

    this.messagesFilteredCounter = Counter.builder("kafka.messages.filtered").description("Total number of messages filtered out").tag(TAG_SERVICE, TAG_ACTION).register(meterRegistry);

    this.messagesErrorCounter = Counter.builder("kafka.messages.error").description("Total number of messages with processing errors").tag(TAG_SERVICE, TAG_ACTION).register(meterRegistry);

    this.messageProcessingTimer = Timer.builder("kafka.message.processing.time").description("Time taken to process a message batch").tag(TAG_SERVICE, TAG_ACTION).register(meterRegistry);

    this.jsonFilteringTimer = Timer.builder("kafka.json.filtering.time").description("Time taken to filter and validate JSON").tag(TAG_SERVICE, TAG_ACTION).register(meterRegistry);

    this.dynamoDbWriteTimer = Timer.builder("dynamodb.write.time").description("Time taken to write to DynamoDB").tag(TAG_SERVICE, TAG_ACTION).register(meterRegistry);
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

    messagesReceivedCounter.increment(messages.size());

    long startTime = System.nanoTime();
    AtomicInteger processedCount = new AtomicInteger(0);
    AtomicInteger errorCount = new AtomicInteger(0);
    AtomicInteger filteredCount = new AtomicInteger(0);

    logger.info("Received batch of {} messages for processing", messages.size());

    Flux
      .fromIterable(messages)
      .onBackpressureBuffer(1000, _ -> logger.warn("Backpressure buffer overflow, dropping message"))
      .flatMap(message -> consumeMessage(message, errorCount),
        10,  // Concurrency: process 10 messages in parallel
        1    // Prefetch: fetch 1 at a time per parallel stream to reduce memory pressure
      )
      .filter(json -> isValidJson(json, filteredCount))
      .mapNotNull(json -> convertJsonToSessionData(json, errorCount))
      .collectList()  // Collect all valid SessionData objects
      .flatMap(sessionsList -> {
        if (sessionsList.isEmpty()) {
          logger.debug("No valid sessions to write to DynamoDB");
          return Mono.just(0);
        }

        logger.info("Writing {} sessions to DynamoDB in batch", sessionsList.size());

        Timer.Sample sample = Timer.start();
        return performBatchSessionWrite(sessionsList, sample, errorCount, processedCount);
      })
      .doOnError(error -> {
        messagesErrorCounter.increment();
        logger.error("Error processing message batch", error);
      })
      .doFinally(_ -> {
        long duration = System.nanoTime() - startTime;
        messageProcessingTimer.record(duration, java.util.concurrent.TimeUnit.NANOSECONDS);

        messagesProcessedCounter.increment(processedCount.get());
        messagesFilteredCounter.increment(filteredCount.get());
        messagesErrorCounter.increment(errorCount.get());

        logger.info(
          "Batch processing completed: processed={}, filtered={}, errors={}, total={}, duration={}ms", processedCount.get(), filteredCount.get(), errorCount.get(), messages.size(), duration / 1_000_000
        );
      })
      .subscribeOn(Schedulers.boundedElastic())
      .subscribe();
  }

  private @NonNull Mono<JsonObject> consumeMessage(
    String message,
    AtomicInteger errorCount) {

    return Mono
      .fromFuture(filter(message))
      .onErrorResume(error -> {
        errorCount.incrementAndGet();
        logger.error("Error filtering message", error);
        return Mono.just(new JsonObject());
      });
  }

  private static boolean isValidJson(
    JsonObject json,
    AtomicInteger filteredCount) {

    boolean isValid = !json.isJsonNull() && json.has(FIELD_DATA);
    if (!isValid) {
      filteredCount.incrementAndGet();
    }

    return isValid;
  }

  private static DynamoDBBatchService.@Nullable SessionData convertJsonToSessionData(
    JsonObject json,
    AtomicInteger errorCount) {

    try {
      JsonObject data = json.get(FIELD_DATA).getAsJsonObject();
      long dateMillis = data.get(FIELD_DATE).getAsLong();
      String dateString = DATE_FORMATTER.format(Instant.ofEpochMilli(dateMillis));
      String sessionId = data.get(FIELD_ID).getAsString();
      String userId = data.get(FIELD_USER_ID).getAsString();
      String memberUsername = data.get(FIELD_MEMBER_USERNAME).getAsString();

      logger.debug("Processing session: id={}, userId={}, username={}", sessionId, userId, memberUsername);

      // Create a SessionData record for batch processing
      return new DynamoDBBatchService.SessionData(dateString, sessionId, userId, memberUsername);
    } catch (Exception e) {
      errorCount.incrementAndGet();
      logger.error("Error extracting session data from JSON", e);
      return null;
    }
  }

  private @NonNull Mono<Integer> performBatchSessionWrite(
    List<DynamoDBBatchService.@Nullable SessionData> sessionsList,
    Timer.Sample sample,
    AtomicInteger errorCount,
    AtomicInteger processedCount) {

    return Mono
      .fromFuture(() -> dynamoDBBatchService
        .batchWriteSessionsAsync(sessionsList)
        .whenComplete((count, error) -> {
          sample.stop(dynamoDbWriteTimer);
          if (error != null) {
            logger.error("Batch DynamoDB write failed", error);
            errorCount.addAndGet(sessionsList.size());
          } else {
            processedCount.set(count);
            int failedCount = sessionsList.size() - count;
            if (failedCount > 0) {
              errorCount.addAndGet(failedCount);
              logger.warn("Batch write partially failed: {}/{} items written", count, sessionsList.size());
            } else {
              logger.info("Successfully wrote {} sessions to DynamoDB", count);
            }
          }
        })
      )
      .onErrorResume(error -> {
        errorCount.addAndGet(sessionsList.size());
        logger.error("Error during batch write operation", error);
        return Mono.just(0);
      });
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

    Timer.Sample sample = Timer.start();

    return CompletableFuture.supplyAsync(() -> {

      JsonObject x = getJsonObject(message);
      if (x != null) {
        sample.stop(jsonFilteringTimer);
        return x;
      }

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

      if (!objectMessage.isJsonNull() && objectMessage.has(FIELD_ACT) && Objects.equals(objectMessage.get(FIELD_ACT).getAsString(), ACTION_BIG_DATA_SESSION) && objectMessage.has(FIELD_DATA) && !objectMessage.get(FIELD_DATA).isJsonNull()) {

        JsonObject data = objectMessage.get(FIELD_DATA).getAsJsonObject();

        logger.debug("Received valid message from Kafka: action={}", ACTION_BIG_DATA_SESSION);

        if (data.isJsonNull() || !data.has(FIELD_ID) || !data.has(FIELD_USER_ID) || !data.has(FIELD_MEMBER_USERNAME)) {

          logger.debug("Message missing required fields, skipping");
          return new JsonObject();
        }

        // Use thread-safe DateTimeFormatter instead of SimpleDateFormat
        long currentDateMilliseconds = System.currentTimeMillis();
        data.addProperty(FIELD_DATE, currentDateMilliseconds);

        logger.debug("Filtered and validated message successfully");

        sample.stop(jsonFilteringTimer);
        return objectMessage;
      } else {
        sample.stop(jsonFilteringTimer);
        return new JsonObject();
      }
    });
  }

  private static @Nullable JsonObject getJsonObject(
    String message) {

    // Validate a message exists first (the fastest check)
    if (message == null || message.isEmpty()) {
      logger.debug("Received null or empty message, skipping");
      return new JsonObject();
    }

    // Validate message size BEFORE parsing to avoid expensive Gson parsing
    // This prevents memory exhaustion attacks and improves performance
    // by rejecting oversized messages immediately
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
