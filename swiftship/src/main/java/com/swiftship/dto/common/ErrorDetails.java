package com.swiftship.dto.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorDetails {
  private int status;
  private String error;
  private String message; // should be two messages one in English and Arabic for locale
  private List<String> details;
  private Instant timestamp;
}
