package com.julian.razif.figaro.bigdata.consumer;

import com.google.gson.*;
import com.julian.razif.figaro.bigdata.consumer.config.KafkaConsumer;
import com.julian.razif.figaro.bigdata.consumer.service.DynamoDBService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

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

    Flux.<JsonObject>create(sink -> CompletableFuture.supplyAsync(() -> messages.parallelStream().map(message -> filter(message).join()).toList()
      ).whenCompleteAsync((dtos, throwable) -> {
        if (throwable != null) {
          sink.complete();
        } else {
          dtos.forEach(sink::next);
        }
      })
    ).subscribeOn(Schedulers.boundedElastic()).subscribe(json -> {
      Date date = new Date(json.get("data").getAsJsonObject().get("date").getAsLong());
      SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
      String dateString = sdf.format(date);
      String sessionId = json.get("data").getAsJsonObject().get("id").getAsString();
      String userId = json.get("data").getAsJsonObject().get("user_id").getAsString();
      String memberUsername = json.get("data").getAsJsonObject().get("member_username").getAsString();
      logger.info("date={}", dateString);
      logger.info("sessionId={}", sessionId);
      logger.info("userId={}", userId);
      logger.info("memberUsername={}", memberUsername);

      dynamoDBService.saveSessionAsync(dateString, sessionId, userId, memberUsername).join();
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
  public CompletableFuture<JsonObject> filter(String message) {
    return CompletableFuture.supplyAsync(() -> {
      JsonObject objectMessage;
      try {
        objectMessage = gson.fromJson(message, JsonElement.class).getAsJsonObject();
      } catch (JsonSyntaxException ex) {
        throw new CompletionException("not valid JSON", ex);
      }

      if (!objectMessage.isJsonNull()
        && objectMessage.has("act")
        && Objects.equals(objectMessage.get("act").getAsString(), "bigDataSesUsPvMember")
        && objectMessage.has("data")
        && !objectMessage.get("data").isJsonNull()) {

        JsonObject data = objectMessage.get("data").getAsJsonObject();

        logger.debug("from kafka={}", objectMessage);

        if (data.isJsonNull()
          || !data.has("id")
          || !data.has("user_id")
          || !data.has("member_username")) {

          throw new CompletionException(new JsonSyntaxException("not valid JSON"));
        }

        long currentDateMilliseconds = System.currentTimeMillis();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        Date currentDate = new Date(currentDateMilliseconds);

        data.addProperty("date", sdf.format(currentDate));

        logger.debug("jsonObject={}", data);

        return objectMessage;
      } else {
        return new JsonObject();
      }
    });
  }

}
