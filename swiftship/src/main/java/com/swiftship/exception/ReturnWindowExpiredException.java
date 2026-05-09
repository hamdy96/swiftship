package com.swiftship.exception;


public class ReturnWindowExpiredException extends RuntimeException {
  public ReturnWindowExpiredException(int returnWindowDays) {
    super("The " + returnWindowDays + "-day return window has expired for this shipment. " +
        "Returns are only accepted within " + returnWindowDays + " days of delivery.");
  }
}
