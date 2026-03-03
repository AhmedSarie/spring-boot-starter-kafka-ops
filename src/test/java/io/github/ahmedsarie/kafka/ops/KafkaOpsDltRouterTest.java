package io.github.ahmedsarie.kafka.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

class KafkaOpsDltRouterTest {

  private static final String RETRY_COUNT_HEADER = "kafka-ops-retry-count";

  private ListableBeanFactory beanFactory;
  private KafkaOpsProperties properties;
  private KafkaTemplate<byte[], byte[]> mockTemplate;

  @BeforeEach
  void setUp() {
    beanFactory = mock(ListableBeanFactory.class);
    properties = new KafkaOpsProperties(null, "test-ops-group", null, null,
        new KafkaOpsProperties.DltRouting(true, 5, "0 */30 * * * *", 3));
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
  @DisplayName("startFromTimestamp throws NoConsumerFoundException when topic has no DLT router configured")
  void shouldThrowWhenStartFromTimestampUnknownTopic() {
    // prepare
    when(beanFactory.getBeansOfType(KafkaOpsAwareConsumer.class)).thenReturn(Map.of());
    var router = new KafkaOpsDltRouter(beanFactory, properties);
    router.afterPropertiesSet();

    // when + then
    assertThrows(NoConsumerFoundException.class,
        () -> router.startFromTimestamp("unknown-topic", 1000L, false));
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
  @DisplayName("routeRecord sends record to retry topic with incremented retry count")
  @SuppressWarnings("unchecked")
  void shouldRouteRecordToRetryTopicWithIncrementedRetryCount() {
    // prepare
    var router = buildRouterWithConsumer("orders", "orders.DLT", "orders-retry");
    router.afterPropertiesSet();

    var record = new ConsumerRecord<>("orders.DLT", 0, 5L,
        "key".getBytes(StandardCharsets.UTF_8), "value".getBytes(StandardCharsets.UTF_8));
    var future = CompletableFuture.completedFuture(
        new org.apache.kafka.clients.producer.RecordMetadata(
            new TopicPartition("orders-retry", 0), 0L, 0, 0L, 0, 0));
    when(mockTemplate.send(any(ProducerRecord.class))).thenReturn(future);

    // when
    router.routeRecord(record, "orders-retry", "orders");

    // then
    verify(mockTemplate).send(any(ProducerRecord.class));
    var retryHeader = record.headers().lastHeader(RETRY_COUNT_HEADER);
    assertEquals("1", new String(retryHeader.value(), StandardCharsets.UTF_8));
  }

  @Test
  @DisplayName("routeRecord skips record when max retry count exceeded")
  @SuppressWarnings("unchecked")
  void shouldSkipRecordWhenMaxRetryCountExceeded() {
    // prepare
    var router = buildRouterWithConsumer("orders", "orders.DLT", "orders-retry");
    router.afterPropertiesSet();

    var headers = new RecordHeaders();
    headers.add(RETRY_COUNT_HEADER, "3".getBytes(StandardCharsets.UTF_8));
    var record = new ConsumerRecord<>("orders.DLT", 0, 5L, 0L, null, 0, 0,
        "key".getBytes(StandardCharsets.UTF_8), "value".getBytes(StandardCharsets.UTF_8),
        headers, java.util.Optional.empty());

    // when
    router.routeRecord(record, "orders-retry", "orders");

    // then
    verify(mockTemplate, never()).send(any(ProducerRecord.class));
  }

  @Test
  @DisplayName("routeRecord resets retry count to 0 in force mode")
  @SuppressWarnings("unchecked")
  void shouldResetRetryCountInForceMode() {
    // prepare
    var router = buildRouterWithConsumer("orders", "orders.DLT", "orders-retry");
    router.afterPropertiesSet();

    var headers = new RecordHeaders();
    headers.add(RETRY_COUNT_HEADER, "5".getBytes(StandardCharsets.UTF_8));
    var record = new ConsumerRecord<>("orders.DLT", 0, 5L, 0L, null, 0, 0,
        "key".getBytes(StandardCharsets.UTF_8), "value".getBytes(StandardCharsets.UTF_8),
        headers, java.util.Optional.empty());

    var future = CompletableFuture.completedFuture(
        new RecordMetadata(new TopicPartition("orders-retry", 0), 0L, 0, 0L, 0, 0));
    when(mockTemplate.send(any(ProducerRecord.class))).thenReturn(future);

    // set force=true directly (bypasses broker connection)
    router.setForceForTesting("orders", true);

    // when
    router.routeRecord(record, "orders-retry", "orders");

    // then
    verify(mockTemplate).send(any(ProducerRecord.class));
    var retryHeader = record.headers().lastHeader(RETRY_COUNT_HEADER);
    assertEquals("0", new String(retryHeader.value(), StandardCharsets.UTF_8));
  }

  @Test
  @DisplayName("routeRecord throws RuntimeException when send fails")
  @SuppressWarnings("unchecked")
  void shouldThrowWhenSendFails() {
    // prepare
    var router = buildRouterWithConsumer("orders", "orders.DLT", "orders-retry");
    router.afterPropertiesSet();

    var record = new ConsumerRecord<>("orders.DLT", 0, 5L,
        "key".getBytes(StandardCharsets.UTF_8), "value".getBytes(StandardCharsets.UTF_8));
    var failedFuture = new CompletableFuture<RecordMetadata>();
    failedFuture.completeExceptionally(new RuntimeException("Kafka send failed"));
    when(mockTemplate.send(any(ProducerRecord.class))).thenReturn(failedFuture);

    // when + then
    assertThrows(RuntimeException.class,
        () -> router.routeRecord(record, "orders-retry", "orders"));
  }

  @Test
  @DisplayName("routeRecord increments existing retry count header")
  @SuppressWarnings("unchecked")
  void shouldIncrementExistingRetryCount() {
    // prepare
    var router = buildRouterWithConsumer("orders", "orders.DLT", "orders-retry");
    router.afterPropertiesSet();

    var headers = new RecordHeaders();
    headers.add(RETRY_COUNT_HEADER, "2".getBytes(StandardCharsets.UTF_8));
    var record = new ConsumerRecord<>("orders.DLT", 0, 5L, 0L, null, 0, 0,
        "key".getBytes(StandardCharsets.UTF_8), "value".getBytes(StandardCharsets.UTF_8),
        headers, java.util.Optional.empty());

    var future = CompletableFuture.completedFuture(
        new RecordMetadata(new TopicPartition("orders-retry", 0), 0L, 0, 0L, 0, 0));
    when(mockTemplate.send(any(ProducerRecord.class))).thenReturn(future);

    // when
    router.routeRecord(record, "orders-retry", "orders");

    // then
    verify(mockTemplate).send(any(ProducerRecord.class));
    var retryHeader = record.headers().lastHeader(RETRY_COUNT_HEADER);
    assertEquals("3", new String(retryHeader.value(), StandardCharsets.UTF_8));
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
