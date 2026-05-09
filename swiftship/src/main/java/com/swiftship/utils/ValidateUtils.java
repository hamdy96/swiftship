package com.swiftship.utils;

import com.swiftship.model.Shipment;

import java.util.UUID;

public interface ValidateUtils {
  void validateCarrierCapacity(UUID carrierId);
  void validateReturnWindow(Shipment shipment);
}
