package io.github.ahmedsarie.kafka.ops;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.ahmedsarie.kafka.ops.KafkaOpsService.NoConsumerFoundException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.serialization.IntegerDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.event.ListenerContainerIdleEvent;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.support.Acknowledgment;

class KafkaOpsDltRouterTest {

  private ListableBeanFactory beanFactory;
  private KafkaOpsProperties properties;
  private KafkaTemplate<byte[], byte[]> mockTemplate;

  @BeforeEach
  void setUp() {
    beanFactory = mock(ListableBeanFactory.class);
    properties = new KafkaOpsProperties(null, "test-ops-group", null, null,
        new KafkaOpsProperties.DltRouting(true, 5, "0 */30 * * * *", 10));
    mockTemplate = mock(KafkaTemplate.class);
  }

  @Test
  @DisplayName("Router creates container when consumer declares both DLT and retry topics")
  void shouldCreateContainerWhenBothDltAndRetryDeclared() {
    // prepare
    var router = buildRouterWithConsumer("orders", "orders.DLT", "orders-retry");

    // when
    router.afterPropertiesSet();

    // then — start should not throw (route exists)
    router.start("orders");
  }

  @Test
  @DisplayName("Router skips consumer that declares only DLT topic without retry topic")
  void shouldSkipConsumerWithOnlyDlt() {
    // prepare
    var consumer = mock(KafkaOpsAwareConsumer.class);
    when(consumer.getTopic()).thenReturn(TopicConfig.of("payments").withDlt("payments.DLT"));

    when(beanFactory.getBeansOfType(KafkaOpsAwareConsumer.class))
        .thenReturn(Map.of("paymentsBean", consumer));
    var router = new KafkaOpsDltRouter(beanFactory, properties);

    // when
    router.afterPropertiesSet();

    // then
    assertThrows(NoConsumerFoundException.class, () -> router.start("payments"));
  }

  @Test
  @DisplayName("Router skips consumer that declares only retry topic without DLT topic")
  void shouldSkipConsumerWithOnlyRetry() {
    // prepare
    var consumer = mock(KafkaOpsAwareConsumer.class);
    when(consumer.getTopic()).thenReturn(TopicConfig.of("notifications").withRetry("notifications-retry"));

    when(beanFactory.getBeansOfType(KafkaOpsAwareConsumer.class))
        .thenReturn(Map.of("notificationsBean", consumer));
    var router = new KafkaOpsDltRouter(beanFactory, properties);

    // when
    router.afterPropertiesSet();

    // then
    assertThrows(NoConsumerFoundException.class, () -> router.start("notifications"));
  }

  @Test
  @DisplayName("Router skips consumer with neither DLT nor retry topic")
  void shouldSkipConsumerWithNeitherDltNorRetry() {
    // prepare
    var consumer = mock(KafkaOpsAwareConsumer.class);
    when(consumer.getTopic()).thenReturn(TopicConfig.of("simple"));

    when(beanFactory.getBeansOfType(KafkaOpsAwareConsumer.class))
        .thenReturn(Map.of("simpleBean", consumer));
    var router = new KafkaOpsDltRouter(beanFactory, properties);

    // when
    router.afterPropertiesSet();

    // then
    assertThrows(NoConsumerFoundException.class, () -> router.start("simple"));
  }

  @Test
  @DisplayName("start throws NoConsumerFoundException when topic has no DLT router configured")
  void shouldThrowWhenStartingUnknownTopic() {
    // prepare
    when(beanFactory.getBeansOfType(KafkaOpsAwareConsumer.class)).thenReturn(Map.of());
    var router = new KafkaOpsDltRouter(beanFactory, properties);
    router.afterPropertiesSet();

    // when + then
    assertThrows(NoConsumerFoundException.class, () -> router.start("unknown-topic"));
  }

  @Test
  @DisplayName("destroy stops all running containers cleanly")
  void shouldDestroyCleanly() {
    // prepare
    when(beanFactory.getBeansOfType(KafkaOpsAwareConsumer.class)).thenReturn(Map.of());
    var router = new KafkaOpsDltRouter(beanFactory, properties);
    router.afterPropertiesSet();

    // when + then (no exception)
    router.destroy();
  }

  @Test
  @DisplayName("Router registers multiple consumers that both have DLT and retry")
  void shouldRegisterMultipleConsumers() {
    // prepare
    var consumer1 = mockConsumer("orders", "orders.DLT", "orders-retry");
    var consumer2 = mockConsumer("payments", "payments.DLT", "payments-retry");
    when(beanFactory.getBeansOfType(KafkaOpsAwareConsumer.class))
        .thenReturn(Map.of("ordersBean", consumer1, "paymentsBean", consumer2));
    mockContainerFactory();

    var router = new KafkaOpsDltRouter(beanFactory, properties);
    router.setKafkaTemplate(mockTemplate);

    // when
    router.afterPropertiesSet();

    // then — both routes should exist (start does not throw)
    router.start("orders");
    router.start("payments");
  }

  @Test
  @DisplayName("routeRecord sends record to retry topic with cycle header and acknowledges")
  @SuppressWarnings("unchecked")
  void shouldRouteRecordToRetryTopic() {
    // prepare
    var router = buildRouterWithConsumer("orders", "orders.DLT", "orders-retry");
    router.afterPropertiesSet();
    router.start("orders");

    var record = new ConsumerRecord<>("orders.DLT", 0, 5L, 1000L, null, 0, 0,
        "key".getBytes(StandardCharsets.UTF_8), "value".getBytes(StandardCharsets.UTF_8),
        new RecordHeaders(), java.util.Optional.empty());
    var future = CompletableFuture.completedFuture(
        new RecordMetadata(new TopicPartition("orders-retry", 0), 0L, 0, 0L, 0, 0));
    when(mockTemplate.send(any(ProducerRecord.class))).thenReturn(future);
    var ack = mock(Acknowledgment.class);

    // when
    router.routeRecord(record, "orders-retry", "orders", ack);

    // then
    verify(mockTemplate).send(any(ProducerRecord.class));
    verify(ack).acknowledge();
    var cycleHeader = record.headers().lastHeader("kafka-ops-dlt-cycle");
    assertEquals("1", new String(cycleHeader.value(), StandardCharsets.UTF_8));
  }

  @Test
  @DisplayName("routeRecord skips and acknowledges when max cycles exceeded")
  @SuppressWarnings("unchecked")
  void shouldSkipWhenMaxCyclesExceeded() {
    // prepare
    var router = buildRouterWithConsumer("orders", "orders.DLT", "orders-retry");
    router.afterPropertiesSet();
    router.start("orders");

    var headers = new RecordHeaders();
    headers.add("kafka-ops-dlt-cycle", "10".getBytes(StandardCharsets.UTF_8));
    var record = new ConsumerRecord<>("orders.DLT", 0, 5L, 1000L, null, 0, 0,
        "key".getBytes(StandardCharsets.UTF_8), "value".getBytes(StandardCharsets.UTF_8),
        headers, java.util.Optional.empty());
    var ack = mock(Acknowledgment.class);

    // when
    router.routeRecord(record, "orders-retry", "orders", ack);

    // then — acknowledged (offset moves forward) but NOT sent
    verify(ack).acknowledge();
    verify(mockTemplate, never()).send(any(ProducerRecord.class));
  }

  @Test
  @DisplayName("routeRecord increments existing cycle count header")
  @SuppressWarnings("unchecked")
  void shouldIncrementExistingCycleCount() {
    // prepare
    var router = buildRouterWithConsumer("orders", "orders.DLT", "orders-retry");
    router.afterPropertiesSet();
    router.start("orders");

    var headers = new RecordHeaders();
    headers.add("kafka-ops-dlt-cycle", "3".getBytes(StandardCharsets.UTF_8));
    var record = new ConsumerRecord<>("orders.DLT", 0, 5L, 1000L, null, 0, 0,
        "key".getBytes(StandardCharsets.UTF_8), "value".getBytes(StandardCharsets.UTF_8),
        headers, java.util.Optional.empty());
    var future = CompletableFuture.completedFuture(
        new RecordMetadata(new TopicPartition("orders-retry", 0), 0L, 0, 0L, 0, 0));
    when(mockTemplate.send(any(ProducerRecord.class))).thenReturn(future);
    var ack = mock(Acknowledgment.class);

    // when
    router.routeRecord(record, "orders-retry", "orders", ack);

    // then
    verify(mockTemplate).send(any(ProducerRecord.class));
    var cycleHeader = record.headers().lastHeader("kafka-ops-dlt-cycle");
    assertEquals("4", new String(cycleHeader.value(), StandardCharsets.UTF_8));
  }

  @Test
  @DisplayName("routeRecord stops container without acknowledging when record timestamp exceeds cutoff")
  @SuppressWarnings("unchecked")
  void shouldStopContainerWhenRecordExceedsCutoffTimestamp() {
    // prepare
    var router = buildRouterWithConsumer("orders", "orders.DLT", "orders-retry");
    router.afterPropertiesSet();
    router.start("orders");

    // record timestamp far in the future (beyond any cutoff)
    var record = new ConsumerRecord<>("orders.DLT", 0, 5L, Long.MAX_VALUE, null, 0, 0,
        "key".getBytes(StandardCharsets.UTF_8), "value".getBytes(StandardCharsets.UTF_8),
        new RecordHeaders(), java.util.Optional.empty());
    var ack = mock(Acknowledgment.class);

    // when
    router.routeRecord(record, "orders-retry", "orders", ack);

    // then — not acknowledged, not sent
    verify(ack, never()).acknowledge();
    verify(mockTemplate, never()).send(any(ProducerRecord.class));
  }

  @Test
  @DisplayName("routeRecord throws RuntimeException and does not acknowledge when send fails")
  @SuppressWarnings("unchecked")
  void shouldThrowWhenSendFails() {
    // prepare
    var router = buildRouterWithConsumer("orders", "orders.DLT", "orders-retry");
    router.afterPropertiesSet();
    router.start("orders");

    var record = new ConsumerRecord<>("orders.DLT", 0, 5L, 1000L, null, 0, 0,
        "key".getBytes(StandardCharsets.UTF_8), "value".getBytes(StandardCharsets.UTF_8),
        new RecordHeaders(), java.util.Optional.empty());
    var failedFuture = new CompletableFuture<RecordMetadata>();
    failedFuture.completeExceptionally(new RuntimeException("Kafka send failed"));
    when(mockTemplate.send(any(ProducerRecord.class))).thenReturn(failedFuture);
    var ack = mock(Acknowledgment.class);

    // when + then
    assertThrows(RuntimeException.class,
        () -> router.routeRecord(record, "orders-retry", "orders", ack));
    verify(ack, never()).acknowledge();
  }

  @Test
  @DisplayName("onIdleEvent stops running container")
  @SuppressWarnings("unchecked")
  void shouldStopContainerOnIdleEvent() {
    // prepare
    var router = buildRouterWithConsumer("orders", "orders.DLT", "orders-retry");
    router.afterPropertiesSet();

    var mockEvent = mock(ListenerContainerIdleEvent.class);
    when(mockEvent.getContainer(ConcurrentMessageListenerContainer.class)).thenReturn(null);

    // when — event with unrelated container does nothing
    router.onIdleEvent(mockEvent);

    // then — no exception
  }

  @Test
  @DisplayName("scheduledRestart restarts all stopped containers")
  void shouldRestartAllStoppedContainers() {
    // prepare
    var router = buildRouterWithConsumer("orders", "orders.DLT", "orders-retry");
    router.afterPropertiesSet();

    // when — scheduledRestart should not throw
    router.scheduledRestart();
  }

  // --- Helpers ---

  private KafkaOpsDltRouter buildRouterWithConsumer(String main, String dlt, String retry) {
    var consumer = mockConsumer(main, dlt, retry);
    when(beanFactory.getBeansOfType(KafkaOpsAwareConsumer.class))
        .thenReturn(Map.of("bean", consumer));
    mockContainerFactory();

    var router = new KafkaOpsDltRouter(beanFactory, properties);
    router.setKafkaTemplate(mockTemplate);
    return router;
  }

  private KafkaOpsAwareConsumer mockConsumer(String main, String dlt, String retry) {
    var consumer = mock(KafkaOpsAwareConsumer.class);
    when(consumer.getTopic()).thenReturn(TopicConfig.of(main).withDlt(dlt).withRetry(retry));
    return consumer;
  }

  @SuppressWarnings("unchecked")
  private void mockContainerFactory() {
    var mockContainer = mock(ConcurrentKafkaListenerContainerFactory.class);
    when(beanFactory.getBean(anyString())).thenReturn(mockContainer);
    var consumerFactoryMock = mock(DefaultKafkaConsumerFactory.class);
    when(mockContainer.getConsumerFactory()).thenReturn(consumerFactoryMock);
    when(consumerFactoryMock.getConfigurationProperties()).thenReturn(Map.of(
        "key.deserializer", IntegerDeserializer.class,
        "value.deserializer", StringDeserializer.class,
        "isolation.level", "read_uncommitted",
        "group.id", "reconsumerId",
        "bootstrap.servers", "127.0.0.1:50120",
        "auto.offset.reset", "earliest"));
  }
}
