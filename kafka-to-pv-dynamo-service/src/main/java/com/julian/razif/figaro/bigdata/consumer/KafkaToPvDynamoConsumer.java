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

@Component
public class KafkaToPvDynamoConsumer implements KafkaConsumer<String> {

  private static final Logger logger = LoggerFactory.getLogger(KafkaToPvDynamoConsumer.class);

  public static final Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd HH:mm:ss").create();

  private final DynamoDBService dynamoDBService;

  public KafkaToPvDynamoConsumer(
    DynamoDBService dynamoDBService) {

    this.dynamoDBService = dynamoDBService;
  }

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

    Flux
      .<JsonObject>create(sink -> CompletableFuture
        .supplyAsync(() -> messages
          .parallelStream()
          .map(message -> filter(message).join())
          .toList()
        )
        .whenCompleteAsync((dtos, throwable) -> {
          if (throwable != null) {
            sink.complete();
          } else {
            dtos.forEach(sink::next);
          }
        })
      )
      .subscribeOn(Schedulers.boundedElastic())
      .subscribe(json -> {
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

  public CompletableFuture<JsonObject> filter(String message) {
    return CompletableFuture
      .supplyAsync(() -> {
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
