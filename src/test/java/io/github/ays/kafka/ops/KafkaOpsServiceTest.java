package io.github.ays.kafka.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.ays.kafka.ops.avro.TestRecord;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KafkaOpsServiceTest {

  static String topic = "ops-service-test";
  KafkaOpsConsumerRegistry registry = mock(KafkaOpsConsumerRegistry.class);
  ManualKafkaConsumer manualKafkaConsumerMock = mock(ManualKafkaConsumer.class);
  KafkaOpsService service = new KafkaOpsService(registry, manualKafkaConsumerMock, 100);
  KafkaOpsAwareConsumer contractMock = mock(KafkaOpsAwareConsumer.class);

  private final KafkaConsumer kafkaConsumer = mock(KafkaConsumer.class);

  @BeforeEach
  public void beforeEach() {
    reset(contractMock);
  }

  private RegisteredConsumerEntry entry(MessageCodec keyCodec, MessageCodec valueCodec) {
    return new RegisteredConsumerEntry(contractMock, kafkaConsumer, keyCodec, valueCodec);
  }

  private RegisteredConsumerEntry stringEntry() {
    return entry(new StringMessageCodec(), new StringMessageCodec());
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
    when(registry.find(topic)).thenReturn(stringEntry());
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
    var msg = "{\"name\":\"junit\",\"desc\":\"serialise!\"}";
    when(registry.find(topic)).thenReturn(stringEntry());

    // when
    service.process(topic, null, msg);

    // then
    verify(contractMock).consume(argThat(arg -> arg.value().equals(msg)));
  }

  @Test
  @DisplayName("process should succeed when an Avro consumer uses MessageCodec for corrections")
  void testProcessWithAvroMessageCodecHappyScenario() {
    // prepare
    var avroJsonMsg = "{\"name\":\"junit\",\"desc\":\"serialise!\"}";
    when(registry.find(topic)).thenReturn(entry(new StringMessageCodec(), new AvroMessageCodec<>(TestRecord.getClassSchema())));

    // when
    service.process(topic, null, avroJsonMsg);

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
  @DisplayName("process should succeed when a Proto consumer uses MessageCodec for corrections")
  void testProcessWithProtoMessageCodecHappyScenario() {
    // prepare
    var protoJsonMsg = "{\"name\": \"junit\"}";
    when(registry.find(topic)).thenReturn(entry(new StringMessageCodec(), new ProtoMessageCodec<>(com.google.protobuf.Struct.getDefaultInstance())));

    // when
    service.process(topic, null, protoJsonMsg);

    // then
    verify(contractMock).consume(argThat(arg -> {
      boolean isStruct = arg.value() instanceof com.google.protobuf.Struct;
      var struct = (com.google.protobuf.Struct) arg.value();
      return isStruct && struct.getFieldsMap().containsKey("name");
    }));
  }

  @Test
  @DisplayName("process should throw when a consumer fails to re-process for corrections")
  void testProcessWithStringTopicConsumerFailure() {
    // prepare
    var msg = "{\"name\":\"junit\",\"desc\":\"serialise!\"}";
    doThrow(RuntimeException.class).when(contractMock).consume(any());
    when(registry.find(topic)).thenReturn(stringEntry());

    // when / then
    assertThrows(RuntimeException.class, () -> service.process(topic, null, msg));
  }

  @Test
  @DisplayName("process should pass key through StringMessageCodec when key is provided")
  void testProcessWithKeyPassedThrough() {
    // prepare
    var key = "order-123";
    var payload = "{\"name\":\"junit\",\"desc\":\"serialise!\"}";
    when(registry.find(topic)).thenReturn(stringEntry());

    // when
    service.process(topic, key, payload);

    // then
    verify(contractMock).consume(argThat(arg ->
        "order-123".equals(arg.key()) && arg.value().equals(payload)
    ));
  }

  @Test
  @DisplayName("process should deserialize both key and value when Proto codecs are declared")
  void testProcessWithKeyAndValueCodecs() {
    // prepare
    var keyJson = "{\"name\": \"key-field\"}";
    var valueJson = "{\"name\": \"value-field\"}";
    var protoCodec = new ProtoMessageCodec<>(com.google.protobuf.Struct.getDefaultInstance());
    when(registry.find(topic)).thenReturn(entry(protoCodec, protoCodec));

    // when
    service.process(topic, keyJson, valueJson);

    // then
    verify(contractMock).consume(argThat(arg -> {
      boolean keyIsStruct = arg.key() instanceof com.google.protobuf.Struct;
      boolean valueIsStruct = arg.value() instanceof com.google.protobuf.Struct;
      var keyStruct = (com.google.protobuf.Struct) arg.key();
      var valueStruct = (com.google.protobuf.Struct) arg.value();
      return keyIsStruct && valueIsStruct
          && keyStruct.getFieldsMap().containsKey("name")
          && valueStruct.getFieldsMap().containsKey("name");
    }));
  }

  @Test
  @DisplayName("process should pass null key when no key is provided")
  void testProcessWithNullKey() {
    // prepare
    var payload = "plain-text";
    when(registry.find(topic)).thenReturn(stringEntry());

    // when
    service.process(topic, null, payload);

    // then
    verify(contractMock).consume(argThat(arg -> arg.key() == null && "plain-text".equals(arg.value())));
  }

  @Test
  @DisplayName("retry should log warning when consumer record is not found")
  void testRetryFailedScenario() {
    // prepare
    when(registry.find(topic)).thenReturn(stringEntry());
    when(manualKafkaConsumerMock.poll(eq(topic), eq(0), eq(0L), any())).thenReturn(Optional.empty());
    var retryRequest = new KafkaOpsRequest(topic, 0, 0L);

    // when
    service.retry(retryRequest);

    // then - no exception, consumer.consume() is never called
    verify(contractMock, org.mockito.Mockito.never()).consume(any());
  }

  @Test
  @DisplayName("Poll succeeds for String message type consumers")
  void testPollWithStringCodec() {
    // prepare
    int partition = 0;
    long offset = 0L;
    when(registry.find(topic)).thenReturn(stringEntry());
    var msgKey = "anything";
    var headers = new RecordHeaders();
    headers.add("traceid", "abc-123".getBytes(StandardCharsets.UTF_8));
    var consumerRecord = new ConsumerRecord<>(topic, partition, offset, ConsumerRecord.NO_TIMESTAMP,
        null, 0, 0, msgKey, (Object) "msg", headers, Optional.empty());
    when(manualKafkaConsumerMock.poll(eq(topic), eq(partition), eq(offset), any())).thenReturn(
        Optional.of(consumerRecord));

    // when
    var poll = service.poll(topic, partition, offset);

    // then
    assertTrue(poll.isPresent());
    assertEquals("msg", poll.get().getConsumerRecordValue());
    assertEquals("anything", poll.get().getKey());
    assertEquals(0, poll.get().getPartition());
    assertEquals(0L, poll.get().getOffset());
    assertEquals("abc-123", poll.get().getHeaders().get("traceid"));
  }

  @Test
  @DisplayName("should return empty Optional when ConsumerRecord is empty")
  void testPollEmptyScenario() {
    // prepare
    int partition = 0;
    long offset = 0L;
    when(registry.find(topic)).thenReturn(stringEntry());
    when(manualKafkaConsumerMock.poll(eq(topic), eq(partition), eq(offset), any())).thenReturn(Optional.empty());

    // when
    var poll = service.poll(topic, partition, offset);

    // then
    assertTrue(poll.isEmpty());
  }

  @Test
  @DisplayName("batchPoll should delegate to ManualKafkaConsumer pollBatch with offset")
  void testBatchPollByOffset() {
    // prepare
    when(registry.find(topic)).thenReturn(stringEntry());
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
    when(registry.find(topic)).thenReturn(stringEntry());
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
    when(registry.find(topic)).thenReturn(stringEntry());
    when(manualKafkaConsumerMock.pollBatch(eq(topic), eq(0), eq(0L), eq(50), any()))
        .thenReturn(List.of());

    // when
    var result = serviceCapped.batchPoll(topic, 0, 0L, null, 200);

    // then
    assertNotNull(result);
    verify(manualKafkaConsumerMock).pollBatch(eq(topic), eq(0), eq(0L), eq(50), any());
  }

  @Test
  @DisplayName("extractHeaders should filter out kafka_dlt-exception-stacktrace header")
  void shouldFilterOutDltStacktraceHeader() {
    // prepare
    when(registry.find(topic)).thenReturn(stringEntry());
    var headers = new RecordHeaders();
    headers.add("traceid", "abc".getBytes(StandardCharsets.UTF_8));
    headers.add("kafka_dlt-exception-stacktrace", "java.lang.RuntimeException: boom\n\tat ...".getBytes(StandardCharsets.UTF_8));
    var record = new ConsumerRecord<>(topic, 0, 0L, ConsumerRecord.NO_TIMESTAMP,
        null, 0, 0, "key", (Object) "value", headers, Optional.empty());
    when(manualKafkaConsumerMock.poll(eq(topic), eq(0), eq(0L), any())).thenReturn(Optional.of(record));

    // when
    var poll = service.poll(topic, 0, 0L);

    // then
    assertTrue(poll.isPresent());
    assertEquals("abc", poll.get().getHeaders().get("traceid"));
    assertNull(poll.get().getHeaders().get("kafka_dlt-exception-stacktrace"));
  }

  @Test
  @DisplayName("extractHeaders should decode BigEndian long headers (offset, timestamp)")
  void shouldDecodeBigEndianLongHeaders() {
    // prepare
    when(registry.find(topic)).thenReturn(stringEntry());
    var headers = new RecordHeaders();
    headers.add("kafka_dlt-original-offset", ByteBuffer.allocate(8).putLong(42L).array());
    headers.add("kafka_dlt-original-timestamp", ByteBuffer.allocate(8).putLong(1700000000000L).array());
    var record = new ConsumerRecord<>(topic, 0, 0L, ConsumerRecord.NO_TIMESTAMP,
        null, 0, 0, "key", (Object) "value", headers, Optional.empty());
    when(manualKafkaConsumerMock.poll(eq(topic), eq(0), eq(0L), any())).thenReturn(Optional.of(record));

    // when
    var poll = service.poll(topic, 0, 0L);

    // then
    assertTrue(poll.isPresent());
    assertEquals("42", poll.get().getHeaders().get("kafka_dlt-original-offset"));
    assertEquals("1700000000000", poll.get().getHeaders().get("kafka_dlt-original-timestamp"));
  }

  @Test
  @DisplayName("extractHeaders should decode BigEndian int header (partition)")
  void shouldDecodeBigEndianIntHeader() {
    // prepare
    when(registry.find(topic)).thenReturn(stringEntry());
    var headers = new RecordHeaders();
    headers.add("kafka_dlt-original-partition", ByteBuffer.allocate(4).putInt(3).array());
    var record = new ConsumerRecord<>(topic, 0, 0L, ConsumerRecord.NO_TIMESTAMP,
        null, 0, 0, "key", (Object) "value", headers, Optional.empty());
    when(manualKafkaConsumerMock.poll(eq(topic), eq(0), eq(0L), any())).thenReturn(Optional.of(record));

    // when
    var poll = service.poll(topic, 0, 0L);

    // then
    assertTrue(poll.isPresent());
    assertEquals("3", poll.get().getHeaders().get("kafka_dlt-original-partition"));
  }

  @Test
  @DisplayName("Poll succeeds for Avro message type consumers with AvroMessageCodec")
  void testPollWithAvroMessageCodec() {
    // prepare
    var avroRecord = TestRecord.newBuilder().setName("junit").setDesc("serialise!").build();
    var codec = new AvroMessageCodec<>(TestRecord.getClassSchema());
    when(registry.find(topic)).thenReturn(entry(new StringMessageCodec(), codec));
    var consumerRecord = new ConsumerRecord<>(topic, 0, 0L, ConsumerRecord.NO_TIMESTAMP,
        null, 0, 0, "key", (Object) avroRecord, new RecordHeaders(), Optional.empty());
    when(manualKafkaConsumerMock.poll(eq(topic), eq(0), eq(0L), any())).thenReturn(Optional.of(consumerRecord));

    // when
    var poll = service.poll(topic, 0, 0L);

    // then
    assertTrue(poll.isPresent());
    assertEquals("{\"name\":\"junit\",\"desc\":\"serialise!\"}", poll.get().getConsumerRecordValue());
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  @Test
  @DisplayName("Poll uses Avro key codec when declared")
  void testPollUsesAvroKeyCodec() {
    // prepare
    var avroKey = TestRecord.newBuilder().setName("key-name").setDesc("key-desc").build();
    var keyCodec = new AvroMessageCodec<>(TestRecord.getClassSchema());
    when(registry.find(topic)).thenReturn(entry(keyCodec, new StringMessageCodec()));
    ConsumerRecord consumerRecord = new ConsumerRecord(topic, 0, 0L, avroKey, "string-value");
    when(manualKafkaConsumerMock.poll(eq(topic), eq(0), eq(0L), any())).thenReturn(Optional.of(consumerRecord));

    // when
    var poll = service.poll(topic, 0, 0L);

    // then
    assertTrue(poll.isPresent());
    assertEquals("{\"name\":\"key-name\",\"desc\":\"key-desc\"}", poll.get().getKey());
  }

  @Test
  @DisplayName("process should succeed when a JSON consumer uses JsonMessageCodec for corrections")
  void testProcessWithJsonMessageCodecHappyScenario() {
    // prepare
    var jsonMsg = "{\"id\":1,\"name\":\"junit\"}";
    when(registry.find(topic)).thenReturn(entry(new StringMessageCodec(), new JsonMessageCodec<>(TestPojo.class)));

    // when
    service.process(topic, null, jsonMsg);

    // then
    verify(contractMock).consume(argThat(arg -> {
      boolean isPojo = arg.value() instanceof TestPojo;
      var value = (TestPojo) arg.value();
      return isPojo && value.getId() == 1 && "junit".equals(value.getName());
    }));
  }

  @Test
  @DisplayName("process should deserialize both JSON key and JSON value when JsonMessageCodec is declared")
  void testProcessWithJsonKeyAndValueCodecs() {
    // prepare
    var keyJson = "{\"id\":99,\"name\":\"key-pojo\"}";
    var valueJson = "{\"id\":1,\"name\":\"value-pojo\"}";
    when(registry.find(topic)).thenReturn(entry(new JsonMessageCodec<>(TestPojo.class), new JsonMessageCodec<>(TestPojo.class)));

    // when
    service.process(topic, keyJson, valueJson);

    // then
    verify(contractMock).consume(argThat(arg -> {
      var key = (TestPojo) arg.key();
      var value = (TestPojo) arg.value();
      return key.getId() == 99 && "key-pojo".equals(key.getName())
          && value.getId() == 1 && "value-pojo".equals(value.getName());
    }));
  }

  @Test
  @DisplayName("Poll succeeds for JSON message type consumers with JsonMessageCodec")
  void testPollWithJsonMessageCodec() {
    // prepare
    var pojo = new TestPojo(1, "junit");
    var codec = new JsonMessageCodec<>(TestPojo.class);
    when(registry.find(topic)).thenReturn(entry(new StringMessageCodec(), codec));
    var consumerRecord = new ConsumerRecord<>(topic, 0, 0L, ConsumerRecord.NO_TIMESTAMP,
        null, 0, 0, "key", (Object) pojo, new RecordHeaders(), Optional.empty());
    when(manualKafkaConsumerMock.poll(eq(topic), eq(0), eq(0L), any())).thenReturn(Optional.of(consumerRecord));

    // when
    var poll = service.poll(topic, 0, 0L);

    // then
    assertTrue(poll.isPresent());
    assertEquals("{\"id\":1,\"name\":\"junit\"}", poll.get().getConsumerRecordValue());
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  @Test
  @DisplayName("Poll uses JSON key codec when declared")
  void testPollUsesJsonKeyCodec() {
    // prepare
    var pojoKey = new TestPojo(42, "key-name");
    var keyCodec = new JsonMessageCodec<>(TestPojo.class);
    when(registry.find(topic)).thenReturn(entry(keyCodec, new StringMessageCodec()));
    ConsumerRecord consumerRecord = new ConsumerRecord(topic, 0, 0L, pojoKey, "string-value");
    when(manualKafkaConsumerMock.poll(eq(topic), eq(0), eq(0L), any())).thenReturn(Optional.of(consumerRecord));

    // when
    var poll = service.poll(topic, 0, 0L);

    // then
    assertTrue(poll.isPresent());
    assertEquals("{\"id\":42,\"name\":\"key-name\"}", poll.get().getKey());
  }

  @Test
  @DisplayName("Poll succeeds for Proto message type consumers with ProtoMessageCodec")
  void testPollWithProtoMessageCodec() {
    // prepare
    var struct = com.google.protobuf.Struct.newBuilder()
        .putFields("name", com.google.protobuf.Value.newBuilder().setStringValue("junit").build())
        .build();
    var codec = new ProtoMessageCodec<>(com.google.protobuf.Struct.getDefaultInstance());
    when(registry.find(topic)).thenReturn(entry(new StringMessageCodec(), codec));
    var consumerRecord = new ConsumerRecord<>(topic, 0, 0L, ConsumerRecord.NO_TIMESTAMP,
        null, 0, 0, "key", (Object) struct, new RecordHeaders(), Optional.empty());
    when(manualKafkaConsumerMock.poll(eq(topic), eq(0), eq(0L), any())).thenReturn(Optional.of(consumerRecord));

    // when
    var poll = service.poll(topic, 0, 0L);

    // then
    assertTrue(poll.isPresent());
    assertTrue(poll.get().getConsumerRecordValue().contains("\"name\""));
    assertTrue(poll.get().getConsumerRecordValue().contains("junit"));
  }

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  static class TestPojo {

    int id;
    String name;
  }
}
