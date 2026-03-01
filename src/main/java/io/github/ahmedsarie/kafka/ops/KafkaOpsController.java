package io.github.ahmedsarie.kafka.ops;

import static java.lang.String.format;

import io.github.ahmedsarie.kafka.ops.KafkaOpsService.NoConsumerFoundException;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
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

  private final KafkaOpsService kafkaOpsService;

  @GetMapping("/consumers")
  public ResponseEntity<?> getConsumers() {
    try {
      log.info("Listing registered consumers");
      return ResponseEntity.ok(kafkaOpsService.getRegisteredTopics());
    } catch (Exception e) {
      log.error("Failed to list consumers", e);
      return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, e);
    }
  }

  @PostMapping
  public ResponseEntity<?> retry(@RequestBody @Valid KafkaOpsRequest body) {
    try {
      var id = UUID.randomUUID().toString();
      MDC.put("api-response-id", id);
      log.info(format("Retry started for topic=%s - partition=%d - offset=%d",
          body.getTopic(), body.getPartition(), body.getOffset()));
      kafkaOpsService.retry(body);
      return ResponseEntity.ok(new KafkaOpsResponse(id));
    } catch (NoConsumerFoundException e) {
      log.error("Retry failed. consumer not found for topic ", e);
      return errorResponse(HttpStatus.NOT_FOUND, e);
    } catch (Exception e) {
      log.error("Retry failed ", e);
      return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, e);
    } finally {
      log.info("Retry finished");
      MDC.clear();
    }
  }

  @GetMapping
  public ResponseEntity<?> poll(
      @RequestParam String topicName, @RequestParam int partition, @RequestParam long offset
  ) {
    try {
      log.info(format("Polling started for topic=%s - partition=%d - offset=%d", topicName, partition, offset));
      return ResponseEntity.ok(new KafkaPollResponse(kafkaOpsService.poll(topicName, partition, offset)));
    } catch (NoConsumerFoundException e) {
      log.error("Poll failed. consumer not found for topic ", e);
      return errorResponse(HttpStatus.NOT_FOUND, e);
    } catch (Exception e) {
      log.error("Poll failed ", e);
      return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, e);
    } finally {
      log.info("Poll finished");
      MDC.clear();
    }
  }

  @PostMapping("/corrections/{kafka_topic}")
  public ResponseEntity<?> correct(@RequestBody String payload, @PathVariable("kafka_topic") String kafkaTopic) {
    try {
      var id = UUID.randomUUID().toString();
      MDC.put("api-response-id", id);
      log.info(format("Correction started for topic=%s", kafkaTopic));
      kafkaOpsService.process(kafkaTopic, payload);
      return ResponseEntity.ok(new KafkaOpsResponse(id));
    } catch (NoConsumerFoundException e) {
      log.error("Correction failed. consumer not found for topic ", e);
      return errorResponse(HttpStatus.NOT_FOUND, e);
    } catch (Exception e) {
      log.error("Correction failed ", e);
      return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, e);
    } finally {
      log.info("Correction finished");
      MDC.clear();
    }
  }

  private static ResponseEntity<Map<String, Object>> errorResponse(HttpStatus status, Exception e) {
    return ResponseEntity.status(status).body(
        Map.of("status", status.value(), "error", status.getReasonPhrase(), "message", e.getMessage()));
  }
}
