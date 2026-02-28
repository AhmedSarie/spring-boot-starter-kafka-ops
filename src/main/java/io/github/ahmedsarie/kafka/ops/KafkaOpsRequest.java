package io.github.ahmedsarie.kafka.ops;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
class KafkaOpsRequest {

  @NotEmpty
  private String topic;
  @Min(0)
  @NotNull
  private Integer partition;
  @Min(0)
  @NotNull
  private Long offset;
}
