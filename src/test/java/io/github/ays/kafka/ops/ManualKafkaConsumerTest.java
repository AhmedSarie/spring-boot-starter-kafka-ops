package io.github.ays.kafka.ops;

import static java.util.Collections.emptyMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyMap;
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
import org.apache.kafka.clients.consumer.OffsetAndTimestamp;
import org.apache.kafka.common.PartitionInfo;
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

  @Test
  @SneakyThrows
  @DisplayName("Should poll batch of records by offset")
  void testPollBatch() {
    // prepare
    var manualKafkaConsumer = new ManualKafkaConsumer(Duration.ofSeconds(2));
    var emptyRecords = new ConsumerRecords<>(emptyMap());
    when(kafkaConsumer.poll(ArgumentMatchers.any(Duration.class)))
        .thenReturn(msg).thenReturn(emptyRecords);

    // when
    var records = manualKafkaConsumer.pollBatch(TOPIC_NAME, 0, 0L, 10, kafkaConsumer);

    // then
    assertEquals(1, records.size());
    assertEquals("msg", ((ConsumerRecord) records.get(0)).value());
    verify(kafkaConsumer).assign(anyCollection());
  }

  @Test
  @SneakyThrows
  @DisplayName("Should return empty list when no records found in batch poll")
  void testPollBatchEmpty() {
    // prepare
    var manualKafkaConsumer = new ManualKafkaConsumer(Duration.ofMillis(50));
    when(kafkaConsumer.poll(ArgumentMatchers.any(Duration.class))).thenReturn(new ConsumerRecords<>(emptyMap()));

    // when
    var records = manualKafkaConsumer.pollBatch(TOPIC_NAME, 0, 0L, 10, kafkaConsumer);

    // then
    assertTrue(records.isEmpty());
  }

  @Test
  @SneakyThrows
  @DisplayName("Should poll batch by timestamp")
  void testPollBatchByTimestamp() {
    // prepare
    var manualKafkaConsumer = new ManualKafkaConsumer(Duration.ofSeconds(2));
    var tp0 = new TopicPartition(TOPIC_NAME, 0);
    var partitionInfo = mock(PartitionInfo.class);
    when(partitionInfo.partition()).thenReturn(0);
    when(kafkaConsumer.partitionsFor(TOPIC_NAME)).thenReturn(List.of(partitionInfo));
    when(kafkaConsumer.offsetsForTimes(anyMap()))
        .thenReturn(Map.of(tp0, new OffsetAndTimestamp(0L, 1000L)));
    var emptyRecords = new ConsumerRecords<>(emptyMap());
    when(kafkaConsumer.poll(ArgumentMatchers.any(Duration.class)))
        .thenReturn(msg).thenReturn(emptyRecords);

    // when
    var records = manualKafkaConsumer.pollBatchByTimestamp(TOPIC_NAME, 1000L, 10, kafkaConsumer);

    // then
    assertEquals(1, records.size());
    assertEquals("msg", ((ConsumerRecord) records.get(0)).value());
  }

  @Test
  @SneakyThrows
  @DisplayName("Should respect limit in batch poll")
  void testPollBatchRespectsLimit() {
    // prepare
    var manualKafkaConsumer = new ManualKafkaConsumer(Duration.ofSeconds(2));
    var tp = new TopicPartition(TOPIC_NAME, 0);
    var records1 = List.of(
        new ConsumerRecord<>(TOPIC_NAME, 0, 0L, "key1", (Object) "val1"),
        new ConsumerRecord<>(TOPIC_NAME, 0, 1L, "key2", (Object) "val2"),
        new ConsumerRecord<>(TOPIC_NAME, 0, 2L, "key3", (Object) "val3")
    );
    var consumerRecords = new ConsumerRecords<>(Map.of(tp, records1));
    when(kafkaConsumer.poll(ArgumentMatchers.any(Duration.class))).thenReturn(consumerRecords);

    // when
    var result = manualKafkaConsumer.pollBatch(TOPIC_NAME, 0, 0L, 2, kafkaConsumer);

    // then
    assertEquals(2, result.size());
    assertEquals("val1", ((ConsumerRecord) result.get(0)).value());
    assertEquals("val2", ((ConsumerRecord) result.get(1)).value());
  }

  private ConsumerRecords<String, Object> createConsumerRecords(Object value) {
    var topicPartition = new TopicPartition(TOPIC_NAME, 0);
    var consumerRecords = List.of(new ConsumerRecord<>(TOPIC_NAME, 0, 0L, "123", value));
    return new ConsumerRecords<>(Map.of(topicPartition, consumerRecords));
  }
}
