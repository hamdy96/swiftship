package com.swiftship.dto.update;

import com.swiftship.dto.common.enums.ShipmentStatus;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateShipmentStatusRequest {
  @NotNull(message = "Target status is required")
  private ShipmentStatus status;

  private String notes;
  private String changedBy;
}
