package io.github.ahmedsarie.kafka.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.ahmedsarie.kafka.ops.KafkaOpsProperties.RestApi;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class KafkaOpsPropertiesTest {

  static boolean isRestEnabled = true;
  static String groupId = "test-group-id";
  static Integer maxPollIntervalMs = 100;
  static String retryEndpointPath = "retry-uri";

  RestApi restApi = new RestApi(isRestEnabled, retryEndpointPath);

  @Test
  @DisplayName("KafkaOpsProperties sets all properties passed to its constructor")
  void testHappyScenario1() {
    // when
    var properties = new KafkaOpsProperties(restApi, groupId, maxPollIntervalMs);

    // then
    assertEquals(isRestEnabled, properties.getRestApi().isEnabled());
    assertEquals(retryEndpointPath, properties.getRestApi().getRetryEndpointUrl());
    assertEquals(groupId, properties.getGroupId());
    assertEquals(maxPollIntervalMs, properties.getMaxPollIntervalMs());
  }

  @Test
  @DisplayName("KafkaOpsProperties sets groupId & maxPollIntervalMs to default values when null")
  void testHappyScenario2() {
    // when
    var properties = new KafkaOpsProperties(restApi, null, null);

    // then
    assertEquals("default-ops-group-id", properties.getGroupId());
    assertEquals(5000, properties.getMaxPollIntervalMs());
  }
}
