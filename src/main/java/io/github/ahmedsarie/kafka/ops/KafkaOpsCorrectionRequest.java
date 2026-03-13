package io.github.ahmedsarie.kafka.ops;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
class KafkaOpsCorrectionRequest {

  private String key;
  private String value;
}
