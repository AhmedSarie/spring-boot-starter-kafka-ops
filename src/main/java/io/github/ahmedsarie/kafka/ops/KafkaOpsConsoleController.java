package io.github.ahmedsarie.kafka.ops;

import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@RestController
@RequestMapping("/kafka-ops/api")
@RequiredArgsConstructor
@ConditionalOnProperty(value = "kafka.ops.rest-api.enabled", havingValue = "true")
class KafkaOpsConsoleController {

    private static final String DEFAULT_RETRY_ENDPOINT_URL = "operational/consumer-retries";
    private final KafkaOpsProperties kafkaOpsProperties;

    @GetMapping("/config")
    @ResponseStatus(HttpStatus.OK)
    public Map<String, String> getConfig() {
        try {
            var restApi = kafkaOpsProperties.getRestApi();
            var url = restApi != null && restApi.getRetryEndpointUrl() != null
                ? restApi.getRetryEndpointUrl()
                : DEFAULT_RETRY_ENDPOINT_URL;
            return Map.of("retryEndpointUrl", url);
        } catch (Exception e) {
            log.error("Console: failed to return configuration", e);
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, e.getMessage(), e);
        }
    }
}
