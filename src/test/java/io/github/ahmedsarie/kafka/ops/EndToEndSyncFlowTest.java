package io.github.ahmedsarie.kafka.ops;

import static io.github.ahmedsarie.kafka.ops.EndToEndSyncFlowTest.TEST_TOPIC_NAME;
import static io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG;
import static org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG;
import static org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.kafka.test.utils.KafkaTestUtils.producerProps;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.ahmedsarie.kafka.ops.avro.TestMessageKey;
import io.github.ahmedsarie.kafka.ops.avro.TestRecord;
import io.github.ahmedsarie.kafka.ops.util.LogAssert;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

@EmbeddedKafka(topics = {TEST_TOPIC_NAME})
@ContextConfiguration(classes = {KafkaOpsConfiguration.class,
    KafkaAutoConfiguration.class,
    EndToEndSyncFlowTest.TestConfig.class})
@WebMvcTest(value = {KafkaOpsController.class},
    properties = {"kafka.ops.max-poll-interval-ms=2000",
        "spring.kafka.consumer.key-deserializer=io.confluent.kafka.serializers.KafkaAvroDeserializer",
        "spring.kafka.consumer.value-deserializer=io.confluent.kafka.serializers.KafkaAvroDeserializer",
        "spring.kafka.consumer.properties.schema.registry.url=mock://test_schema_registry_url",
        "spring.kafka.producer.properties.schema.registry.url=mock://test_schema_registry_url",
        "spring.kafka.consumer.properties.specific.avro.reader=true"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EndToEndSyncFlowTest {

  public static final String TEST_TOPIC_NAME = "EndToEndSyncFlowTest-topic";
  final LogAssert logAssert = new LogAssert(KafkaOpsService.class);
  @Autowired
  private MockMvc mockMvc;

  @Autowired
  EmbeddedKafkaBroker embeddedKafka;

  @Autowired
  @Qualifier("endToEndSyncTestBean")
  KafkaOpsAwareConsumer<TestMessageKey, TestRecord> kafkaOpsConsumer;

  @Autowired
  KafkaOpsService service;

  @Autowired
  KafkaTemplate<Object, Object> testKafkaAvroTemplate;

  private static final String RETRY_CONSUMER_API_URI = "/operational/consumer-retries";
  private static final List<ConsumerRecord<TestMessageKey, TestRecord>> consumeResult = new ArrayList();

  @BeforeAll
  void beforeAll() {
    logAssert.start();
  }

  @BeforeEach
  public void beforeEach() {
    logAssert.reset();
  }

  @AfterAll
  public void afterAll() {
    logAssert.close();
  }

  @Test
  @SneakyThrows
  void testEndToEndHappyScenario() {

    // prepare
    var msg = "test message";
    var metadataMap = publishTo_partition0_partition1(msg);

    int counter = 0;
    for (var item : metadataMap.entrySet()) {
      // when
      int sentPartition = item.getValue().partition();
      long sentOffset = item.getValue().offset();
      var content =
          "{\"topic\":\"" + TEST_TOPIC_NAME + "\", \"partition\":" + sentPartition + ", \"offset\":" + sentOffset + "}";
      this.mockMvc.perform(post(RETRY_CONSUMER_API_URI).contentType(MediaType.APPLICATION_JSON).content(content))
          .andDo(print())
          .andExpect(status().isOk())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
          .andExpect(jsonPath("$.id").exists());

      // then
      logAssert.assertLog("polled consumer record successfully. reprocess!", 20);
      var poll = service.poll(TEST_TOPIC_NAME, sentPartition, sentOffset);
      assertNotNull(poll);
      assertTrue(poll.getConsumerRecordValue().contains(consumeResult.get(counter).value().getName()));
      assertTrue(poll.getConsumerRecordValue().contains(consumeResult.get(counter).value().getDesc()));
      counter++;
    }
  }

  private Map<Integer, RecordMetadata> publishTo_partition0_partition1(String msg)
      throws InterruptedException, ExecutionException {

    Map<Integer, RecordMetadata> responses = new LinkedHashMap<>();

    TestMessageKey testMessageKeyWithValue1 = TestMessageKey.newBuilder().setKafkaMessageKey(1).build();
    String msg1 = msg + "_1";
    TestRecord testRecordWithName1 = TestRecord.newBuilder().setName(msg1).setDesc(msg1).build();
    var producerRecordForPartition0 = new ProducerRecord<Object, Object>(
        TEST_TOPIC_NAME, 0, testMessageKeyWithValue1, testRecordWithName1);
    var recordMetadataPartition0 = testKafkaAvroTemplate.send(producerRecordForPartition0).get().getRecordMetadata();
    responses.put(recordMetadataPartition0.partition(), recordMetadataPartition0);

    TestMessageKey testMessageKeyWithValue2 = TestMessageKey.newBuilder().setKafkaMessageKey(2).build();
    String msg2 = msg + "_2";
    TestRecord testRecordWithName2 = TestRecord.newBuilder().setName(msg2).setDesc(msg2).build();
    var producerRecordForPartition1 = new ProducerRecord<Object, Object>(
        TEST_TOPIC_NAME, 1, testMessageKeyWithValue2, testRecordWithName2);
    var recordMetadataPartition1 = testKafkaAvroTemplate.send(producerRecordForPartition1).get().getRecordMetadata();
    responses.put(recordMetadataPartition1.partition(), recordMetadataPartition1);

    return responses;
  }

  @Slf4j
  @TestConfiguration
  static class TestConfig {

    @Bean
    public KafkaOpsAwareConsumer<TestMessageKey, TestRecord> endToEndSyncTestBean() {

      return new KafkaOpsAwareConsumer<>() {
        @Override
        public void consume(ConsumerRecord<TestMessageKey, TestRecord> consumerRecord) {
          log.info("consumed " + consumerRecord.value());
          consumeResult.add(consumerRecord);
        }

        @Override
        public String getTopicName() {
          return TEST_TOPIC_NAME;
        }
      };
    }

    @Bean(name = "testKafkaAvroTemplate")
    public KafkaTemplate<Object, Object> kafkaTemplate(
        @org.springframework.beans.factory.annotation.Value("${spring.kafka.bootstrap-servers}") String brokers) {

      var producerProperties = producerProps(brokers);
      producerProperties.put(KEY_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class.getName());
      producerProperties.put(VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class.getName());
      producerProperties.put(SCHEMA_REGISTRY_URL_CONFIG, "mock://test_schema_registry_url");

      var producerFactory = new DefaultKafkaProducerFactory<>(producerProperties);
      return new KafkaTemplate<>(producerFactory);
    }
  }
}
