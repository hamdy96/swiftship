package com.swiftship.exception;


public class ShipmentNotFoundException extends RuntimeException {
  public ShipmentNotFoundException(String identifier) {
    super("Shipment not found: " + identifier);
  }
}
