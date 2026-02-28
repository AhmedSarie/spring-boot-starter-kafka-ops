package io.github.ahmedsarie.kafka.ops;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class KafkaPollResponse {

  private final String consumerRecordValue;
}
