package io.github.ays.kafka.ops;

import static java.lang.String.format;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;

@Slf4j
public class KafkaOpsService {

  private static final String DLT_STACKTRACE_HEADER = "kafka_dlt-exception-stacktrace";
  private static final Set<String> BIG_ENDIAN_LONG_HEADERS = Set.of(
      "kafka_dlt-original-offset", "kafka_dlt-original-timestamp");
  private static final Set<String> BIG_ENDIAN_INT_HEADERS = Set.of(
      "kafka_dlt-original-partition");

  private final KafkaOpsConsumerRegistry registry;
  private final ManualKafkaConsumer manualKafkaConsumer;
  private final int batchMaxLimit;

  KafkaOpsService(KafkaOpsConsumerRegistry registry,
                  ManualKafkaConsumer manualKafkaConsumer,
                  int batchMaxLimit) {
    this.registry = registry;
    this.manualKafkaConsumer = manualKafkaConsumer;
    this.batchMaxLimit = batchMaxLimit;
  }

  public Set<String> getRegisteredTopics() {
    return registry.getRegisteredTopics();
  }

  public List<KafkaOpsConsumerInfo> getConsumerDetails() {
    return registry.getConsumerDetails();
  }

  @SuppressWarnings("unchecked")
  public void process(String topic, String key, String value) {
    var entry = this.registry.find(topic);
    var consumer = entry.getConsumer();
    var valueCodec = entry.getValueCodec();
    var keyCodec = entry.getKeyCodec();
    var correctionTopic = topic + "-correction";
    var deserializedKey = key != null ? keyCodec.fromJson(key) : key;
    var deserializedValue = valueCodec.fromJson(value);
    consumer.consume(new ConsumerRecord(correctionTopic, 0, 0, deserializedKey, deserializedValue));
  }

  public void retry(KafkaOpsRequest kafkaOpsRequest) {
    var topic = kafkaOpsRequest.getTopic();
    var partition = kafkaOpsRequest.getPartition();
    var offset = kafkaOpsRequest.getOffset();

    var entry = this.registry.find(topic);
    var consumer = entry.getConsumer();
    Optional<ConsumerRecord> consumerRecord = manualKafkaConsumer.poll(topic, partition, offset, entry.getKafkaConsumer());

    consumerRecord.ifPresentOrElse(cr -> {
      log.info("polled consumer record successfully. reprocess!");
      consumer.consume(cr);
    }, () -> log.warn(format("empty records in topic = %s, partition = %d, offset = %d", topic, partition, offset)));
  }

  public Optional<KafkaPollResponse> poll(String topic, int partition, long offset) {
    var entry = this.registry.find(topic);
    var keyCodec = entry.getKeyCodec();
    var valueCodec = entry.getValueCodec();
    Optional<ConsumerRecord> consumerRecord = manualKafkaConsumer.poll(topic, partition, offset, entry.getKafkaConsumer());
    return consumerRecord.map(cr -> toKafkaPollResponse(cr, keyCodec, valueCodec));
  }

  public KafkaOpsBatchResponse batchPoll(String topicName, Integer partition, Long startOffset,
                                         Long startTimestamp, int limit) {
    var entry = this.registry.find(topicName);
    var keyCodec = entry.getKeyCodec();
    var valueCodec = entry.getValueCodec();
    var cappedLimit = Math.min(limit, batchMaxLimit);

    List<ConsumerRecord> records;
    if (startTimestamp != null) {
      records = manualKafkaConsumer.pollBatchByTimestamp(topicName, startTimestamp, cappedLimit, entry.getKafkaConsumer());
    } else {
      records = manualKafkaConsumer.pollBatch(topicName, partition, startOffset, cappedLimit, entry.getKafkaConsumer());
    }

    var batchRecords = records.stream()
        .map(r -> toBatchRecord(r, keyCodec, valueCodec))
        .toList();

    var hasMore = batchRecords.size() >= cappedLimit;
    return new KafkaOpsBatchResponse(batchRecords, hasMore);
  }

  @SuppressWarnings("unchecked")
  private KafkaOpsBatchResponse.KafkaOpsBatchRecord toBatchRecord(ConsumerRecord record,
                                                                   MessageCodec keyCodec, MessageCodec valueCodec) {
    return new KafkaOpsBatchResponse.KafkaOpsBatchRecord(
        record.partition(),
        record.offset(),
        record.timestamp(),
        keyCodec.toJson(record.key()),
        valueCodec.toJson(record.value()),
        extractHeaders(record)
    );
  }

  @SuppressWarnings("unchecked")
  private KafkaPollResponse toKafkaPollResponse(ConsumerRecord consumerRecord,
                                                 MessageCodec keyCodec, MessageCodec valueCodec) {
    return new KafkaPollResponse(
        valueCodec.toJson(consumerRecord.value()),
        keyCodec.toJson(consumerRecord.key()),
        consumerRecord.partition(),
        consumerRecord.offset(),
        consumerRecord.timestamp(),
        extractHeaders(consumerRecord)
    );
  }

  private Map<String, String> extractHeaders(ConsumerRecord<String, ?> consumerRecord) {
    var headers = new HashMap<String, String>();
    for (Header header : consumerRecord.headers()) {
      if (DLT_STACKTRACE_HEADER.equals(header.key())) {
        continue;
      }
      String value;
      if (header.value() == null) {
        value = null;
      } else if (BIG_ENDIAN_LONG_HEADERS.contains(header.key()) && header.value().length == 8) {
        value = String.valueOf(ByteBuffer.wrap(header.value()).getLong());
      } else if (BIG_ENDIAN_INT_HEADERS.contains(header.key()) && header.value().length == 4) {
        value = String.valueOf(ByteBuffer.wrap(header.value()).getInt());
      } else {
        value = new String(header.value(), StandardCharsets.UTF_8);
      }
      headers.put(header.key(), value);
    }
    return headers;
  }

  public static class NoConsumerFoundException extends RuntimeException {

    public NoConsumerFoundException(String msg) {
      super(msg);
    }
  }
}
