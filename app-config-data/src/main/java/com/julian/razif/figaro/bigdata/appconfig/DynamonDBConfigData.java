package com.julian.razif.figaro.bigdata.appconfig;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration properties for Amazon DynamoDB connection and client settings.
 * <p>
 * This record encapsulates all necessary configuration parameters for establishing
 * and managing DynamoDB connections, including endpoint configuration, AWS credentials,
 * table names, and various client timeout and connection pool settings.
 * </p>
 * <p>
 * Properties are bound from the configuration file using the prefix {@code dynamo-config-data}.
 * Default values are provided for most parameters to ensure the application can run
 * with minimal configuration.
 * </p>
 * <p>
 * <strong>Security Note:</strong> AWS credentials should be provided via environment variables
 * or AWS credentials provider chain in production environments, not hardcoded in configuration files.
 * </p>
 *
 * @param dynamodbEndpoint                   the DynamoDB service endpoint URL
 * @param awsRegion                          AWS region identifier where DynamoDB tables are located
 * @param awsAccesskey                       AWS access key ID for authentication (should be externalized)
 * @param awsSecretkey                       AWS secret access key for authentication (should be externalized)
 * @param tableNameSessionProviderMember     name of the DynamoDB table for provider member sessions
 * @param tableNameSessionUser               name of the DynamoDB table for user sessions
 * @param tableNamePersistenceProviderMember name of the DynamoDB table for provider member persistence data
 * @param tableNamePersistenceUser           name of the DynamoDB table for user persistence data
 * @param actSessionUserPv                   action identifier for session user provider
 * @param actSessionUser                     action identifier for session user
 * @param connectionTTL                      time-to-live in milliseconds for connections in the connection pool
 * @param connectionTimeout                  timeout in milliseconds for establishing a connection
 * @param clientExecutionTimeout             timeout in milliseconds for the entire client execution
 * @param requestTimeout                     timeout in milliseconds for individual requests
 * @param socketTimeout                      timeout in milliseconds for socket read operations
 * @param maxRetry                           maximum number of retry attempts for failed requests
 * @param throttledRetries                   whether to enable automatic retries for throttled requests
 * @param connectionMaxIdleMillis            maximum idle time in milliseconds before a connection is closed
 * @param maxConnections                     maximum number of concurrent connections in the connection pool
 * @param tcpKeepAlive                       whether to enable TCP keep-alive for connections
 * @author Julian Razif Figaro
 * @version 1.0
 * @since 1.0
 */
@ConfigurationProperties(prefix = "dynamo-config-data")
public record DynamonDBConfigData(
  @DefaultValue("dynamodb.ap-southeast-1.amazonaws.com") String dynamodbEndpoint,
  @DefaultValue("ap-southeast-1") String awsRegion,
  @DefaultValue("") String awsAccesskey,
  @DefaultValue("") String awsSecretkey,
  @DefaultValue("BigDataSession_ProviderMember") String tableNameSessionProviderMember,
  @DefaultValue("BigDataSession_User") String tableNameSessionUser,
  @DefaultValue("BigDataPersistence_ProviderMember") String tableNamePersistenceProviderMember,
  @DefaultValue("BigDataPersistence_User") String tableNamePersistenceUser,
  @DefaultValue("bigDataSesUsPvMember") String actSessionUserPv,
  @DefaultValue("bigDataSesUser") String actSessionUser,
  @DefaultValue("20000") Integer connectionTTL,
  @DefaultValue("4000") Integer connectionTimeout,
  @DefaultValue("10000") Integer clientExecutionTimeout,
  @DefaultValue("2000") Integer requestTimeout,
  @DefaultValue("1800") Integer socketTimeout,
  @DefaultValue("100") Integer maxRetry,
  @DefaultValue("true") Boolean throttledRetries,
  @DefaultValue("1000") Integer connectionMaxIdleMillis,
  @DefaultValue("2000") Integer maxConnections,
  @DefaultValue("false") Boolean tcpKeepAlive) {
}
