package com.julian.razif.figaro.bigdata.appconfig;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "kafka-consumer-config")
public record KafkaConsumerConfigData(
  @DefaultValue("192.168.1.8:9092,192.168.1.9:9092,192.168.1.10:9092") String bootstrapServers,
  @DefaultValue("BO_JULIAN_DEV_PV_02") String clientId,
  @DefaultValue("BO_02_JULIAN_DEV_PV") String groupId,
  @DefaultValue("org.apache.kafka.common.serialization.StringDeserializer") String keyDeserializer,
  @DefaultValue("org.apache.kafka.common.serialization.StringDeserializer") String valueDeserializer,
  @DefaultValue("false") Boolean enableAutoCommit,
  @DefaultValue("latest") String autoOffsetReset,
  @DefaultValue("true") Boolean batchListener,
  @DefaultValue("true") Boolean autoStartup,
  @DefaultValue("3") Integer concurrencyLevel,
  @DefaultValue("10000") Integer sessionTimeoutMs,
  @DefaultValue("3000") Integer heartbeatIntervalMs,
  @DefaultValue("300000") Integer maxPollIntervalMs,
  @DefaultValue("500") Integer maxPollRecords,
  @DefaultValue("1048576") Integer maxPartitionFetchBytesDefault,
  @DefaultValue("1") Integer maxPartitionFetchBytesBoostFactor,
  @DefaultValue("150") Long pollTimeoutMs,
  @DefaultValue("3") Integer numOfPartitions,
  @DefaultValue("3") Integer replicationFactor,
  @DefaultValue("RedisTempBigDataSession") String redisTempDataTopic) {
}
