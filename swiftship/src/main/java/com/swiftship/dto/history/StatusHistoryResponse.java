package com.swiftship.dto.history;

import com.swiftship.dto.common.enums.ShipmentStatus;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatusHistoryResponse {
  private UUID id;
  private ShipmentStatus fromStatus;
  private ShipmentStatus toStatus;
  private String notes;
  private String changedBy;
  private Instant changedAt;
}
