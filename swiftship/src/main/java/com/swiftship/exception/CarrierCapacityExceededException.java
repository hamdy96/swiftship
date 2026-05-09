package com.swiftship.exception;


public class CarrierCapacityExceededException extends RuntimeException {
  public CarrierCapacityExceededException(int maxCapacity) {
    super("Carrier has reached the maximum limit of " + maxCapacity +
        " active shipments. Please assign a different carrier.");
  }
}
