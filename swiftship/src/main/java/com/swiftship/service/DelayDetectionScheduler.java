package com.swiftship.service;

import com.swiftship.model.Shipment;
import com.swiftship.repository.ShipmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Runs every hour and logs any delayed shipments it finds.
 * - Pushing a "SHIPMENT_DELAYED" event to Kafka (via the outbox)
 * - Triggering SMS / email alerts to the customer or ops team
 * - Feeding a monitoring dashboard or SLA report
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DelayDetectionScheduler {

  private final ShipmentRepository shipmentRepository;

  /**
   * Runs every hour on the hour (cron = "0 0 * * * *").
   */
  @Scheduled(cron = "0 0 * * * *")
  public void detectDelayedShipments() {
    List<Shipment> delayed = shipmentRepository.findDelayedShipments();

    if (delayed.isEmpty()) {
      log.debug("Delay check: no delayed shipments found.");
      return;
    }

    // Log a summary at WARN level so it shows up in production monitoring dashboards
    log.warn("Delay check: {} shipment(s) are overdue (>48h past estimated delivery):",
        delayed.size());

    delayed.forEach(s ->
        log.warn("  → tracking={} | status={} | estimatedDelivery={}",
            s.getTrackingNumber(),
            s.getStatus(),
            s.getEstimatedDeliveryDate())
    );
  }
}
