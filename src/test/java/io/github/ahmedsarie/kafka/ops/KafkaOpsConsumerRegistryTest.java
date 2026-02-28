package io.github.ahmedsarie.kafka.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
}
