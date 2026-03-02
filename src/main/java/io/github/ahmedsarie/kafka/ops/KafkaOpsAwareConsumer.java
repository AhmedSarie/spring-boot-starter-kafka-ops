package io.github.ahmedsarie.kafka.ops;

import org.apache.avro.Schema;
import org.apache.kafka.clients.consumer.ConsumerRecord;

/**
 * Interface that consumers must implement to enable Kafka operations
 * (retry, poll, corrections) for their topics.
 *
 * @param <K> the key type of the consumer record
 * @param <T> the value type of the consumer record
 */
public interface KafkaOpsAwareConsumer<K, T> {

  void consume(ConsumerRecord<K, T> consumerRecord);

  TopicConfig getTopic();

  default TopicConfig getDltTopic() {
    return null;
  }

  default TopicConfig getRetryTopic() {
    return null;
  }

  default String getContainerName() {
    return null;
  }

  default Schema getSchema() {
    return null;
  }
}
