package io.github.ahmedsarie.kafka.ops;

import static java.util.Collections.emptyMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.SneakyThrows;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

public class ManualKafkaConsumerTest {

  public final static String TOPIC_NAME = "manual-kafka-consumer-test-topic";

  KafkaConsumer kafkaConsumer;
  ConsumerRecords<String, Object> msg;

  @BeforeEach
  void setUp() {
    kafkaConsumer = mock(KafkaConsumer.class);
    msg = createConsumerRecords("msg");
    when(kafkaConsumer.poll(ArgumentMatchers.any(Duration.class))).thenReturn(msg);
  }

  @Test
  @SneakyThrows
  @DisplayName("Should successfully poll Consumer Records if found")
  void testHappyScenario1() {
    // prepare
    var manualKafkaConsumer = new ManualKafkaConsumer(Duration.ofSeconds(2));

    // when
    Optional<ConsumerRecord<String, ?>> optionalConsumerRecord = manualKafkaConsumer.poll(
        TOPIC_NAME, 0, 0L, kafkaConsumer);

    // then
    assertTrue(optionalConsumerRecord.isPresent());
    verify(kafkaConsumer).assign(anyCollection());
    verify(kafkaConsumer, never()).subscribe(anyCollection());
    verify(kafkaConsumer, never()).unsubscribe();
    assertEquals(msg.iterator().next().value(), optionalConsumerRecord.get().value());
  }

  @Test
  @SneakyThrows
  @DisplayName("Should return empty from the nothing found")
  void testHappyScenario2() {
    // prepare
    var manualKafkaConsumer = new ManualKafkaConsumer(Duration.ofMillis(100));

    when(kafkaConsumer.poll(ArgumentMatchers.any(Duration.class))).thenReturn(new ConsumerRecords<>(emptyMap()));
    // when
    var optionalConsumerRecord = manualKafkaConsumer.poll(TOPIC_NAME, 0, 1, kafkaConsumer);

    // then
    assertTrue(optionalConsumerRecord.isEmpty());
  }

  private ConsumerRecords<String, Object> createConsumerRecords(Object value) {
    var topicPartition = new TopicPartition(TOPIC_NAME, 0);
    var consumerRecords = List.of(new ConsumerRecord<>(TOPIC_NAME, 0, 0L, "123", value));
    return new ConsumerRecords<>(Map.of(topicPartition, consumerRecords));
  }
}
