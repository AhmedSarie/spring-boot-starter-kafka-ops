package io.github.ahmedsarie.kafka.ops;

import java.util.Objects;

public class TopicConfig {

  private final String name;
  private final RetryConfig retryConfig;

  private TopicConfig(String name, RetryConfig retryConfig) {
    this.name = name;
    this.retryConfig = retryConfig;
  }

  public static TopicConfig of(String name) {
    return new TopicConfig(name, null);
  }

  public static TopicConfig withFixedRetry(String name, int maxAttempts, long intervalMs) {
    return new TopicConfig(name, new RetryConfig(maxAttempts, intervalMs, 1.0));
  }

  public static TopicConfig withExponentialRetry(String name, int maxAttempts,
                                                  long intervalMs, double multiplier) {
    return new TopicConfig(name, new RetryConfig(maxAttempts, intervalMs, multiplier));
  }

  public String getName() {
    return name;
  }

  public RetryConfig getRetryConfig() {
    return retryConfig;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    TopicConfig that = (TopicConfig) o;
    return Objects.equals(name, that.name);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name);
  }

  @Override
  public String toString() {
    return "TopicConfig{name='" + name + "', retryConfig=" + retryConfig + "}";
  }
}
