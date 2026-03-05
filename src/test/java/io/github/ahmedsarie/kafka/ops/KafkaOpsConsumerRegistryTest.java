package io.github.ahmedsarie.kafka.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.ahmedsarie.kafka.ops.KafkaOpsService.NoConsumerFoundException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;

class KafkaOpsConsumerRegistryTest {

  private static final String CONSUMER_GROUP = "kafka_ops_consumer";
  private static final String REGISTERED_TOPIC = "registered-topic";

  private ListableBeanFactory beanFactory;
  private KafkaOpsAwareConsumer contractMock;
  private KafkaConsumer mockKafkaConsumer;
  private KafkaConsumer mockMetadataConsumer;
  private final Function<Map<String, Object>, KafkaConsumer> mockFactory = props -> {
    if (props.containsKey("group.id") && String.valueOf(props.get("group.id")).endsWith("-metadata")) {
      return mockMetadataConsumer;
    }
    return mockKafkaConsumer;
  };

  @BeforeEach
  void setUp() {
    beanFactory = mock(ListableBeanFactory.class);
    contractMock = mock(KafkaOpsAwareConsumer.class);
    mockKafkaConsumer = mock(KafkaConsumer.class);
    mockMetadataConsumer = mock(KafkaConsumer.class);
  }

  @Test
  @DisplayName("Returns registered beans successfully")
  void shouldFindRegisteredConsumer() {
    // prepare
    var registry = buildRegistry(Map.of(REGISTERED_TOPIC, contractMock));
    when(contractMock.getTopic()).thenReturn(TopicConfig.of(REGISTERED_TOPIC));

    // when
    registry.afterPropertiesSet();
    var result = registry.find(REGISTERED_TOPIC);

    // then
    assertNotNull(result);
    assertEquals(contractMock, result.getKey());
  }

  @Test
  @DisplayName("Returns set of registered topic names")
  void shouldReturnRegisteredTopics() {
    // prepare
    var registry = buildRegistry(Map.of(REGISTERED_TOPIC, contractMock));
    when(contractMock.getTopic()).thenReturn(TopicConfig.of(REGISTERED_TOPIC));

    // when
    registry.afterPropertiesSet();
    var topics = registry.getRegisteredTopics();

    // then
    assertEquals(1, topics.size());
    assertTrue(topics.contains(REGISTERED_TOPIC));
  }

  @Test
  @DisplayName("Throws when topic not found in registry")
  void shouldThrowWhenTopicNotFound() {
    // prepare
    var notTopicName = "not-registered-topic";
    when(beanFactory.getBeansOfType(KafkaOpsAwareConsumer.class)).thenReturn(Map.of());
    var registry = new KafkaOpsConsumerRegistry(beanFactory, CONSUMER_GROUP, mockFactory);

    // when
    var exception = assertThrows(NoConsumerFoundException.class, () -> registry.find(notTopicName));

    // then
    assertEquals("unable to find consumer for topic=" + notTopicName + " in the registry", exception.getMessage());
  }

  @Test
  @DisplayName("getConsumerDetails returns partition count and message count from mock consumer")
  void shouldReturnConsumerDetailsWithCounts() {
    // prepare
    var registry = buildRegistry(Map.of(REGISTERED_TOPIC, contractMock));
    when(contractMock.getTopic()).thenReturn(TopicConfig.of(REGISTERED_TOPIC));

    var tp0 = new TopicPartition(REGISTERED_TOPIC, 0);
    var tp1 = new TopicPartition(REGISTERED_TOPIC, 1);
    when(mockMetadataConsumer.partitionsFor(REGISTERED_TOPIC))
        .thenReturn(List.of(
            new PartitionInfo(REGISTERED_TOPIC, 0, null, null, null),
            new PartitionInfo(REGISTERED_TOPIC, 1, null, null, null)));
    when(mockMetadataConsumer.endOffsets(anyList()))
        .thenReturn(Map.of(tp0, 100L, tp1, 200L));
    when(mockMetadataConsumer.beginningOffsets(anyList()))
        .thenReturn(Map.of(tp0, 10L, tp1, 50L));

    registry.afterPropertiesSet();

    // when
    var details = registry.getConsumerDetails();

    // then
    assertEquals(1, details.size());
    assertEquals(REGISTERED_TOPIC, details.get(0).getName());
    assertEquals(2, details.get(0).getPartitions());
    assertEquals(240, details.get(0).getMessageCount()); // (100-10) + (200-50)
    verify(mockMetadataConsumer).partitionsFor(REGISTERED_TOPIC);
  }

  @Test
  @DisplayName("Consumer with DLT and retry topics registers all three in the registry")
  void shouldRegisterDltAndRetryTopics() {
    // prepare
    var consumer = mock(KafkaOpsAwareConsumer.class);
    when(consumer.getTopic()).thenReturn(TopicConfig.of("main").withDlt("main.DLT").withRetry("main-retry"));

    var registry = buildRegistry(Map.of("mainBean", consumer));

    // when
    registry.afterPropertiesSet();

    // then
    assertNotNull(registry.find("main"));
    assertNotNull(registry.find("main.DLT"));
    assertNotNull(registry.find("main-retry"));
    assertEquals(3, registry.getRegisteredTopics().size());
  }

  @Test
  @DisplayName("Consumer with only main topic does not register DLT or retry topics")
  void shouldNotRegisterExtraTopicsForMainOnly() {
    // prepare
    var consumer = mock(KafkaOpsAwareConsumer.class);
    when(consumer.getTopic()).thenReturn(TopicConfig.of("main-only"));

    var registry = buildRegistry(Map.of("mainOnlyBean", consumer));

    // when
    registry.afterPropertiesSet();

    // then
    assertEquals(1, registry.getRegisteredTopics().size());
    assertNotNull(registry.find("main-only"));
    assertThrows(NoConsumerFoundException.class, () -> registry.find("main-only.DLT"));
    assertThrows(NoConsumerFoundException.class, () -> registry.find("main-only-retry"));
  }

  @Test
  @DisplayName("getConsumerDetails includes DLT and retry sub-objects")
  void shouldIncludeDltAndRetryInConsumerDetails() {
    // prepare
    var consumer = mock(KafkaOpsAwareConsumer.class);
    when(consumer.getTopic()).thenReturn(TopicConfig.of("orders").withDlt("orders.DLT").withRetry("orders-retry"));

    var registry = buildRegistry(Map.of("ordersBean", consumer));
    registry.afterPropertiesSet();

    // when
    var details = registry.getConsumerDetails();

    // then
    assertEquals(1, details.size());
    assertEquals("orders", details.get(0).getName());
    assertNotNull(details.get(0).getDlt());
    assertEquals("orders.DLT", details.get(0).getDlt().getName());
    assertNotNull(details.get(0).getRetry());
    assertEquals("orders-retry", details.get(0).getRetry().getName());
  }

  @Test
  @DisplayName("getConsumerDetails omits DLT and retry when not declared")
  void shouldOmitDltAndRetryWhenNotDeclared() {
    // prepare
    var consumer = mock(KafkaOpsAwareConsumer.class);
    when(consumer.getTopic()).thenReturn(TopicConfig.of("simple"));

    var registry = buildRegistry(Map.of("simpleBean", consumer));
    registry.afterPropertiesSet();

    // when
    var details = registry.getConsumerDetails();

    // then
    assertEquals(1, details.size());
    assertEquals("simple", details.get(0).getName());
    assertNull(details.get(0).getDlt());
    assertNull(details.get(0).getRetry());
  }

  @Test
  @DisplayName("destroy closes all KafkaConsumer instances")
  void shouldCloseConsumersOnDestroy() {
    // prepare
    var registry = buildRegistry(Map.of(REGISTERED_TOPIC, contractMock));
    when(contractMock.getTopic()).thenReturn(TopicConfig.of(REGISTERED_TOPIC));
    registry.afterPropertiesSet();

    // when
    registry.destroy();

    // then
    verify(mockKafkaConsumer).close();
    verify(mockMetadataConsumer).close();
  }

  @SuppressWarnings("unchecked")
  private KafkaOpsConsumerRegistry buildRegistry(Map<String, KafkaOpsAwareConsumer> consumers) {
    when(beanFactory.getBeansOfType(KafkaOpsAwareConsumer.class)).thenReturn(consumers);
    var mockContainer = mock(ConcurrentKafkaListenerContainerFactory.class);
    when(beanFactory.getBean(anyString())).thenReturn(mockContainer);
    var consumerFactoryMock = mock(DefaultKafkaConsumerFactory.class);
    when(mockContainer.getConsumerFactory()).thenReturn(consumerFactoryMock);
    when(consumerFactoryMock.getConfigurationProperties()).thenReturn(Map.of(
        "bootstrap.servers", "localhost:9092",
        "group.id", "test-group"));
    return new KafkaOpsConsumerRegistry(beanFactory, CONSUMER_GROUP, mockFactory);
  }
}
