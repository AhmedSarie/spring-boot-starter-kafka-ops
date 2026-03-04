package io.github.ahmedsarie.kafka.ops;

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

  @Setter
  private KafkaOpsConsumerInfo dlt;

  @Setter
  private KafkaOpsConsumerInfo retry;
}
