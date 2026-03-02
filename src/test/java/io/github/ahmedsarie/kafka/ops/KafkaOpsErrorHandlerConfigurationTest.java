package io.github.ahmedsarie.kafka.ops;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ListableBeanFactory;

class KafkaOpsErrorHandlerConfigurationTest {

  private final ListableBeanFactory beanFactory = mock(ListableBeanFactory.class);

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

  @SuppressWarnings("unchecked")
  private void mockContainerFactory() {
    var factory = mock(org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory.class);
    var consumerFactory = mock(org.springframework.kafka.core.DefaultKafkaConsumerFactory.class);
    when(factory.getConsumerFactory()).thenReturn(consumerFactory);
    when(consumerFactory.getConfigurationProperties()).thenReturn(Map.of(
        "bootstrap.servers", "localhost:9092"
    ));
    when(beanFactory.getBean("kafkaListenerContainerFactory")).thenReturn(factory);
  }
}
