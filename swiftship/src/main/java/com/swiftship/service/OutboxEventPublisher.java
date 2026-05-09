package com.swiftship.service;

import com.swiftship.model.OutboxEvent;
import com.swiftship.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Scheduled publisher that completes the second half of the Transactional Outbox pattern.
 *
 * PHASE 1 (in ShipmentService, same DB transaction as the status update):
 * → shipment row is updated
 * → outbox_events row is inserted with published = false
 * → both commit atomically — the event is now durable in the DB
 *
 * PHASE 2 (this class, runs on a fixed schedule every 5 seconds):
 * → Fetch all outbox rows where published = false
 * → For each row, send the payload to Kafka
 * → On success, mark the row published = true and stamp publishedAt
 * → On failure, log the error and leave the row as published = false
 * so the next schedule run will retry it automatically
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxEventPublisher {
  @Value("${swiftship.kafka.topics.shipment-events}")
  private String shipmentEventsTopic;

  private final KafkaTemplate<String, Object> kafkaTemplate;
  private final OutboxEventRepository outboxEventRepository;
  /**
   * Polls the outbox table every 5 seconds for unpublished events and
   * forwards them to Kafka.
   */
  @Transactional
  @Scheduled(fixedDelay = 5000)
  public void publishPendingEvents() {
    // Fetch unpublished events oldest-first to maintain ordering
    List<OutboxEvent> pending = outboxEventRepository.findByPublishedFalseOrderByCreatedAtAsc();

    if (pending.isEmpty()) {
      return; // nothing to do — skip logging to avoid noise
    }

    log.info("OutboxEventPublisher: found {} pending event(s) to publish", pending.size());

    for (OutboxEvent outboxEvent : pending) {
      publishSingleEvent(outboxEvent);
    }
  }

  private void publishSingleEvent(OutboxEvent outboxEvent) {
    // Use the aggregateId (shipmentId) as the Kafka partition key.
    // This guarantees that all events for the same shipment always
    // land on the same partition, preserving their order.
    String partitionKey = outboxEvent.getAggregateId().toString();

    try {
      // Send to Kafka and wait for broker acknowledgement before proceeding
      kafkaTemplate.send(shipmentEventsTopic, partitionKey, outboxEvent.getPayload())
          .get(); // blocks — intentional: we only mark published after confirmation

      // Mark the row as published so it is never sent again
      outboxEvent.setPublished(true);
      outboxEvent.setPublishedAt(Instant.now());
      outboxEventRepository.save(outboxEvent);

      log.info("Published outbox event {} (type={}) for aggregate {}",
          outboxEvent.getId(), outboxEvent.getEventType(), outboxEvent.getAggregateId());

    } catch (Exception ex) {
      // Leave published=false — this row will be retried on the next poll
      log.error("Failed to publish outbox event {} for aggregate {}. Will retry on next poll.",
          outboxEvent.getId(), outboxEvent.getAggregateId(), ex);
    }
  }
}
