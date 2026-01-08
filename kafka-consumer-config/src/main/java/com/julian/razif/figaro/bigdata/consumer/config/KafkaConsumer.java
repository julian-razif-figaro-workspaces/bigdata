package com.julian.razif.figaro.bigdata.consumer.config;

import java.io.Serializable;
import java.util.List;

/**
 * Interface for Kafka message consumers.
 * <p>
 * Implementations of this interface are responsible for processing batches of messages
 * received from Kafka topics. The generic type {@code V} must be serializable to ensure
 * proper message deserialization from Kafka.
 * </p>
 *
 * @param <V> the type of message values to consume, must extend {@link Serializable}
 * @author Julian Razif Figaro
 * @version 1.0
 * @since 1.0
 */
public interface KafkaConsumer<V extends Serializable> {

  /**
   * Receives and processes a batch of messages from Kafka.
   * <p>
   * This method is typically invoked by a Kafka listener container when messages are
   * available for consumption. Implementations should handle message processing,
   * error handling, and acknowledgment as appropriate.
   * </p>
   *
   * @param messages list of messages received from Kafka, never {@code null}
   */
  void receive(List<V> messages);

}
