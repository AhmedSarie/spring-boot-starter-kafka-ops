package io.github.ahmedsarie.kafka.ops;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
class RetryConfig {

  private final int maxAttempts;
  private final long intervalMs;
  private final double multiplier;

  boolean isExponential() {
    return multiplier > 1.0;
  }

  @Override
  public String toString() {
    return "RetryConfig{maxAttempts=" + maxAttempts
        + ", intervalMs=" + intervalMs
        + ", multiplier=" + multiplier + "}";
  }
}
