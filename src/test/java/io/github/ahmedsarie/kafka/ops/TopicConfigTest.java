package io.github.ahmedsarie.kafka.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
  @DisplayName("of() has null dltTopic and retryTopic by default")
  void shouldHaveNullSubTopicsByDefault() {
    // when
    var config = TopicConfig.of("orders");

    // then
    assertNull(config.getDltTopic());
    assertNull(config.getRetryTopic());
  }

  @Test
  @DisplayName("withDlt() sets dltTopic and returns new instance")
  void shouldSetDltTopic() {
    // when
    var config = TopicConfig.of("orders").withDlt("orders.DLT");

    // then
    assertEquals("orders.DLT", config.getDltTopic());
    assertNull(config.getRetryTopic());
  }

  @Test
  @DisplayName("withRetry() sets retryTopic and returns new instance")
  void shouldSetRetryTopic() {
    // when
    var config = TopicConfig.of("orders").withRetry("orders-retry");

    // then
    assertNull(config.getDltTopic());
    assertEquals("orders-retry", config.getRetryTopic());
  }

  @Test
  @DisplayName("withDlt().withRetry() sets both sub-topics")
  void shouldSetBothSubTopics() {
    // when
    var config = TopicConfig.of("orders").withDlt("orders.DLT").withRetry("orders-retry");

    // then
    assertEquals("orders.DLT", config.getDltTopic());
    assertEquals("orders-retry", config.getRetryTopic());
  }

  @Test
  @DisplayName("equals ignores dltTopic and retryTopic — name only")
  void shouldEqualRegardlessOfSubTopics() {
    // prepare
    var plain = TopicConfig.of("orders");
    var withSubs = TopicConfig.of("orders").withDlt("orders.DLT").withRetry("orders-retry");

    // then
    assertEquals(plain, withSubs);
    assertEquals(plain.hashCode(), withSubs.hashCode());
  }

  @Test
  @DisplayName("toString contains name, dltTopic, and retryTopic")
  void shouldIncludeAllFieldsInToString() {
    // when
    var result = TopicConfig.of("orders").withDlt("orders.DLT").withRetry("orders-retry").toString();

    // then
    assertEquals("TopicConfig(name=orders, dltTopic=orders.DLT, retryTopic=orders-retry)", result);
  }
}
