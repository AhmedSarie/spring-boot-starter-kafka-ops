package io.github.ahmedsarie.kafka.ops;

import static java.lang.String.format;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

import io.github.ahmedsarie.kafka.ops.KafkaOpsService.NoConsumerFoundException;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@RestController
@RequestMapping("/${kafka.ops.rest-api.retry-endpoint-url:operational/consumer-retries}")
@RequiredArgsConstructor
@ConditionalOnProperty(value = "kafka.ops.rest-api.enabled", havingValue = "true")
class KafkaOpsController {

  private final KafkaOpsService kafkaOpsService;

  @PostMapping
  @ResponseStatus(HttpStatus.OK)
  public KafkaOpsResponse retry(@RequestBody @Valid KafkaOpsRequest request) {
    try {
      var id = UUID.randomUUID().toString();
      MDC.put("api-response-id", id);
      var topic = request.getTopic();
      var partition = request.getPartition();
      var offset = request.getOffset();

      log.info(format("Retry started for topic=%s - partition=%d - offset=%d", topic, partition, offset));
      kafkaOpsService.retry(request);
      return new KafkaOpsResponse(id);
    } catch (NoConsumerFoundException e) {
      log.error("Retry failed. consumer not found for topic ", e);
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
    } catch (Exception e) {
      log.error("Retry failed ", e);
      throw new ResponseStatusException(INTERNAL_SERVER_ERROR, e.getMessage(), e);
    } finally {
      log.info("Retry finished");
      MDC.clear();
    }
  }

  @GetMapping
  @ResponseStatus(HttpStatus.OK)
  public KafkaPollResponse poll(
      @RequestParam String topicName, @RequestParam int partition, @RequestParam long offset
  ) {
    try {
      log.info(format("Polling started for topic=%s - partition=%d - offset=%d", topicName, partition, offset));
      return new KafkaPollResponse(kafkaOpsService.poll(topicName, partition, offset));
    } catch (NoConsumerFoundException e) {
      log.error("Poll failed. consumer not found for topic ", e);
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
    } catch (Exception e) {
      log.error("Poll failed ", e);
      throw new ResponseStatusException(INTERNAL_SERVER_ERROR, e.getMessage(), e);
    } finally {
      log.info("Poll finished");
      MDC.clear();
    }
  }

  @PostMapping("/corrections/{kafka_topic}")
  @ResponseStatus(HttpStatus.OK)
  public KafkaOpsResponse correct(@RequestBody String payload, @PathVariable("kafka_topic") String kafkaTopic) {
    try {
      var id = UUID.randomUUID().toString();
      MDC.put("api-response-id", id);
      log.info(format("Correction started for topic=%s", kafkaTopic));
      kafkaOpsService.process(kafkaTopic, payload);
      return new KafkaOpsResponse(id);
    } catch (NoConsumerFoundException e) {
      log.error("Correction failed. consumer not found for topic ", e);
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
    } catch (Exception e) {
      log.error("Correction failed ", e);
      throw new ResponseStatusException(INTERNAL_SERVER_ERROR, e.getMessage(), e);
    } finally {
      log.info("Correction finished");
      MDC.clear();
    }
  }
}
