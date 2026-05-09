package com.swiftship.dto.common.enums;

import lombok.Getter;

import java.util.Set;

@Getter
public enum ShipmentStatus {
  CREATED,
  PICKED_UP,
  IN_TRANSIT,
  OUT_FOR_DELIVERY,
  DELIVERED,
  CANCELLED,
  RETURNED;

  public Set<ShipmentStatus> allowedTransitions() {
    return switch (this) {
      case CREATED -> Set.of(PICKED_UP, CANCELLED);
      case PICKED_UP -> Set.of(IN_TRANSIT, CANCELLED);
      case IN_TRANSIT -> Set.of(OUT_FOR_DELIVERY);
      case OUT_FOR_DELIVERY -> Set.of(DELIVERED);
      case DELIVERED -> Set.of(RETURNED); // ValidateUtils enforces 14-day window
      case CANCELLED, RETURNED -> Set.of();         // terminal — no further transitions
    };
  }

  public boolean canTransitionTo(ShipmentStatus next) {
    return allowedTransitions().contains(next);
  }
}
