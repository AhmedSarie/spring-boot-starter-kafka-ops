package io.github.ays.kafka.ops;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Getter
@RequiredArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
class KafkaOpsConsumerInfo {

  private final String name;
  private final int partitions;
  private final long messageCount;
  private final String keyFormat;
  private final String valueFormat;

  KafkaOpsConsumerInfo(String name, int partitions, long messageCount) {
    this(name, partitions, messageCount, null, null);
  }

  @Setter
  private KafkaOpsConsumerInfo dlt;

  @Setter
  private KafkaOpsConsumerInfo retry;
}
