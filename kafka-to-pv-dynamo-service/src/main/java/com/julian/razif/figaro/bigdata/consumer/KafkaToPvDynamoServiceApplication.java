package com.julian.razif.figaro.bigdata.consumer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan({"com.julian.razif.figaro.bigdata.appconfig"})
public class KafkaToPvDynamoServiceApplication {

  private KafkaToPvDynamoServiceApplication() {
  }

  public static void main(String[] args) {
    SpringApplication.run(KafkaToPvDynamoServiceApplication.class, args);
  }

}
