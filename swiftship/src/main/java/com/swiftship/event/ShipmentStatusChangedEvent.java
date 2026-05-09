package com.swiftship.event;

import com.swiftship.dto.common.enums.ShipmentStatus;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents the domain event emitted when a shipment changes status.
 **/
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShipmentStatusChangedEvent {
  private UUID eventId;
  private UUID shipmentId;
  private String trackingNumber;
  private ShipmentStatus previousStatus;
  private ShipmentStatus newStatus;
  private String customerEmail;
  private String carrierName;
  private String notes;

  @Builder.Default
  private Instant occurredAt = Instant.now();
}
