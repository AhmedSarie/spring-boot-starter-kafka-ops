package io.github.ahmedsarie.kafka.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.ahmedsarie.kafka.ops.avro.TestRecord;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
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
  KafkaOpsService service = new KafkaOpsService(registry, manualKafkaConsumerMock);
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
    when(registry.getRegisteredTopics()).thenReturn(Set.of("topic-a", "topic-b"));

    // when
    var topics = service.getRegisteredTopics();

    // then
    assertEquals(Set.of("topic-a", "topic-b"), topics);
    verify(registry).getRegisteredTopics();
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
    var consumerRecord = new ConsumerRecord<>(topic, partition, offset, msgKey, value);
    when(manualKafkaConsumerMock.poll(eq(topic), eq(partition), eq(offset), any())).thenReturn(
        Optional.of(consumerRecord));

    // when
    String poll = service.poll(topic, partition, offset);

    // then
    assertEquals(result, poll);
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
    String poll = service.poll(topic, partition, offset);

    // then
    assertNull(poll);
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
