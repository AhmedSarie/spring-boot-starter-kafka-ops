package io.github.ahmedsarie.kafka.ops;

import static java.util.Spliterator.ORDERED;
import static java.util.Spliterators.spliteratorUnknownSize;
import static java.util.stream.StreamSupport.stream;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;

@Slf4j
@RequiredArgsConstructor
class ManualKafkaConsumer {

  private final Duration pollDuration;

  synchronized <T> Optional<ConsumerRecord<String, T>> poll(
      String topic,
      int partition,
      long offset,
      KafkaConsumer<String, T> kafkaConsumer
  ) {
    assignAndSeek(kafkaConsumer, topic, partition, offset);
    return tryPolling(topic, partition, offset, kafkaConsumer);
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
      log.info("Returning consumer record: {}", item);
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
