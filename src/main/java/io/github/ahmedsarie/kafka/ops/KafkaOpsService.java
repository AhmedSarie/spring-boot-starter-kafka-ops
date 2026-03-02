package io.github.ahmedsarie.kafka.ops;

import static io.github.ahmedsarie.kafka.ops.AvroUtil.avroToJson;
import static io.github.ahmedsarie.kafka.ops.AvroUtil.jsonToAvro;
import static java.lang.String.format;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.generic.GenericContainer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;

@Slf4j
public class KafkaOpsService {

  private final KafkaOpsConsumerRegistry registry;
  private final ManualKafkaConsumer manualKafkaConsumer;
  private final ObjectMapper mapper;
  private final int batchMaxLimit;

  KafkaOpsService(KafkaOpsConsumerRegistry registry,
                  ManualKafkaConsumer manualKafkaConsumer,
                  int batchMaxLimit) {
    this.registry = registry;
    this.manualKafkaConsumer = manualKafkaConsumer;
    this.mapper = new ObjectMapper();
    this.batchMaxLimit = batchMaxLimit;
  }

  public Set<String> getRegisteredTopics() {
    return registry.getRegisteredTopics();
  }

  public List<KafkaOpsConsumerInfo> getConsumerDetails() {
    return registry.getConsumerDetails();
  }

  public void process(String topic, String payload) {
    var entry = this.registry.find(topic);
    var avroSchema = entry.getKey().getSchema();
    var correctionTopic = topic + "-correction";
    if (avroSchema != null) {
      var specificRecord = jsonToAvro(payload, avroSchema);
      entry.getKey().consume(new ConsumerRecord(correctionTopic, 0, 0, null, specificRecord));
    } else {
      entry.getKey().consume(new ConsumerRecord(correctionTopic, 0, 0, null, payload));
    }
  }

  public void retry(KafkaOpsRequest kafkaOpsRequest) {
    var topic = kafkaOpsRequest.getTopic();
    var partition = kafkaOpsRequest.getPartition();
    var offset = kafkaOpsRequest.getOffset();

    var entry = this.registry.find(topic);
    var consumer = entry.getValue();
    Optional<ConsumerRecord> consumerRecord = manualKafkaConsumer.poll(topic, partition, offset, consumer);
    var registeredKafkaConsumer = entry.getKey();

    consumerRecord.ifPresentOrElse(cr -> {
      log.info("polled consumer record successfully. reprocess!");
      registeredKafkaConsumer.consume(cr);
    }, () -> log.warn(format("empty records in topic = %s, partition = %d, offset = %d", topic, partition, offset)));
  }

  @SneakyThrows
  public KafkaPollResponse poll(String topic, int partition, long offset) {
    var entry = this.registry.find(topic);
    var consumer = entry.getValue();
    Optional<ConsumerRecord> consumerRecord = manualKafkaConsumer.poll(topic, partition, offset, consumer);
    return consumerRecord.map(this::toKafkaPollResponse).orElse(null);
  }

  public KafkaOpsBatchResponse batchPoll(String topicName, Integer partition, Long startOffset,
                                         Long startTimestamp, int limit) {
    var entry = this.registry.find(topicName);
    var consumer = entry.getValue();
    var cappedLimit = Math.min(limit, batchMaxLimit);

    List<ConsumerRecord> records;
    if (startTimestamp != null) {
      records = manualKafkaConsumer.pollBatchByTimestamp(topicName, startTimestamp, cappedLimit, consumer);
    } else {
      records = manualKafkaConsumer.pollBatch(topicName, partition, startOffset, cappedLimit, consumer);
    }

    var batchRecords = records.stream()
        .map(this::toBatchRecord)
        .toList();

    var hasMore = batchRecords.size() >= cappedLimit;
    return new KafkaOpsBatchResponse(batchRecords, hasMore);
  }

  private KafkaOpsBatchResponse.KafkaOpsBatchRecord toBatchRecord(ConsumerRecord<String, ?> record) {
    return new KafkaOpsBatchResponse.KafkaOpsBatchRecord(
        record.partition(),
        record.offset(),
        record.timestamp(),
        record.key(),
        recordValueAsString(record),
        extractHeaders(record)
    );
  }

  private KafkaPollResponse toKafkaPollResponse(ConsumerRecord consumerRecord) {
    var key = consumerRecord.key() != null ? String.valueOf(consumerRecord.key()) : null;
    return new KafkaPollResponse(
        recordValueAsString(consumerRecord),
        key,
        consumerRecord.partition(),
        consumerRecord.offset(),
        consumerRecord.timestamp(),
        extractHeaders(consumerRecord)
    );
  }

  private Map<String, String> extractHeaders(ConsumerRecord<String, ?> consumerRecord) {
    var headers = new HashMap<String, String>();
    for (Header header : consumerRecord.headers()) {
      var value = header.value() != null
          ? new String(header.value(), StandardCharsets.UTF_8)
          : null;
      headers.put(header.key(), value);
    }
    return headers;
  }

  @SneakyThrows
  private String recordValueAsString(ConsumerRecord<String, ?> consumerRecord) {
    Object value = consumerRecord.value();
    if (value instanceof GenericContainer) {
      return avroToJson((GenericContainer) value);
    } else if (value instanceof String) {
      return (String) value;
    } else {
      return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
    }
  }

  public static class NoConsumerFoundException extends RuntimeException {

    public NoConsumerFoundException(String msg) {
      super(msg);
    }
  }
}
