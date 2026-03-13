package io.github.ays.kafka.ops;

import static java.util.Spliterator.ORDERED;
import static java.util.Spliterators.spliteratorUnknownSize;
import static java.util.stream.StreamSupport.stream;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndTimestamp;
import org.apache.kafka.common.TopicPartition;

@Slf4j
@RequiredArgsConstructor
class ManualKafkaConsumer {

  private static final Duration BATCH_POLL_TIMEOUT = Duration.ofMillis(200);
  private final Duration pollDuration;

  <T> Optional<ConsumerRecord<String, T>> poll(
      String topic,
      int partition,
      long offset,
      KafkaConsumer<String, T> kafkaConsumer
  ) {
    synchronized (kafkaConsumer) {
      assignAndSeek(kafkaConsumer, topic, partition, offset);
      return tryPolling(topic, partition, offset, kafkaConsumer);
    }
  }

  <T> List<ConsumerRecord<String, T>> pollBatch(
      String topic,
      int partition,
      long startOffset,
      int limit,
      KafkaConsumer<String, T> kafkaConsumer
  ) {
    synchronized (kafkaConsumer) {
      assignAndSeek(kafkaConsumer, topic, partition, startOffset);
      return pollMultiple(kafkaConsumer, limit);
    }
  }

  <T> List<ConsumerRecord<String, T>> pollBatchByTimestamp(
      String topic,
      long startTimestamp,
      int limit,
      KafkaConsumer<String, T> kafkaConsumer
  ) {
    synchronized (kafkaConsumer) {
      var partitionInfos = kafkaConsumer.partitionsFor(topic);
      var topicPartitions = new ArrayList<TopicPartition>();
      var timestampsToSearch = new HashMap<TopicPartition, Long>();
      for (var info : partitionInfos) {
        var tp = new TopicPartition(topic, info.partition());
        topicPartitions.add(tp);
        timestampsToSearch.put(tp, startTimestamp);
      }

      kafkaConsumer.assign(topicPartitions);

      Map<TopicPartition, OffsetAndTimestamp> offsets = kafkaConsumer.offsetsForTimes(timestampsToSearch);
      for (var entry : offsets.entrySet()) {
        if (entry.getValue() != null) {
          kafkaConsumer.seek(entry.getKey(), entry.getValue().offset());
        } else {
          var endOffsets = kafkaConsumer.endOffsets(List.of(entry.getKey()));
          kafkaConsumer.seek(entry.getKey(), endOffsets.getOrDefault(entry.getKey(), 0L));
        }
      }

      return pollMultiple(kafkaConsumer, limit);
    }
  }

  private <T> List<ConsumerRecord<String, T>> pollMultiple(
      KafkaConsumer<String, T> kafkaConsumer, int limit
  ) {
    var result = new ArrayList<ConsumerRecord<String, T>>();
    while (result.size() < limit) {
      var records = kafkaConsumer.poll(BATCH_POLL_TIMEOUT);
      if (records.isEmpty()) {
        break;
      }
      for (var record : records) {
        result.add(record);
        if (result.size() >= limit) {
          break;
        }
      }
    }
    return result;
  }

  private <T> Optional<ConsumerRecord<String, T>> tryPolling(
      String topic, int partition, long offset, KafkaConsumer<String, T> kafkaConsumer
  ) {
    var consumerRecords = kafkaConsumer.poll(pollDuration);
    if (consumerRecords.isEmpty()) {
      log.warn("no consumer records polled at topic={}, partition={}, offset={}", topic, partition, offset);
      return Optional.empty();
    } else {
      var consumerRecordsStream = stream(spliteratorUnknownSize(consumerRecords.iterator(), ORDERED), false);
      var item = consumerRecordsStream.filter(validate(partition, offset)).findFirst();
      item.ifPresent(r -> log.info("Returning consumer record: topic={}, partition={}, offset={}",
          r.topic(), r.partition(), r.offset()));
      return item;
    }
  }

  private static <T> Predicate<ConsumerRecord<String, T>> validate(int partition, long offset) {
    return record -> {
      if (record.partition() == partition && record.offset() == offset) {
        return true;
      } else {
        log.warn("unexpected consumer record polled: " + record);
        return false;
      }
    };
  }

  private void assignAndSeek(KafkaConsumer<?, ?> kafkaConsumer, String topicName, Integer partition, Long offset) {
    TopicPartition topicPartition = new TopicPartition(topicName, partition);
    kafkaConsumer.assign(List.of(topicPartition));
    kafkaConsumer.seek(topicPartition, offset);
  }
}
