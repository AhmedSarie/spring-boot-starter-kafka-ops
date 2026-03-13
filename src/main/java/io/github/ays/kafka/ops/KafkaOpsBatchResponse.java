package io.github.ays.kafka.ops;

import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
class KafkaOpsBatchResponse {

  private final List<KafkaOpsBatchRecord> records;
  private final boolean hasMore;

  @Getter
  @RequiredArgsConstructor
  static class KafkaOpsBatchRecord {

    private final int partition;
    private final long offset;
    private final long timestamp;
    private final String key;
    private final String value;
    private final Map<String, String> headers;
  }
}
