package io.github.ays.kafka.ops;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

@Getter
@ToString
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class TopicConfig {

  @EqualsAndHashCode.Include
  private final String name;
  private final String dltTopic;
  private final String retryTopic;

  public static TopicConfig of(String name) {
    return new TopicConfig(name, null, null);
  }

  public TopicConfig withDlt(String dltTopic) {
    return new TopicConfig(this.name, dltTopic, this.retryTopic);
  }

  public TopicConfig withRetry(String retryTopic) {
    return new TopicConfig(this.name, this.dltTopic, retryTopic);
  }
}
