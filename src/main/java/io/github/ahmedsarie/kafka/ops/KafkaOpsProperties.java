package io.github.ahmedsarie.kafka.ops;

import static org.springframework.util.ObjectUtils.isEmpty;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@ConfigurationProperties(prefix = "kafka.ops")
public class KafkaOpsProperties {

  private static final String DEFAULT_GROUP_ID = "default-ops-group-id";
  private static final int DEFAULT_POLL_DURATION_MS = 5000;

  private final String groupId;

  private final Integer maxPollIntervalMs;

  private final RestApi restApi;

  public KafkaOpsProperties(RestApi restApi, String groupId, Integer maxPollIntervalMs) {
    this.restApi = restApi;
    this.groupId = isEmpty(groupId) ? DEFAULT_GROUP_ID : groupId;
    this.maxPollIntervalMs = isEmpty(maxPollIntervalMs) ? DEFAULT_POLL_DURATION_MS : maxPollIntervalMs;
  }

  @Getter
  @AllArgsConstructor
  public static class RestApi {
    private final boolean enabled;
    private final String retryEndpointUrl;
  }
}
