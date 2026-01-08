package com.julian.razif.figaro.bigdata.consumer.config;

import com.julian.razif.figaro.bigdata.appconfig.KafkaConsumerConfigData;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.KafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Spring configuration class for Kafka consumer setup.
 * <p>
 * This configuration creates and configures Kafka consumer beans including consumer factory
 * and listener container factory. It uses {@link KafkaConsumerConfigData} for externalized
 * configuration properties.
 * </p>
 * <p>
 * The configuration supports:
 * <ul>
 * <li>Batch message processing for improved throughput</li>
 * <li>Concurrent consumer threads for parallel processing</li>
 * <li>Manual offset commit for reliability</li>
 * <li>Configurable timeouts and fetch parameters</li>
 * </ul>
 * </p>
 *
 * @param <K> the type of message keys, must extend {@link Serializable}
 * @param <V> the type of message values, must extend {@link Serializable}
 * @author Julian Razif Figaro
 * @version 1.0
 * @since 1.0
 */
@EnableKafka
@Configuration
public class KafkaConsumerConfig<K extends Serializable, V extends Serializable> {

  private final KafkaConsumerConfigData kafkaConfigData;

  /**
   * Constructs a new Kafka consumer configuration.
   *
   * @param kafkaConfigData configuration properties for Kafka consumer, must not be {@code null}
   */
  public KafkaConsumerConfig(KafkaConsumerConfigData kafkaConfigData) {
    this.kafkaConfigData = kafkaConfigData;
  }

  /**
   * Creates Kafka consumer configuration properties.
   * <p>
   * Configures essential Kafka consumer settings including:
   * <ul>
   * <li>Bootstrap servers for initial connection</li>
   * <li>Client and group identifiers for consumer coordination</li>
   * <li>Key and value deserializers for message parsing</li>
   * <li>Offset reset strategy and commit behavior</li>
   * <li>Timeout settings for session management</li>
   * <li>Fetch parameters for throughput optimization</li>
   * </ul>
   * </p>
   *
   * @return map of Kafka consumer configuration properties
   */
  @Bean
  public Map<String, Object> kafkaConsumerConfigProperties() {
    Map<String, Object> props = new HashMap<>();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaConfigData.bootstrapServers());
    props.put(ConsumerConfig.CLIENT_ID_CONFIG, kafkaConfigData.clientId());
    props.put(ConsumerConfig.GROUP_ID_CONFIG, kafkaConfigData.groupId());
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, kafkaConfigData.keyDeserializer());
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, kafkaConfigData.valueDeserializer());
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, kafkaConfigData.autoOffsetReset());
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, kafkaConfigData.enableAutoCommit());
    props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, kafkaConfigData.sessionTimeoutMs());
    props.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, kafkaConfigData.heartbeatIntervalMs());
    props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, kafkaConfigData.maxPollIntervalMs());
    props.put(ConsumerConfig.MAX_PARTITION_FETCH_BYTES_CONFIG, kafkaConfigData.maxPartitionFetchBytesDefault() * kafkaConfigData.maxPartitionFetchBytesBoostFactor());
    props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, kafkaConfigData.maxPollRecords());
    return props;
  }

  /**
   * Creates a Kafka consumer factory.
   * <p>
   * The consumer factory is responsible for creating Kafka consumer instances
   * using the configured properties. It supports generic key and value types.
   * </p>
   *
   * @return consumer factory for creating Kafka consumers
   */
  @Bean
  public ConsumerFactory<K, V> consumerFactory() {
    return new DefaultKafkaConsumerFactory<>(kafkaConsumerConfigProperties());
  }

  /**
   * Creates a Kafka listener container factory for concurrent message processing.
   * <p>
   * This factory configures:
   * <ul>
   * <li>Batch listener mode for processing multiple messages at once</li>
   * <li>Concurrent consumer threads for parallel processing</li>
   * <li>Automatic startup behavior</li>
   * <li>Batch acknowledgment mode for better performance</li>
   * <li>Poll timeout for message fetching</li>
   * </ul>
   * </p>
   * <p>
   * <strong>Performance Considerations:</strong>
   * The concurrency level should match or be less than the number of partitions
   * in the consumed topics for optimal throughput.
   * </p>
   *
   * @return configured Kafka listener container factory
   */
  @Bean
  public KafkaListenerContainerFactory<ConcurrentMessageListenerContainer<K, V>> kafkaListenerContainerFactoryBigDataSession() {
    ConcurrentKafkaListenerContainerFactory<K, V> factory = new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(consumerFactory());
    factory.setBatchListener(kafkaConfigData.batchListener());
    factory.setConcurrency(kafkaConfigData.concurrencyLevel());
    factory.setAutoStartup(kafkaConfigData.autoStartup());
    factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.BATCH);
    factory.getContainerProperties().setPollTimeout(kafkaConfigData.pollTimeoutMs());
    return factory;
  }

}
