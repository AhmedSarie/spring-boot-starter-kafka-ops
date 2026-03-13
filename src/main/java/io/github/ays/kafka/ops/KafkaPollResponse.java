package io.github.ays.kafka.ops;

import java.util.Map;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class KafkaPollResponse {

  private final String consumerRecordValue;
  private final String key;
  private final int partition;
  private final long offset;
  private final long timestamp;
  private final Map<String, String> headers;
}
