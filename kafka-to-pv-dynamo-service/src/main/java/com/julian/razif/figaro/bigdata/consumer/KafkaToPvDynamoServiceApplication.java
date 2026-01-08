package com.julian.razif.figaro.bigdata.consumer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Main application class for the Kafka to DynamoDB service.
 * <p>
 * This service consumes messages from Kafka topics and persists them to DynamoDB tables.
 * Configuration properties are automatically scanned from the {@code com.julian.razif.figaro.bigdata.appconfig}
 * package, and DynamoDB configuration is loaded from the {@code com.julian.razif.figaro.bigdata.dynamodb.config}
 * package.
 * </p>
 *
 * @author Julian Razif Figaro
 * @version 1.0
 * @since 1.0
 */
@SpringBootApplication(scanBasePackages = {
  "com.julian.razif.figaro.bigdata.consumer",
  "com.julian.razif.figaro.bigdata.dynamodb.config",
})
@ConfigurationPropertiesScan({"com.julian.razif.figaro.bigdata.appconfig"})
public class KafkaToPvDynamoServiceApplication {

  /**
   * Private constructor to prevent instantiation of the utility class.
   */
  private KafkaToPvDynamoServiceApplication() {
  }

  /**
   * Main entry point for the application.
   * <p>
   * Initializes the Spring Boot application context and starts the service.
   * </p>
   *
   * @param args command line arguments passed to the application
   */
  static void main(String[] args) {
    SpringApplication.run(KafkaToPvDynamoServiceApplication.class, args);
  }

}
