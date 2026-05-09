package com.swiftship.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

@Configuration
public class KafkaConfig {

  @Value("${swiftship.kafka.topics.shipment-events}")
  private String shipmentEventsTopic;

  /**
   * Declares the Kafka topic.
   */
  @Bean
  public NewTopic shipmentEventsTopic() {
    return TopicBuilder.name(shipmentEventsTopic)
        .partitions(6)
        .replicas(3)
        .build();
  }

  /**
   * The single KafkaTemplate used by OutboxEventPublisher to send events.
   */
  @Bean
  public KafkaTemplate<String, Object> kafkaTemplate(
      ProducerFactory<String, Object> factory
  ) {
    return new KafkaTemplate<>(factory);
  }
}
