package io.github.ahmedsarie.kafka.ops;

import static java.lang.String.format;

import jakarta.validation.Valid;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/${kafka.ops.rest-api.retry-endpoint-url:operational/consumer-retries}")
@RequiredArgsConstructor
@ConditionalOnProperty(value = "kafka.ops.rest-api.enabled", havingValue = "true")
class KafkaOpsController {

  private static final int MAX_PAYLOAD_SIZE = 1_048_576;

  private final KafkaOpsService kafkaOpsService;
  private final KafkaOpsProperties kafkaOpsProperties;
  private final Optional<KafkaOpsDltRouter> dltRouter;

  @GetMapping("/consumers")
  public ResponseEntity<?> getConsumers() {
    log.info("Listing registered consumers");
    return ResponseEntity.ok(kafkaOpsService.getConsumerDetails());
  }

  @PostMapping
  public ResponseEntity<?> retry(@RequestBody @Valid KafkaOpsRequest body) {
    var id = UUID.randomUUID().toString();
    MDC.put("api-response-id", id);
    try {
      log.info(format("Retry started for topic=%s - partition=%d - offset=%d",
          body.getTopic(), body.getPartition(), body.getOffset()));
      kafkaOpsService.retry(body);
      return ResponseEntity.ok(new KafkaOpsResponse(id));
    } finally {
      log.info("Retry finished");
      MDC.clear();
    }
  }

  @GetMapping
  public ResponseEntity<?> poll(
      @RequestParam String topicName, @RequestParam int partition, @RequestParam long offset
  ) {

    log.info(format("Polling started for topic=%s - partition=%d - offset=%d", topicName, partition, offset));
    try {
      var result = kafkaOpsService.poll(topicName, partition, offset);
      return result.map(ResponseEntity::ok)
          .map(r -> (ResponseEntity<?>) r)
          .orElseGet(() -> ResponseEntity.noContent().build());
    } finally {
      log.info("Poll finished");
      MDC.clear();
    }
  }

  @GetMapping("/batch")
  public ResponseEntity<?> batchPoll(
      @RequestParam String topicName,
      @RequestParam(required = false) Integer partition,
      @RequestParam(required = false) Long startOffset,
      @RequestParam(required = false) Long startTimestamp,
      @RequestParam(defaultValue = "10") int limit
  ) {

    log.info(format("Batch poll started for topic=%s", topicName));
    try {
      var hasTimestamp = startTimestamp != null;
      var hasOffset = partition != null && startOffset != null;
      if (hasTimestamp == hasOffset) {
        return ResponseEntity.badRequest().body(
            Map.of("message", "Provide either partition+startOffset or startTimestamp, not both"));
      }
      if ((partition != null) != (startOffset != null)) {
        return ResponseEntity.badRequest().body(
            Map.of("message", "partition and startOffset must both be provided"));
      }
      var result = kafkaOpsService.batchPoll(topicName, partition, startOffset, startTimestamp, limit);
      return ResponseEntity.ok(result);
    } finally {
      log.info("Batch poll finished");
      MDC.clear();
    }
  }

  @PostMapping("/corrections/{kafka_topic}")
  public ResponseEntity<?> correct(@RequestBody KafkaOpsCorrectionRequest request,
                                   @PathVariable("kafka_topic") String kafkaTopic) {

    if (request.getValue() == null) {
      throw new IllegalArgumentException("Correction value must not be null");
    }
    if (request.getValue().length() > MAX_PAYLOAD_SIZE) {
      throw new IllegalArgumentException("Payload size exceeds maximum allowed size of " + MAX_PAYLOAD_SIZE + " bytes");
    }
    var id = UUID.randomUUID().toString();
    MDC.put("api-response-id", id);
    try {
      log.info(format("Correction started for topic=%s", kafkaTopic));
      kafkaOpsService.process(kafkaTopic, request.getKey(), request.getValue());
      return ResponseEntity.ok(new KafkaOpsResponse(id));
    } finally {
      log.info("Correction finished");
      MDC.clear();
    }
  }

  @PostMapping("/dlt-routing/{topic}/start")
  public ResponseEntity<?> startDltRouting(@PathVariable("topic") String topic) {
    var id = UUID.randomUUID().toString();
    MDC.put("api-response-id", id);
    try {
      if (dltRouter.isEmpty()) {
        log.error(format("DLT routing not configured — enable via kafka.ops.dlt-routing.enabled=true"));
        throw new KafkaOpsService.NoConsumerFoundException(
            "DLT routing is not enabled. Set kafka.ops.dlt-routing.enabled=true");
      }
      log.info(format("DLT routing start for topic=%s", topic));
      dltRouter.get().start(topic);
      return ResponseEntity.ok(new KafkaOpsResponse(id));
    } finally {
      log.info("DLT routing request finished");
      MDC.clear();
    }
  }
}
