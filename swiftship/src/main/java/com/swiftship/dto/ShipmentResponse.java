package com.swiftship.dto;

import com.swiftship.dto.common.enums.ShipmentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShipmentResponse {
  private UUID id;
  private String trackingNumber;
  private String customerName;
  private String customerEmail;
  private String originAddress;
  private String destinationAddress;
  private UUID originWarehouseId;
  private String originWarehouseName;
  private UUID destinationWarehouseId;
  private String destinationWarehouseName;
  private UUID carrierId;
  private String carrierName;
  private ShipmentStatus status;
  private boolean delayed;
  private BigDecimal weightKg;
  private LocalDate estimatedDeliveryDate;
  private Instant deliveredAt;
  private Instant createdAt;
  private Instant updatedAt;
}
