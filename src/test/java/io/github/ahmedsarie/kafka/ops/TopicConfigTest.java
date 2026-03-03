package io.github.ahmedsarie.kafka.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TopicConfigTest {

  @Test
  @DisplayName("of() creates TopicConfig with the given name")
  void shouldCreateWithName() {
    // when
    var config = TopicConfig.of("orders");

    // then
    assertEquals("orders", config.getName());
  }

  @Test
  @DisplayName("equals compares by name only")
  void shouldEqualByNameOnly() {
    // prepare
    var config1 = TopicConfig.of("orders");
    var config2 = TopicConfig.of("orders");
    var config3 = TopicConfig.of("payments");

    // then
    assertEquals(config1, config2);
    assertNotEquals(config1, config3);
  }

  @Test
  @DisplayName("hashCode is based on name only")
  void shouldHashByNameOnly() {
    // prepare
    var config1 = TopicConfig.of("orders");
    var config2 = TopicConfig.of("orders");

    // then
    assertEquals(config1.hashCode(), config2.hashCode());
  }

  @Test
  @DisplayName("equals returns false for null and different type")
  void shouldNotEqualNullOrDifferentType() {
    // prepare
    var config = TopicConfig.of("orders");

    // then
    assertNotEquals(null, config);
    assertNotEquals("orders", config);
  }

  @Test
  @DisplayName("equals returns true for same instance")
  void shouldEqualSameInstance() {
    // prepare
    var config = TopicConfig.of("orders");

    // then
    assertEquals(config, config);
  }

  @Test
  @DisplayName("toString contains the topic name")
  void shouldIncludeNameInToString() {
    // when
    var result = TopicConfig.of("orders").toString();

    // then
    assertEquals("TopicConfig{name='orders'}", result);
  }
}
