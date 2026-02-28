package io.github.ahmedsarie.kafka.ops;

import static io.github.ahmedsarie.kafka.ops.AvroUtil.avroToJson;
import static io.github.ahmedsarie.kafka.ops.AvroUtil.jsonToAvro;
import static java.lang.String.format;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.generic.GenericContainer;
import org.apache.kafka.clients.consumer.ConsumerRecord;

@Slf4j
public class KafkaOpsService {

  private final KafkaOpsConsumerRegistry registry;
  private final ManualKafkaConsumer manualKafkaConsumer;
  private final ObjectMapper mapper;

  KafkaOpsService(KafkaOpsConsumerRegistry registry, ManualKafkaConsumer manualKafkaConsumer) {
    this.registry = registry;
    this.manualKafkaConsumer = manualKafkaConsumer;
    this.mapper = new ObjectMapper();
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
  public String poll(String topic, int partition, long offset) {
    var entry = this.registry.find(topic);
    var consumer = entry.getValue();
    Optional<ConsumerRecord> consumerRecord = manualKafkaConsumer.poll(topic, partition, offset, consumer);
    return consumerRecord.map(this::recordValueAsString).orElse(null);
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
