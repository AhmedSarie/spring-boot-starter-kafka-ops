package io.github.ahmedsarie.kafka.ops;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
class KafkaOpsConsumerInfo {

  private final String name;
  private final int partitions;
  private final long messageCount;
}
