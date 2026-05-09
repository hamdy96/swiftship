package com.swiftship.exception;

public class CarrierNotFoundException extends RuntimeException {
  public CarrierNotFoundException(String identifier) {
    super("Carrier not found: " + identifier);
  }
}
