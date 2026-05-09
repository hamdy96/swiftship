package com.swiftship.dto.create;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateShipmentRequest {
  @NotBlank(message = "Customer name is required")
  private String customerName;

  @NotBlank(message = "Customer email is required")
  @Email(message = "Customer email must be a valid email address")
  private String customerEmail;

  @NotBlank(message = "Origin address is required")
  private String originAddress;

  @NotBlank(message = "Destination address is required")
  private String destinationAddress;

  private UUID originWarehouseId;
  private UUID destinationWarehouseId;

  @NotNull(message = "Carrier ID is required")
  private UUID carrierId;

  @DecimalMin(value = "0.001", message = "Weight must be greater than 0")
  private BigDecimal weightKg;

  private LocalDate estimatedDeliveryDate;
}
