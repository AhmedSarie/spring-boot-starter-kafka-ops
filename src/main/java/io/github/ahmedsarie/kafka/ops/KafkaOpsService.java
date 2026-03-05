package io.github.ahmedsarie.kafka.ops;

import static java.lang.String.format;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.ByteBuffer;
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

  private static final String DLT_STACKTRACE_HEADER = "kafka_dlt-exception-stacktrace";
  private static final Set<String> BIG_ENDIAN_LONG_HEADERS = Set.of(
      "kafka_dlt-original-offset", "kafka_dlt-original-timestamp");
  private static final Set<String> BIG_ENDIAN_INT_HEADERS = Set.of(
      "kafka_dlt-original-partition");

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

  @SuppressWarnings({"unchecked", "deprecation"})
  public void process(String topic, String payload) {
    var entry = this.registry.find(topic);
    var consumer = entry.getKey();
    var codec = resolveCodec(consumer);
    var correctionTopic = topic + "-correction";
    if (codec != null) {
      consumer.consume(new ConsumerRecord(correctionTopic, 0, 0, null, codec.fromJson(payload)));
    } else {
      consumer.consume(new ConsumerRecord(correctionTopic, 0, 0, null, payload));
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

  @SuppressWarnings("deprecation")
  @SneakyThrows
  public Optional<KafkaPollResponse> poll(String topic, int partition, long offset) {
    var entry = this.registry.find(topic);
    var consumer = entry.getValue();
    var codec = resolveCodec(entry.getKey());
    Optional<ConsumerRecord> consumerRecord = manualKafkaConsumer.poll(topic, partition, offset, consumer);
    return consumerRecord.map(cr -> toKafkaPollResponse(cr, codec));
  }

  @SuppressWarnings("deprecation")
  public KafkaOpsBatchResponse batchPoll(String topicName, Integer partition, Long startOffset,
                                         Long startTimestamp, int limit) {
    var entry = this.registry.find(topicName);
    var consumer = entry.getValue();
    var codec = resolveCodec(entry.getKey());
    var cappedLimit = Math.min(limit, batchMaxLimit);

    List<ConsumerRecord> records;
    if (startTimestamp != null) {
      records = manualKafkaConsumer.pollBatchByTimestamp(topicName, startTimestamp, cappedLimit, consumer);
    } else {
      records = manualKafkaConsumer.pollBatch(topicName, partition, startOffset, cappedLimit, consumer);
    }

    var batchRecords = records.stream()
        .map(r -> toBatchRecord(r, codec))
        .toList();

    var hasMore = batchRecords.size() >= cappedLimit;
    return new KafkaOpsBatchResponse(batchRecords, hasMore);
  }

  private KafkaOpsBatchResponse.KafkaOpsBatchRecord toBatchRecord(ConsumerRecord<String, ?> record, ValueCodec codec) {
    return new KafkaOpsBatchResponse.KafkaOpsBatchRecord(
        record.partition(),
        record.offset(),
        record.timestamp(),
        record.key(),
        recordValueAsString(record, codec),
        extractHeaders(record)
    );
  }

  private KafkaPollResponse toKafkaPollResponse(ConsumerRecord consumerRecord, ValueCodec codec) {
    var key = consumerRecord.key() != null ? String.valueOf(consumerRecord.key()) : null;
    return new KafkaPollResponse(
        recordValueAsString(consumerRecord, codec),
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

  @SuppressWarnings({"unchecked", "deprecation"})
  private ValueCodec resolveCodec(KafkaOpsAwareConsumer consumer) {
    if (consumer.getValueCodec() != null) {
      return consumer.getValueCodec();
    }
    if (consumer.getSchema() != null) {
      return new AvroValueCodec(consumer.getSchema());
    }
    return null;
  }

  @SuppressWarnings("unchecked")
  @SneakyThrows
  private String recordValueAsString(ConsumerRecord<String, ?> consumerRecord, ValueCodec codec) {
    Object value = consumerRecord.value();
    if (codec != null) {
      return codec.toJson(value);
    } else if (value instanceof GenericContainer avro) {
      return AvroUtil.avroToJson(avro);
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
