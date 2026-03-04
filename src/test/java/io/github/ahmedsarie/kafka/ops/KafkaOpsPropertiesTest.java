package io.github.ahmedsarie.kafka.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.github.ahmedsarie.kafka.ops.KafkaOpsProperties.Batch;
import io.github.ahmedsarie.kafka.ops.KafkaOpsProperties.DltRouting;
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
    // prepare
    var batch = new Batch(50);
    var dltRouting = new DltRouting(true, 10, "0 0 22 * * *");

    // when
    var properties = new KafkaOpsProperties(restApi, groupId, maxPollIntervalMs, batch, dltRouting);

    // then
    assertEquals(isRestEnabled, properties.getRestApi().isEnabled());
    assertEquals(retryEndpointPath, properties.getRestApi().getRetryEndpointUrl());
    assertEquals(groupId, properties.getGroupId());
    assertEquals(maxPollIntervalMs, properties.getMaxPollIntervalMs());
    assertEquals(50, properties.getBatch().getMaxLimit());
    assertEquals(true, properties.getDltRouting().isEnabled());
    assertEquals(10, properties.getDltRouting().getIdleShutdownMinutes());
    assertEquals("0 0 22 * * *", properties.getDltRouting().getRestartCron());
  }

  @Test
  @DisplayName("KafkaOpsProperties sets groupId & maxPollIntervalMs to default values when null")
  void testHappyScenario2() {
    // when
    var properties = new KafkaOpsProperties(restApi, null, null, null, null);

    // then
    assertEquals("default-ops-group-id", properties.getGroupId());
    assertEquals(5000, properties.getMaxPollIntervalMs());
  }

  @Test
  @DisplayName("KafkaOpsProperties sets batch to default when null")
  void testBatchDefaults() {
    // when
    var properties = new KafkaOpsProperties(restApi, groupId, maxPollIntervalMs, null, null);

    // then
    assertNotNull(properties.getBatch());
    assertEquals(100, properties.getBatch().getMaxLimit());
  }

  @Test
  @DisplayName("KafkaOpsProperties sets custom batch maxLimit")
  void testBatchCustomValues() {
    // prepare
    var batch = new Batch(200);

    // when
    var properties = new KafkaOpsProperties(restApi, groupId, maxPollIntervalMs, batch, null);

    // then
    assertEquals(200, properties.getBatch().getMaxLimit());
  }

  @Test
  @DisplayName("KafkaOpsProperties sets DLT routing to defaults when null")
  void testDltRoutingDefaults() {
    // when
    var properties = new KafkaOpsProperties(restApi, groupId, maxPollIntervalMs, null, null);

    // then
    assertNotNull(properties.getDltRouting());
    assertFalse(properties.getDltRouting().isEnabled());
    assertEquals(5, properties.getDltRouting().getIdleShutdownMinutes());
    assertEquals("0 */30 * * * *", properties.getDltRouting().getRestartCron());
  }

  @Test
  @DisplayName("KafkaOpsProperties sets custom DLT routing values")
  void testDltRoutingCustomValues() {
    // prepare
    var dltRouting = new DltRouting(true, 15, "0 0 0 * * SAT,SUN");

    // when
    var properties = new KafkaOpsProperties(restApi, groupId, maxPollIntervalMs, null, dltRouting);

    // then
    assertEquals(true, properties.getDltRouting().isEnabled());
    assertEquals(15, properties.getDltRouting().getIdleShutdownMinutes());
    assertEquals("0 0 0 * * SAT,SUN", properties.getDltRouting().getRestartCron());
  }
}
