package io.github.ahmedsarie.kafka.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;

class KafkaOpsErrorHandlerConfigurationTest {

  private ListableBeanFactory beanFactory;

  @BeforeEach
  void setUp() {
    beanFactory = mock(ListableBeanFactory.class);
  }

  @Test
  @DisplayName("should create error handler when consumer has retry config")
  void shouldCreateErrorHandlerWithRetryConfig() {
    // prepare
    var consumer = mock(KafkaOpsAwareConsumer.class);
    when(consumer.getTopic()).thenReturn(TopicConfig.withFixedRetry("orders", 3, 1000));
    when(consumer.getDltTopic()).thenReturn(null);
    when(consumer.getRetryTopic()).thenReturn(null);
    when(beanFactory.getBeansOfType(KafkaOpsAwareConsumer.class))
        .thenReturn(Map.of("orderConsumer", consumer));
    var config = new KafkaOpsErrorHandlerConfiguration(beanFactory);

    // when
    var handler = config.kafkaOpsDefaultErrorHandler();

    // then
    assertNotNull(handler);
  }

  @Test
  @DisplayName("should create error handler when consumer has DLT topic")
  void shouldCreateErrorHandlerWithDltTopic() {
    // prepare
    var consumer = mock(KafkaOpsAwareConsumer.class);
    when(consumer.getTopic()).thenReturn(TopicConfig.of("orders"));
    when(consumer.getDltTopic()).thenReturn(TopicConfig.of("orders.DLT"));
    when(consumer.getRetryTopic()).thenReturn(null);
    when(consumer.getContainerName()).thenReturn(null);
    when(beanFactory.getBeansOfType(KafkaOpsAwareConsumer.class))
        .thenReturn(Map.of("orderConsumer", consumer));
    mockContainerFactory();
    var config = new KafkaOpsErrorHandlerConfiguration(beanFactory);

    // when
    var handler = config.kafkaOpsDefaultErrorHandler();

    // then
    assertNotNull(handler);
  }

  @Test
  @DisplayName("should not create error handler when no consumer needs it")
  void shouldNotCreateErrorHandlerWhenNotNeeded() {
    // prepare
    var consumer = mock(KafkaOpsAwareConsumer.class);
    when(consumer.getTopic()).thenReturn(TopicConfig.of("orders"));
    when(consumer.getDltTopic()).thenReturn(null);
    when(consumer.getRetryTopic()).thenReturn(null);
    when(beanFactory.getBeansOfType(KafkaOpsAwareConsumer.class))
        .thenReturn(Map.of("orderConsumer", consumer));
    var config = new KafkaOpsErrorHandlerConfiguration(beanFactory);

    // when
    var handler = config.kafkaOpsDefaultErrorHandler();

    // then
    assertNull(handler);
  }

  @Test
  @DisplayName("should create error handler with recoverer when DLT and retry configured")
  void shouldCreateErrorHandlerWithRecoverer() {
    // prepare
    var consumer = mock(KafkaOpsAwareConsumer.class);
    when(consumer.getTopic()).thenReturn(TopicConfig.withFixedRetry("orders", 3, 1000));
    when(consumer.getDltTopic()).thenReturn(TopicConfig.of("orders.DLT"));
    when(consumer.getRetryTopic()).thenReturn(TopicConfig.withFixedRetry("orders-retry", 5, 2000));
    when(consumer.getContainerName()).thenReturn(null);
    when(beanFactory.getBeansOfType(KafkaOpsAwareConsumer.class))
        .thenReturn(Map.of("orderConsumer", consumer));
    mockContainerFactory();
    var config = new KafkaOpsErrorHandlerConfiguration(beanFactory);

    // when
    var handler = config.kafkaOpsDefaultErrorHandler();

    // then
    assertNotNull(handler);
  }

  @Test
  @DisplayName("should create error handler without recoverer when only retry config no DLT")
  void shouldCreateErrorHandlerWithoutRecoverer() {
    // prepare
    var consumer = mock(KafkaOpsAwareConsumer.class);
    when(consumer.getTopic()).thenReturn(TopicConfig.withExponentialRetry("orders", 3, 500, 2.0));
    when(consumer.getDltTopic()).thenReturn(null);
    when(consumer.getRetryTopic()).thenReturn(null);
    when(beanFactory.getBeansOfType(KafkaOpsAwareConsumer.class))
        .thenReturn(Map.of("orderConsumer", consumer));
    var config = new KafkaOpsErrorHandlerConfiguration(beanFactory);

    // when
    var handler = config.kafkaOpsDefaultErrorHandler();

    // then
    assertNotNull(handler);
  }

  // --- Destination resolver tests ---

  @Test
  @DisplayName("destination resolver routes main topic to retry topic")
  void shouldRouteMainToRetry() {
    // prepare
    var consumer = mock(KafkaOpsAwareConsumer.class);
    when(consumer.getTopic()).thenReturn(TopicConfig.of("orders"));
    when(consumer.getRetryTopic()).thenReturn(TopicConfig.of("orders-retry"));
    when(consumer.getDltTopic()).thenReturn(TopicConfig.of("orders.DLT"));
    var config = new KafkaOpsErrorHandlerConfiguration(beanFactory);
    var resolver = config.buildDestinationResolver(List.of(consumer));

    var record = new ConsumerRecord<>("orders", 0, 0L, "key", "value");

    // when
    var result = resolver.apply(record, new RuntimeException("test"));

    // then
    assertNotNull(result);
    assertEquals("orders-retry", result.topic());
    assertEquals(-1, result.partition());
  }

  @Test
  @DisplayName("destination resolver routes retry topic to DLT")
  void shouldRouteRetryToDlt() {
    // prepare
    var consumer = mock(KafkaOpsAwareConsumer.class);
    when(consumer.getTopic()).thenReturn(TopicConfig.of("orders"));
    when(consumer.getRetryTopic()).thenReturn(TopicConfig.of("orders-retry"));
    when(consumer.getDltTopic()).thenReturn(TopicConfig.of("orders.DLT"));
    var config = new KafkaOpsErrorHandlerConfiguration(beanFactory);
    var resolver = config.buildDestinationResolver(List.of(consumer));

    var record = new ConsumerRecord<>("orders-retry", 0, 0L, "key", "value");

    // when
    var result = resolver.apply(record, new RuntimeException("test"));

    // then
    assertNotNull(result);
    assertEquals("orders.DLT", result.topic());
    assertEquals(-1, result.partition());
  }

  @Test
  @DisplayName("destination resolver routes main to DLT when no retry topic")
  void shouldRouteMainToDltWhenNoRetry() {
    // prepare
    var consumer = mock(KafkaOpsAwareConsumer.class);
    when(consumer.getTopic()).thenReturn(TopicConfig.of("payments"));
    when(consumer.getRetryTopic()).thenReturn(null);
    when(consumer.getDltTopic()).thenReturn(TopicConfig.of("payments.DLT"));
    var config = new KafkaOpsErrorHandlerConfiguration(beanFactory);
    var resolver = config.buildDestinationResolver(List.of(consumer));

    var record = new ConsumerRecord<>("payments", 0, 0L, "key", "value");

    // when
    var result = resolver.apply(record, new RuntimeException("test"));

    // then
    assertNotNull(result);
    assertEquals("payments.DLT", result.topic());
    assertEquals(-1, result.partition());
  }

  @Test
  @DisplayName("destination resolver returns null for unmapped topic")
  void shouldReturnNullForUnmappedTopic() {
    // prepare
    var consumer = mock(KafkaOpsAwareConsumer.class);
    when(consumer.getTopic()).thenReturn(TopicConfig.of("orders"));
    when(consumer.getRetryTopic()).thenReturn(TopicConfig.of("orders-retry"));
    when(consumer.getDltTopic()).thenReturn(TopicConfig.of("orders.DLT"));
    var config = new KafkaOpsErrorHandlerConfiguration(beanFactory);
    var resolver = config.buildDestinationResolver(List.of(consumer));

    var record = new ConsumerRecord<>("unknown-topic", 0, 0L, "key", "value");

    // when
    var result = resolver.apply(record, new RuntimeException("test"));

    // then
    assertNull(result);
  }

  @SuppressWarnings("unchecked")
  private void mockContainerFactory() {
    var factory = mock(ConcurrentKafkaListenerContainerFactory.class);
    var consumerFactory = mock(DefaultKafkaConsumerFactory.class);
    when(factory.getConsumerFactory()).thenReturn(consumerFactory);
    when(consumerFactory.getConfigurationProperties()).thenReturn(Map.of(
        "bootstrap.servers", "localhost:9092"));
    when(beanFactory.getBean("kafkaListenerContainerFactory")).thenReturn(factory);
  }
}
