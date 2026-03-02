package io.github.ahmedsarie.kafka.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.ahmedsarie.kafka.ops.avro.TestRecord;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class KafkaOpsServiceTest {

  static String topic = "ops-service-test";
  KafkaOpsConsumerRegistry registry = mock(KafkaOpsConsumerRegistry.class);
  ManualKafkaConsumer manualKafkaConsumerMock = mock(ManualKafkaConsumer.class);
  KafkaOpsService service = new KafkaOpsService(registry, manualKafkaConsumerMock, 100);
  KafkaOpsAwareConsumer contractMock = mock(KafkaOpsAwareConsumer.class);

  private final KafkaConsumer kafkaConsumer = mock(KafkaConsumer.class);

  Map.Entry entry = Map.entry(contractMock, kafkaConsumer);

  @BeforeEach
  public void beforeEach() {
    reset(contractMock);
  }

  @Test
  @DisplayName("should return registered topics from registry")
  void shouldReturnRegisteredTopics() {
    // prepare
    when(registry.getRegisteredTopics()).thenReturn(java.util.Set.of("topic-a", "topic-b"));

    // when
    var topics = service.getRegisteredTopics();

    // then
    assertEquals(java.util.Set.of("topic-a", "topic-b"), topics);
    verify(registry).getRegisteredTopics();
  }

  @Test
  @DisplayName("should return consumer details from registry")
  void shouldReturnConsumerDetails() {
    // prepare
    var details = List.of(
        new KafkaOpsConsumerInfo("topic-a", 3, 1500),
        new KafkaOpsConsumerInfo("topic-b", 6, 3000)
    );
    when(registry.getConsumerDetails()).thenReturn(details);

    // when
    var result = service.getConsumerDetails();

    // then
    assertEquals(2, result.size());
    assertEquals("topic-a", result.get(0).getName());
    assertEquals(3, result.get(0).getPartitions());
    assertEquals(1500, result.get(0).getMessageCount());
    verify(registry).getConsumerDetails();
  }

  @Test
  @DisplayName("retry should succeed when consumer record is found")
  void testRetryHappyScenario() {
    // prepare
    when(contractMock.getTopicName()).thenReturn(topic);
    when(registry.find(topic)).thenReturn(entry);
    var consumerRecordMock = mock(ConsumerRecord.class);
    when(manualKafkaConsumerMock.poll(eq(topic), eq(0), eq(0L), any())).thenReturn(Optional.of(consumerRecordMock));

    // when
    service.retry(new KafkaOpsRequest(topic, 0, 0L));

    // then
    verify(contractMock).consume(consumerRecordMock);
  }

  @Test
  @DisplayName("process should succeed when a String consumer is re-processed for corrections")
  void testProcessWithStringTopicConsumerHappyScenario() {
    // prepare
    var avroJsonMsg = "{\"name\":\"junit\",\"desc\":\"serialise!\"}";
    when(contractMock.getTopicName()).thenReturn(topic);
    when(registry.find(topic)).thenReturn(entry);

    // when
    service.process(topic, avroJsonMsg);

    // then
    verify(contractMock).consume(argThat(arg -> arg.value() == avroJsonMsg));
  }

  @Test
  @DisplayName("process should succeed when an Avro consumer is re-processed for corrections")
  void testProcessWithAvroTopicConsumerHappyScenario() {
    // prepare
    var avroJsonMsg = "{\"name\":\"junit\",\"desc\":\"serialise!\"}";
    when(contractMock.getTopicName()).thenReturn(topic);
    when(contractMock.getSchema()).thenReturn(TestRecord.getClassSchema());
    when(registry.find(topic)).thenReturn(entry);

    // when
    service.process(topic, avroJsonMsg);

    // then
    verify(contractMock).consume(argThat(arg -> {
      boolean isTestRecord = arg.value() instanceof TestRecord;
      var value = (TestRecord) arg.value();
      boolean isNameEquals = value.getName().equals("junit");
      boolean isDescEquals = value.getDesc().equals("serialise!");
      return isTestRecord && isNameEquals && isDescEquals;
    }));
  }

  @Test
  @DisplayName("process should throw when a consumer fails to re-process for corrections")
  void testProcessWithStringTopicConsumerFailure() {
    // prepare
    var avroJsonMsg = "{\"name\":\"junit\",\"desc\":\"serialise!\"}";
    when(contractMock.getTopicName()).thenReturn(topic);
    doThrow(RuntimeException.class).when(contractMock).consume(any());
    when(registry.find(topic)).thenReturn(entry);

    // when / then
    assertThrows(RuntimeException.class, () -> service.process(topic, avroJsonMsg));
  }

  @Test
  @DisplayName("retry should log warning when consumer record is not found")
  void testRetryFailedScenario() {
    // prepare
    when(contractMock.getTopicName()).thenReturn(topic);
    when(registry.find(topic)).thenReturn(entry);
    when(manualKafkaConsumerMock.poll(eq(topic), eq(0), eq(0L), any())).thenReturn(Optional.empty());
    var retryRequest = new KafkaOpsRequest(topic, 0, 0L);

    // when
    service.retry(retryRequest);

    // then - no exception, consumer.consume() is never called
    verify(contractMock, org.mockito.Mockito.never()).consume(any());
  }

  @ParameterizedTest(name = "Poll succeeds for {2} message type consumers")
  @MethodSource("messages")
  void testPollHappyScenario(Object value, String result, String name) {
    // prepare
    int partition = 0;
    long offset = 0L;
    when(contractMock.getTopicName()).thenReturn(topic);
    when(registry.find(topic)).thenReturn(entry);
    var msgKey = "anything";
    var headers = new RecordHeaders();
    headers.add("traceid", "abc-123".getBytes(StandardCharsets.UTF_8));
    var consumerRecord = new ConsumerRecord<>(topic, partition, offset, ConsumerRecord.NO_TIMESTAMP,
        null, 0, 0, msgKey, value, headers, Optional.empty());
    when(manualKafkaConsumerMock.poll(eq(topic), eq(partition), eq(offset), any())).thenReturn(
        Optional.of(consumerRecord));

    // when
    KafkaPollResponse poll = service.poll(topic, partition, offset);

    // then
    assertNotNull(poll);
    assertEquals(result, poll.getConsumerRecordValue());
    assertEquals("anything", poll.getKey());
    assertEquals(0, poll.getPartition());
    assertEquals(0L, poll.getOffset());
    assertEquals("abc-123", poll.getHeaders().get("traceid"));
  }

  @Test
  @DisplayName("should return null when ConsumerRecord is empty")
  void testPollNullScenario() {
    // prepare
    int partition = 0;
    long offset = 0L;
    when(contractMock.getTopicName()).thenReturn(topic);
    when(registry.find(topic)).thenReturn(entry);
    when(manualKafkaConsumerMock.poll(eq(topic), eq(partition), eq(offset), any())).thenReturn(Optional.empty());

    // when
    KafkaPollResponse poll = service.poll(topic, partition, offset);

    // then
    assertNull(poll);
  }

  @Test
  @DisplayName("batchPoll should delegate to ManualKafkaConsumer pollBatch with offset")
  void testBatchPollByOffset() {
    // prepare
    when(registry.find(topic)).thenReturn(entry);
    var headers = new RecordHeaders();
    headers.add("h1", "v1".getBytes(StandardCharsets.UTF_8));
    var record = new ConsumerRecord<>(topic, 0, 5L, ConsumerRecord.NO_TIMESTAMP,
        null, 0, 0, "key1", (Object) "value1", headers, Optional.empty());
    when(manualKafkaConsumerMock.pollBatch(eq(topic), eq(0), eq(5L), eq(10), any()))
        .thenReturn(List.of(record));

    // when
    var result = service.batchPoll(topic, 0, 5L, null, 10);

    // then
    assertNotNull(result);
    assertEquals(1, result.getRecords().size());
    var batchRecord = result.getRecords().get(0);
    assertEquals(0, batchRecord.getPartition());
    assertEquals(5L, batchRecord.getOffset());
    assertEquals("key1", batchRecord.getKey());
    assertEquals("value1", batchRecord.getValue());
    assertEquals("v1", batchRecord.getHeaders().get("h1"));
    assertTrue(!result.isHasMore());
  }

  @Test
  @DisplayName("batchPoll should delegate to ManualKafkaConsumer pollBatchByTimestamp when timestamp provided")
  void testBatchPollByTimestamp() {
    // prepare
    when(registry.find(topic)).thenReturn(entry);
    var record = new ConsumerRecord<>(topic, 0, 10L, "key2", (Object) "value2");
    when(manualKafkaConsumerMock.pollBatchByTimestamp(eq(topic), eq(1000L), eq(5), any()))
        .thenReturn(List.of(record));

    // when
    var result = service.batchPoll(topic, null, null, 1000L, 5);

    // then
    assertNotNull(result);
    assertEquals(1, result.getRecords().size());
    assertEquals("value2", result.getRecords().get(0).getValue());
  }

  @Test
  @DisplayName("batchPoll should cap limit to batchMaxLimit")
  void testBatchPollCapsLimit() {
    // prepare
    var serviceCapped = new KafkaOpsService(registry, manualKafkaConsumerMock, 50);
    when(registry.find(topic)).thenReturn(entry);
    when(manualKafkaConsumerMock.pollBatch(eq(topic), eq(0), eq(0L), eq(50), any()))
        .thenReturn(List.of());

    // when
    var result = serviceCapped.batchPoll(topic, 0, 0L, null, 200);

    // then
    assertNotNull(result);
    verify(manualKafkaConsumerMock).pollBatch(eq(topic), eq(0), eq(0L), eq(50), any());
  }

  private static Stream<Arguments> messages() {
    var msg = "msg";
    var avroRecord = getAvroRecord();
    var javaPojo = new TestPojo(1, "test");
    var avroRecordJsonResult = "{\"name\":\"junit\",\"desc\":\"serialise!\"}";
    var javaPojoJsonResult = "{\n" + "  \"id\" : 1,\n" + "  \"name\" : \"test\"\n" + "}";

    return Stream.of(Arguments.of(msg, msg, "String"), Arguments.of(avroRecord, avroRecordJsonResult, "Avro"),
        Arguments.of(javaPojo, javaPojoJsonResult, "Pojo")
    );
  }

  private static TestRecord getAvroRecord() {
    return TestRecord.newBuilder().setName("junit").setDesc("serialise!").build();
  }

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  static class TestPojo {

    int id;
    String name;
  }
}
