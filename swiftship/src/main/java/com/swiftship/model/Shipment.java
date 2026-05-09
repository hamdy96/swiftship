package com.swiftship.model;

import com.swiftship.dto.common.enums.ShipmentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "shipments")
public class Shipment {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "tracking_number", nullable = false, unique = true, length = 30)
  private String trackingNumber;

  @Column(name = "customer_name", nullable = false, length = 200)
  private String customerName;

  @Column(name = "customer_email", nullable = false)
  private String customerEmail;

  @Column(name = "origin_address", nullable = false)
  private String originAddress;

  @Column(name = "destination_address", nullable = false)
  private String destinationAddress;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "origin_warehouse_id")
  private Warehouse originWarehouse;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "destination_warehouse_id")
  private Warehouse destinationWarehouse;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "carrier_id")
  private Carrier carrier;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  @Builder.Default
  private ShipmentStatus status = ShipmentStatus.CREATED;

  @Column(name = "weight_kg", precision = 8, scale = 3)
  private BigDecimal weightKg;

  @Column(name = "estimated_delivery_date")
  private LocalDate estimatedDeliveryDate;

  @Column(name = "delivered_at")
  private Instant deliveredAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  @Builder.Default
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  @Builder.Default
  private Instant updatedAt = Instant.now();

  @PreUpdate
  void onUpdate() {
    this.updatedAt = Instant.now();
  }

  @Transient
  public boolean isDelayed() {
    // Terminal states are never delayed
    if (status == ShipmentStatus.DELIVERED
        || status == ShipmentStatus.CANCELLED
        || status == ShipmentStatus.RETURNED
        || estimatedDeliveryDate == null) {
      return false;
    }

    // estimatedDeliveryDate is a calendar date (midnight); add 48h grace period
    return estimatedDeliveryDate
        .atStartOfDay()
        .plusHours(48)
        .isBefore(java.time.LocalDateTime.now());
  }
}
