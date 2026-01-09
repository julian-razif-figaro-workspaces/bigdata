package com.julian.razif.figaro.bigdata.appconfig;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration properties for Kafka consumer settings.
 * <p>
 * This record encapsulates all necessary configuration parameters for establishing
 * and managing Kafka consumer connections. Properties are bound from the configuration
 * file using the prefix {@code kafka-consumer-config}.
 * </p>
 * <p>
 * Default values are provided for all parameters to ensure the application can run
 * with minimal configuration. Override these values in your application properties
 * or YAML files as needed.
 * </p>
 *
 * @param bootstrapServers                  comma-separated list of Kafka broker addresses
 * @param clientId                          unique identifier for this Kafka client
 * @param groupId                           consumer group identifier for coordinated consumption
 * @param keyDeserializer                   fully qualified class name of the key deserializer
 * @param valueDeserializer                 fully qualified class name of the value deserializer
 * @param enableAutoCommit                  whether to automatically commit offsets
 * @param autoOffsetReset                   strategy for resetting offsets when no initial offset exists (earliest,
 *                                          latest, none)
 * @param batchListener                     whether to enable batch message processing
 * @param autoStartup                       whether to automatically start the consumer on application startup
 * @param concurrencyLevel                  number of concurrent consumer threads
 * @param sessionTimeoutMs                  timeout in milliseconds for detecting consumer failures
 * @param heartbeatIntervalMs               expected time in milliseconds between heartbeats to the consumer coordinator
 * @param maxPollIntervalMs                 maximum delay in milliseconds between invocations of poll()
 * @param maxPollRecords                    maximum number of records returned in a single poll() call
 * @param maxPartitionFetchBytesDefault     maximum amount of data per partition the server will return
 * @param maxPartitionFetchBytesBoostFactor multiplier for dynamically adjusting fetch size
 * @param pollTimeoutMs                     timeout in milliseconds spent waiting in a poll if data is not available
 * @param fetchMinBytes                     minimum amount of data the server should return for a fetch request
 * @param fetchMaxWaitMs                    maximum time the server will block before answering the fetch request
 * @param connectionsMaxIdleMs              close idle connections after this many milliseconds
 * @param requestTimeoutMs                  maximum time a client will await a response of a request
 * @param compressionType                   compression codec for messages (none, gzip, snappy, lz4, zstd)
 * @param numOfPartitions                   number of partitions for topics created by this consumer
 * @param replicationFactor                 replication factor for topics created by this consumer
 * @param redisTempDataTopic                name of the Kafka topic for temporary Redis data
 * @author Julian Razif Figaro
 * @version 1.0
 * @since 1.0
 */
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
  @DefaultValue("1024") Integer fetchMinBytes,
  @DefaultValue("500") Integer fetchMaxWaitMs,
  @DefaultValue("540000") Integer connectionsMaxIdleMs,
  @DefaultValue("30000") Integer requestTimeoutMs,
  @DefaultValue("lz4") String compressionType,
  @DefaultValue("3") Integer numOfPartitions,
  @DefaultValue("3") Integer replicationFactor,
  @DefaultValue("RedisTempBigDataSession") String redisTempDataTopic) {
}
