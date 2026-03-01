package io.github.ahmedsarie.kafka.ops;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/kafka-ops/api")
@RequiredArgsConstructor
@ConditionalOnProperty(value = "kafka.ops.console.enabled", havingValue = "true")
class KafkaOpsConsoleController {

  private static final String DEFAULT_RETRY_ENDPOINT_URL = "operational/consumer-retries";
  private final KafkaOpsProperties kafkaOpsProperties;

  @GetMapping("/config")
  public ResponseEntity<?> getConfig() {
    try {
      var restApi = kafkaOpsProperties.getRestApi();
      var url = restApi != null && restApi.getRetryEndpointUrl() != null
          ? restApi.getRetryEndpointUrl()
          : DEFAULT_RETRY_ENDPOINT_URL;
      return ResponseEntity.ok(Map.of("retryEndpointUrl", url));
    } catch (Exception e) {
      log.error("Console: failed to return configuration", e);
      var message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
          Map.of("status", 500, "error", "Internal Server Error", "message", message));
    }
  }
}
