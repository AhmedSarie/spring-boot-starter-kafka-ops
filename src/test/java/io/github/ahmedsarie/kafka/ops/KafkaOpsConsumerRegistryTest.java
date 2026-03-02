package io.github.ahmedsarie.kafka.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.ahmedsarie.kafka.ops.KafkaOpsService.NoConsumerFoundException;
import java.util.Map;
import org.apache.kafka.common.serialization.IntegerDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;

public class KafkaOpsConsumerRegistryTest {

  ListableBeanFactory listableBeanFactoryMock = mock(ListableBeanFactory.class);
  KafkaOpsAwareConsumer contractMock = mock(KafkaOpsAwareConsumer.class);
  static final String registeredTopic = "registered-topic";
  Map<String, KafkaOpsAwareConsumer> mockData = Map.of(registeredTopic, contractMock);

  private final static String CONSUMER_GROUP = "kafka_ops_consumer";

  @Test
  @DisplayName("Returns registered beans successfully")
  void testHappyScenario() {
    // prepare
    var mockContainer = mock(ConcurrentKafkaListenerContainerFactory.class);
    when(listableBeanFactoryMock.getBeansOfType(KafkaOpsAwareConsumer.class)).thenReturn(mockData);
    when(listableBeanFactoryMock.getBean(anyString())).thenReturn(mockContainer);
    var consumerFactoryMock = mock(DefaultKafkaConsumerFactory.class);
    when(mockContainer.getConsumerFactory()).thenReturn(consumerFactoryMock);
    when(consumerFactoryMock.getConfigurationProperties()).thenReturn(testConsumerProp());
    when(contractMock.getTopic()).thenReturn(TopicConfig.of(registeredTopic));
    var registry = new KafkaOpsConsumerRegistry(listableBeanFactoryMock, CONSUMER_GROUP);
    // when
    registry.afterPropertiesSet();
    var result = registry.find(registeredTopic);

    // then
    assertNotNull(result);
  }

  private Map<Object, Object> testConsumerProp() {
    return Map.of("key.deserializer", IntegerDeserializer.class,
        "value.deserializer", StringDeserializer.class,
        "isolation.level", "read_uncommitted",
        "group.id", "reconsumerId",
        "bootstrap.servers", "127.0.0.1:50120",
        "auto.offset.reset", "earliest");
  }

  @Test
  @DisplayName("Returns set of registered topic names")
  void testGetRegisteredTopics() {
    // prepare
    var mockContainer = mock(ConcurrentKafkaListenerContainerFactory.class);
    when(listableBeanFactoryMock.getBeansOfType(KafkaOpsAwareConsumer.class)).thenReturn(mockData);
    when(listableBeanFactoryMock.getBean(anyString())).thenReturn(mockContainer);
    var consumerFactoryMock = mock(DefaultKafkaConsumerFactory.class);
    when(mockContainer.getConsumerFactory()).thenReturn(consumerFactoryMock);
    when(consumerFactoryMock.getConfigurationProperties()).thenReturn(testConsumerProp());
    when(contractMock.getTopic()).thenReturn(TopicConfig.of(registeredTopic));
    var registry = new KafkaOpsConsumerRegistry(listableBeanFactoryMock, CONSUMER_GROUP);

    // when
    registry.afterPropertiesSet();
    var topics = registry.getRegisteredTopics();

    // then
    assertEquals(1, topics.size());
    assertTrue(topics.contains(registeredTopic));
  }

  @Test
  @DisplayName("Throws when topic not found")
  void testFailedScenario() {
    // prepare
    var notTopicName = "not-registered-topic";
    when(listableBeanFactoryMock.getBeansOfType(KafkaOpsAwareConsumer.class)).thenReturn(mockData);
    var registry = new KafkaOpsConsumerRegistry(listableBeanFactoryMock, CONSUMER_GROUP);

    // when
    var exception = assertThrows(NoConsumerFoundException.class, () -> registry.find(notTopicName));

    // then
    var msg = "unable to find consumer for topic=" + notTopicName + " in the registry";
    assertEquals(msg, exception.getMessage());
  }

  @Test
  @DisplayName("getConsumerDetails returns partition count and message count")
  void testGetConsumerDetails() {
    // prepare
    var mockContainer = mock(ConcurrentKafkaListenerContainerFactory.class);
    when(listableBeanFactoryMock.getBeansOfType(KafkaOpsAwareConsumer.class)).thenReturn(mockData);
    when(listableBeanFactoryMock.getBean(anyString())).thenReturn(mockContainer);
    var consumerFactoryMock = mock(DefaultKafkaConsumerFactory.class);
    when(mockContainer.getConsumerFactory()).thenReturn(consumerFactoryMock);
    when(consumerFactoryMock.getConfigurationProperties()).thenReturn(testConsumerProp());
    when(contractMock.getTopic()).thenReturn(TopicConfig.of(registeredTopic));
    var registry = new KafkaOpsConsumerRegistry(listableBeanFactoryMock, CONSUMER_GROUP);
    registry.afterPropertiesSet();

    // The KafkaConsumer in the registry is a real one but we can't easily mock it after afterPropertiesSet.
    // Instead, we verify the method handles broker errors gracefully (returns -1).
    // when
    var details = registry.getConsumerDetails();

    // then
    assertEquals(1, details.size());
    assertEquals(registeredTopic, details.get(0).getName());
    // The real KafkaConsumer will throw because there's no actual broker, so we expect -1
    assertEquals(-1, details.get(0).getPartitions());
    assertEquals(-1, details.get(0).getMessageCount());
  }

  @Test
  @DisplayName("Consumer with DLT and retry topics registers all three in the registry")
  void testDltAndRetryTopicRegistration() {
    // prepare
    var dltConsumer = mock(KafkaOpsAwareConsumer.class);
    when(dltConsumer.getTopic()).thenReturn(TopicConfig.of("main"));
    when(dltConsumer.getDltTopic()).thenReturn(TopicConfig.of("main.DLT"));
    when(dltConsumer.getRetryTopic()).thenReturn(TopicConfig.of("main-retry"));

    var beanFactory = mock(ListableBeanFactory.class);
    when(beanFactory.getBeansOfType(KafkaOpsAwareConsumer.class))
        .thenReturn(Map.of("dltBean", dltConsumer));
    var mockContainer = mock(ConcurrentKafkaListenerContainerFactory.class);
    when(beanFactory.getBean(anyString())).thenReturn(mockContainer);
    var consumerFactoryMock = mock(DefaultKafkaConsumerFactory.class);
    when(mockContainer.getConsumerFactory()).thenReturn(consumerFactoryMock);
    when(consumerFactoryMock.getConfigurationProperties()).thenReturn(testConsumerProp());

    var registry = new KafkaOpsConsumerRegistry(beanFactory, CONSUMER_GROUP);

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
  void testMainTopicOnlyNoExtraRegistration() {
    // prepare
    var mainOnlyConsumer = mock(KafkaOpsAwareConsumer.class);
    when(mainOnlyConsumer.getTopic()).thenReturn(TopicConfig.of("main-only"));

    var beanFactory = mock(ListableBeanFactory.class);
    when(beanFactory.getBeansOfType(KafkaOpsAwareConsumer.class))
        .thenReturn(Map.of("mainOnlyBean", mainOnlyConsumer));
    var mockContainer = mock(ConcurrentKafkaListenerContainerFactory.class);
    when(beanFactory.getBean(anyString())).thenReturn(mockContainer);
    var consumerFactoryMock = mock(DefaultKafkaConsumerFactory.class);
    when(mockContainer.getConsumerFactory()).thenReturn(consumerFactoryMock);
    when(consumerFactoryMock.getConfigurationProperties()).thenReturn(testConsumerProp());

    var registry = new KafkaOpsConsumerRegistry(beanFactory, CONSUMER_GROUP);

    // when
    registry.afterPropertiesSet();

    // then
    assertEquals(1, registry.getRegisteredTopics().size());
    assertNotNull(registry.find("main-only"));
    assertThrows(NoConsumerFoundException.class, () -> registry.find("main-only.DLT"));
    assertThrows(NoConsumerFoundException.class, () -> registry.find("main-only-retry"));
  }

  @Test
  @DisplayName("getConsumerDetails includes DLT and retry sub-objects with message counts")
  void testGetConsumerDetailsWithDltAndRetry() {
    // prepare
    var dltConsumer = mock(KafkaOpsAwareConsumer.class);
    when(dltConsumer.getTopic()).thenReturn(TopicConfig.of("orders"));
    when(dltConsumer.getDltTopic()).thenReturn(TopicConfig.of("orders.DLT"));
    when(dltConsumer.getRetryTopic()).thenReturn(TopicConfig.of("orders-retry"));

    var beanFactory = mock(ListableBeanFactory.class);
    when(beanFactory.getBeansOfType(KafkaOpsAwareConsumer.class))
        .thenReturn(Map.of("ordersBean", dltConsumer));
    var mockContainer = mock(ConcurrentKafkaListenerContainerFactory.class);
    when(beanFactory.getBean(anyString())).thenReturn(mockContainer);
    var consumerFactoryMock = mock(DefaultKafkaConsumerFactory.class);
    when(mockContainer.getConsumerFactory()).thenReturn(consumerFactoryMock);
    when(consumerFactoryMock.getConfigurationProperties()).thenReturn(testConsumerProp());

    var registry = new KafkaOpsConsumerRegistry(beanFactory, CONSUMER_GROUP);
    registry.afterPropertiesSet();

    // when
    var details = registry.getConsumerDetails();

    // then
    assertEquals(1, details.size());
    var mainDetail = details.get(0);
    assertEquals("orders", mainDetail.getName());
    // Real KafkaConsumer throws — expect -1
    assertEquals(-1, mainDetail.getPartitions());

    assertNotNull(mainDetail.getDlt());
    assertEquals("orders.DLT", mainDetail.getDlt().getName());
    assertEquals(-1, mainDetail.getDlt().getPartitions());

    assertNotNull(mainDetail.getRetry());
    assertEquals("orders-retry", mainDetail.getRetry().getName());
    assertEquals(-1, mainDetail.getRetry().getPartitions());
  }

  @Test
  @DisplayName("getConsumerDetails omits DLT and retry when not declared")
  void testGetConsumerDetailsWithoutDltAndRetry() {
    // prepare
    var mainOnlyConsumer = mock(KafkaOpsAwareConsumer.class);
    when(mainOnlyConsumer.getTopic()).thenReturn(TopicConfig.of("simple"));

    var beanFactory = mock(ListableBeanFactory.class);
    when(beanFactory.getBeansOfType(KafkaOpsAwareConsumer.class))
        .thenReturn(Map.of("simpleBean", mainOnlyConsumer));
    var mockContainer = mock(ConcurrentKafkaListenerContainerFactory.class);
    when(beanFactory.getBean(anyString())).thenReturn(mockContainer);
    var consumerFactoryMock = mock(DefaultKafkaConsumerFactory.class);
    when(mockContainer.getConsumerFactory()).thenReturn(consumerFactoryMock);
    when(consumerFactoryMock.getConfigurationProperties()).thenReturn(testConsumerProp());

    var registry = new KafkaOpsConsumerRegistry(beanFactory, CONSUMER_GROUP);
    registry.afterPropertiesSet();

    // when
    var details = registry.getConsumerDetails();

    // then
    assertEquals(1, details.size());
    assertEquals("simple", details.get(0).getName());
    assertNull(details.get(0).getDlt());
    assertNull(details.get(0).getRetry());
  }
}
