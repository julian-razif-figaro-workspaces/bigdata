package com.julian.razif.figaro.bigdata.consumer.config;

import java.io.Serializable;
import java.util.List;

public interface KafkaConsumer<V extends Serializable> {

  void receive(List<V> messages);

}
