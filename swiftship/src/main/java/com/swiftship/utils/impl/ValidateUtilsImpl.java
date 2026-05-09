package com.swiftship.utils.impl;

import com.swiftship.exception.CarrierCapacityExceededException;
import com.swiftship.exception.ReturnWindowExpiredException;
import com.swiftship.model.Shipment;
import com.swiftship.repository.ShipmentRepository;
import com.swiftship.utils.ValidateUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ValidateUtilsImpl implements ValidateUtils {

  @Value("${swiftship.business.max-active-shipments-per-carrier:20}")
  private int maxActiveShipmentsPerCarrier;

  @Value("${swiftship.business.return-window-days:14}")
  private int returnWindowDays;

  private final ShipmentRepository shipmentRepository;

  @Override
  public void validateCarrierCapacity(UUID carrierId) {
    long activeCount = shipmentRepository.countActiveShipmentsByCarrier(carrierId);

    if (activeCount >= maxActiveShipmentsPerCarrier) {
      // Throw a 409 Conflict — the caller should try a different carrier
      throw new CarrierCapacityExceededException(maxActiveShipmentsPerCarrier);
    }
  }

  @Override
  public void validateReturnWindow(Shipment shipment) {
    // If deliveredAt is null (shouldn't happen for a DELIVERED shipment), allow it
    if (shipment.getDeliveredAt() == null) return;

    // Calculate the deadline: deliveredAt + N days
    Instant returnDeadline = shipment.getDeliveredAt().plus(returnWindowDays, ChronoUnit.DAYS);

    if (Instant.now().isAfter(returnDeadline)) {
      // Throw a 422 Unprocessable Entity — the return window has closed
      throw new ReturnWindowExpiredException(returnWindowDays);
    }
  }
}