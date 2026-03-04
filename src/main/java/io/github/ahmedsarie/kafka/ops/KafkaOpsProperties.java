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
  private static final int DEFAULT_BATCH_MAX_LIMIT = 100;
  private static final boolean DEFAULT_DLT_ENABLED = false;
  private static final int DEFAULT_DLT_IDLE_SHUTDOWN_MINUTES = 5;
  private static final String DEFAULT_DLT_RESTART_CRON = "0 */30 * * * *";


  private final String groupId;

  private final Integer maxPollIntervalMs;

  private final RestApi restApi;

  private final Batch batch;

  private final DltRouting dltRouting;

  public KafkaOpsProperties(RestApi restApi, String groupId, Integer maxPollIntervalMs,
                            Batch batch, DltRouting dltRouting) {
    this.restApi = restApi;
    this.groupId = isEmpty(groupId) ? DEFAULT_GROUP_ID : groupId;
    this.maxPollIntervalMs = isEmpty(maxPollIntervalMs) ? DEFAULT_POLL_DURATION_MS : maxPollIntervalMs;
    this.batch = batch != null ? batch : new Batch(DEFAULT_BATCH_MAX_LIMIT);
    this.dltRouting = dltRouting != null ? dltRouting : new DltRouting(
        DEFAULT_DLT_ENABLED, DEFAULT_DLT_IDLE_SHUTDOWN_MINUTES,
        DEFAULT_DLT_RESTART_CRON);
  }

  @Getter
  @AllArgsConstructor
  public static class RestApi {
    private final boolean enabled;
    private final String retryEndpointUrl;
  }

  @Getter
  @AllArgsConstructor
  public static class Batch {
    private final int maxLimit;
  }

  @Getter
  @AllArgsConstructor
  public static class DltRouting {
    private final boolean enabled;
    private final int idleShutdownMinutes;
    private final String restartCron;
  }
}
