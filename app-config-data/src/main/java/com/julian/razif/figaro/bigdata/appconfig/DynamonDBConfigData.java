package com.julian.razif.figaro.bigdata.appconfig;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "dynamo-config-data")
public record DynamonDBConfigData (
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
