package com.swiftship.exception;

import com.swiftship.dto.common.enums.ShipmentStatus;


public class InvalidStatusTransitionException extends RuntimeException {
  public InvalidStatusTransitionException(ShipmentStatus oldStatus, ShipmentStatus newStatus) {
    super(String.format("Cannot transition shipment from %s to %s. " +
        "Allowed transitions from %s: %s", oldStatus, newStatus, oldStatus, oldStatus.allowedTransitions()));
  }
}
