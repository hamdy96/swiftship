package com.swiftship.utils.impl;

import com.swiftship.utils.CommonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CommonUtilsImpl implements CommonUtils {

  /**
   * Example output: SWF-1714978432000-A3B2C1
   */
  @Override
  public String generateTrackingNumber() {
    // Take the first 6 chars of a random UUID (without dashes) as the suffix
    String randomSuffix = UUID.randomUUID()
        .toString()
        .replace("-", "")
        .substring(0, 6)
        .toUpperCase();

    return "SWF-" + System.currentTimeMillis() + "-" + randomSuffix;
  }
}
