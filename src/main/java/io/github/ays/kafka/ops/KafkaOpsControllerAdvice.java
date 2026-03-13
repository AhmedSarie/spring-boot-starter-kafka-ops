package io.github.ays.kafka.ops;

import io.github.ays.kafka.ops.KafkaOpsService.NoConsumerFoundException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@Slf4j
@RequiredArgsConstructor
@ControllerAdvice(assignableTypes = KafkaOpsController.class)
class KafkaOpsControllerAdvice {

  private final KafkaOpsProperties kafkaOpsProperties;

  @ExceptionHandler(NoConsumerFoundException.class)
  ResponseEntity<Map<String, Object>> handleNotFound(NoConsumerFoundException e) {
    return errorResponse(HttpStatus.NOT_FOUND, e);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException e) {
    return errorResponse(HttpStatus.BAD_REQUEST, e);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<Map<String, Object>> handleMessageNotReadable(HttpMessageNotReadableException e) {
    return errorResponse(HttpStatus.BAD_REQUEST, e);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<Map<String, Object>> handleGeneric(Exception e) {
    var restApi = kafkaOpsProperties.getRestApi();
    var expose = restApi == null || restApi.isExposeErrorDetails();
    var message = expose
        ? (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName())
        : "Internal server error";
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
        Map.of("status", HttpStatus.INTERNAL_SERVER_ERROR.value(),
            "error", HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
            "message", message));
  }

  private static ResponseEntity<Map<String, Object>> errorResponse(HttpStatus status, Exception e) {
    var message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    return ResponseEntity.status(status).body(
        Map.of("status", status.value(), "error", status.getReasonPhrase(), "message", message));
  }
}
