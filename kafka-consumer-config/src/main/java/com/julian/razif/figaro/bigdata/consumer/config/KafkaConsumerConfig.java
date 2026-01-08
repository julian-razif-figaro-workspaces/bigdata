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

@EnableKafka
@Configuration
public class KafkaConsumerConfig<K extends Serializable, V extends Serializable> {

  private final KafkaConsumerConfigData kafkaConfigData;

  public KafkaConsumerConfig(KafkaConsumerConfigData kafkaConfigData) {
    this.kafkaConfigData = kafkaConfigData;
  }

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

  @Bean
  public ConsumerFactory<K, V> consumerFactory() {
    return new DefaultKafkaConsumerFactory<>(kafkaConsumerConfigProperties());
  }

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
