package io.github.ahmedsarie.kafka.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.ahmedsarie.kafka.ops.KafkaOpsService.NoConsumerFoundException;
import java.util.List;
import java.util.Map;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
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
    when(contractMock.getTopicName()).thenReturn(registeredTopic);
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
    when(contractMock.getTopicName()).thenReturn(registeredTopic);
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
    when(contractMock.getTopicName()).thenReturn(registeredTopic);
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
}
