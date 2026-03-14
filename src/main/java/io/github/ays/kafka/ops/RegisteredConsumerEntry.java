package io.github.ays.kafka.ops;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.KafkaConsumer;

@Getter
@RequiredArgsConstructor
class RegisteredConsumerEntry {

  private final KafkaOpsAwareConsumer consumer;
  private final KafkaConsumer kafkaConsumer;
  private final MessageCodec keyCodec;
  private final MessageCodec valueCodec;
}
