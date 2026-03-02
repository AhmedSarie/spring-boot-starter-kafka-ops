package io.github.ahmedsarie.kafka.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TopicConfigTest {

  @Test
  @DisplayName("of() creates TopicConfig with name and null retryConfig")
  void testOfFactory() {
    // when
    var config = TopicConfig.of("orders");

    // then
    assertEquals("orders", config.getName());
    assertNull(config.getRetryConfig());
  }

  @Test
  @DisplayName("withFixedRetry creates TopicConfig with fixed retry (multiplier=1.0)")
  void testWithFixedRetry() {
    // when
    var config = TopicConfig.withFixedRetry("payments", 3, 1000L);

    // then
    assertEquals("payments", config.getName());
    assertNotNull(config.getRetryConfig());
    assertEquals(3, config.getRetryConfig().getMaxAttempts());
    assertEquals(1000L, config.getRetryConfig().getIntervalMs());
    assertEquals(1.0, config.getRetryConfig().getMultiplier());
    assertFalse(config.getRetryConfig().isExponential());
  }

  @Test
  @DisplayName("withExponentialRetry creates TopicConfig with exponential retry")
  void testWithExponentialRetry() {
    // when
    var config = TopicConfig.withExponentialRetry("events", 5, 500L, 2.0);

    // then
    assertEquals("events", config.getName());
    assertNotNull(config.getRetryConfig());
    assertEquals(5, config.getRetryConfig().getMaxAttempts());
    assertEquals(500L, config.getRetryConfig().getIntervalMs());
    assertEquals(2.0, config.getRetryConfig().getMultiplier());
    assertTrue(config.getRetryConfig().isExponential());
  }

  @Test
  @DisplayName("equals compares by name only")
  void testEqualsOnNameOnly() {
    // prepare
    var config1 = TopicConfig.of("orders");
    var config2 = TopicConfig.withFixedRetry("orders", 3, 1000L);
    var config3 = TopicConfig.of("payments");

    // then
    assertEquals(config1, config2);
    assertNotEquals(config1, config3);
  }

  @Test
  @DisplayName("hashCode is based on name only")
  void testHashCodeOnNameOnly() {
    // prepare
    var config1 = TopicConfig.of("orders");
    var config2 = TopicConfig.withFixedRetry("orders", 3, 1000L);

    // then
    assertEquals(config1.hashCode(), config2.hashCode());
  }

  @Test
  @DisplayName("equals returns false for null and different type")
  void testEqualsEdgeCases() {
    // prepare
    var config = TopicConfig.of("orders");

    // then
    assertNotEquals(null, config);
    assertNotEquals("orders", config);
  }

  @Test
  @DisplayName("equals returns true for same instance")
  void testEqualsSameInstance() {
    // prepare
    var config = TopicConfig.of("orders");

    // then
    assertEquals(config, config);
  }

  @Test
  @DisplayName("toString contains name and retryConfig")
  void testToString() {
    // prepare
    var config = TopicConfig.withFixedRetry("orders", 3, 1000L);

    // when
    var result = config.toString();

    // then
    assertTrue(result.contains("orders"));
    assertTrue(result.contains("RetryConfig"));
  }

  @Test
  @DisplayName("toString for simple config contains null retryConfig")
  void testToStringNoRetry() {
    // prepare
    var config = TopicConfig.of("orders");

    // when
    var result = config.toString();

    // then
    assertTrue(result.contains("orders"));
    assertTrue(result.contains("null"));
  }
}
