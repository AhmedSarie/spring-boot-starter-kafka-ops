package io.github.ahmedsarie.kafka.ops;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.ahmedsarie.kafka.ops.KafkaOpsService.NoConsumerFoundException;
import java.util.List;
import java.util.Map;
import lombok.SneakyThrows;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(KafkaOpsController.class)
@ContextConfiguration(classes = KafkaOpsController.class)
class KafkaOpsControllerTest {

  private static final String RETRY_CONSUMER_API_URI = "/operational/consumer-retries";
  private static final String CORRECTIONS_API_URI = RETRY_CONSUMER_API_URI + "/corrections";
  private static final String CORRECTIONS_PAYLOAD_ONLY = "{\"name\":\"junit\",\"desc\":\"serialise!\"}";

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private KafkaOpsService service;

  @Test
  @SneakyThrows
  @DisplayName("should return list of registered consumers with details")
  void shouldReturnRegisteredConsumers() {

    // prepare
    when(service.getConsumerDetails()).thenReturn(List.of(
        new KafkaOpsConsumerInfo("orders", 3, 1500),
        new KafkaOpsConsumerInfo("payments", 6, 3000)
    ));

    // when
    this.mockMvc.perform(get(RETRY_CONSUMER_API_URI + "/consumers"))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].name").exists())
        .andExpect(jsonPath("$[0].partitions").exists())
        .andExpect(jsonPath("$[0].messageCount").exists());
  }

  @Test
  @SneakyThrows
  @DisplayName("should return empty list when no consumers registered")
  void shouldReturnEmptyListWhenNoConsumers() {

    // prepare
    when(service.getConsumerDetails()).thenReturn(List.of());

    // when
    this.mockMvc.perform(get(RETRY_CONSUMER_API_URI + "/consumers"))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  @SneakyThrows
  @DisplayName("should retry successfully")
  void shouldRetrySuccessfully() {

    //prepare
    doNothing().when(service).retry(any());

    // when
    this.mockMvc.perform(post(RETRY_CONSUMER_API_URI).contentType(MediaType.APPLICATION_JSON)
            .content("{\"topic\":\"test-topic\", \"partition\":0, \"offset\":0}"))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").exists());

    verify(service).retry(any());
  }

  @Test
  @SneakyThrows
  @DisplayName("should fail when retry consumer throw")
  void shouldFailWhenRetryConsumerThrow() {

    // prepare
    doThrow(new RuntimeException("connection timeout")).when(service).retry(any());

    // when
    this.mockMvc.perform(post(RETRY_CONSUMER_API_URI).contentType(MediaType.APPLICATION_JSON)
            .content("{\"topic\":\"test-topic\", \"partition\":0, \"offset\":0}"))
        .andDo(print())
        .andExpect(status().is5xxServerError())
        .andExpect(jsonPath("$.message").value("connection timeout"))
        .andExpect(jsonPath("$.status").value(500))
        .andExpect(jsonPath("$.error").value("Internal Server Error"));
  }

  @Test
  @SneakyThrows
  @DisplayName("should return 404 when consumer not found exception is thrown")
  void shouldReturn404WhenConsumerNotFoundExceptionIsThrown() {

    // prepare
    doThrow(new NoConsumerFoundException("topic not registered")).when(service).retry(any());

    // when
    this.mockMvc.perform(post(RETRY_CONSUMER_API_URI).contentType(MediaType.APPLICATION_JSON)
            .content("{\"topic\":\"test-topic\", \"partition\":0, \"offset\":0}"))
        .andDo(print())
        .andExpect(status().is4xxClientError())
        .andExpect(jsonPath("$.message").value("topic not registered"))
        .andExpect(jsonPath("$.status").value(404));
  }

  @Test
  @SneakyThrows
  @DisplayName("should poll successfully and return enriched response")
  void shouldPollSuccessfully() {

    //prepare
    var pollResponse = new KafkaPollResponse("junit", "key-1", 0, 0L, 1709251200000L,
        Map.of("traceid", "abc-123"));
    when(service.poll(anyString(), anyInt(), anyLong())).thenReturn(pollResponse);

    // when
    this.mockMvc.perform(
            get(RETRY_CONSUMER_API_URI + "?topicName=test-topic&partition=0&offset=0").contentType(MediaType.APPLICATION_JSON))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.consumerRecordValue").value("junit"))
        .andExpect(jsonPath("$.key").value("key-1"))
        .andExpect(jsonPath("$.partition").value(0))
        .andExpect(jsonPath("$.offset").value(0))
        .andExpect(jsonPath("$.timestamp").value(1709251200000L))
        .andExpect(jsonPath("$.headers.traceid").value("abc-123"));

    verify(service).poll("test-topic", 0, 0L);
  }

  @Test
  @SneakyThrows
  @DisplayName("should poll successfully when nothing found")
  void shouldPollSuccessfullyWhenNothingFound() {

    //prepare
    when(service.poll(anyString(), anyInt(), anyLong())).thenReturn(null);

    // when
    this.mockMvc.perform(
            get(RETRY_CONSUMER_API_URI + "?topicName=test-topic&partition=0&offset=0").contentType(MediaType.APPLICATION_JSON_VALUE))
        .andDo(print())
        .andExpect(status().isOk());

    verify(service).poll("test-topic", 0, 0L);
  }

  @Test
  @SneakyThrows
  @DisplayName("poll should fail when consumer throw")
  void pollShouldFailWhenConsumerThrow() {

    // prepare
    doThrow(new RuntimeException("broker unavailable")).when(service).poll(anyString(), anyInt(), anyLong());

    // when
    this.mockMvc.perform(
            get(RETRY_CONSUMER_API_URI + "?topicName=test-topic&partition=0&offset=0").contentType(MediaType.APPLICATION_JSON))
        .andDo(print())
        .andExpect(status().is5xxServerError())
        .andExpect(jsonPath("$.message").value("broker unavailable"));
  }

  @Test
  @SneakyThrows
  @DisplayName("should return 404 when consumer not found exception is thrown from poll")
  void shouldReturn404WhenConsumerNotFoundExceptionIsThrownFromPoll() {

    // prepare
    doThrow(new NoConsumerFoundException("unknown topic")).when(service).poll(anyString(), anyInt(), anyLong());

    // when
    this.mockMvc.perform(
            get(RETRY_CONSUMER_API_URI + "?topicName=test-topic&partition=0&offset=0").contentType(MediaType.APPLICATION_JSON))
        .andDo(print())
        .andExpect(status().is4xxClientError())
        .andExpect(jsonPath("$.message").value("unknown topic"));
  }

  @Test
  @SneakyThrows
  @DisplayName("batch poll should return records for offset-based query")
  void shouldBatchPollByOffset() {

    // prepare
    var batchRecord = new KafkaOpsBatchResponse.KafkaOpsBatchRecord(
        0, 5L, 1709251200000L, "key1", "value1", Map.of("h1", "v1"));
    var batchResponse = new KafkaOpsBatchResponse(List.of(batchRecord), false);
    when(service.batchPoll(eq("test-topic"), eq(0), eq(5L), isNull(), eq(10)))
        .thenReturn(batchResponse);

    // when
    this.mockMvc.perform(get(RETRY_CONSUMER_API_URI + "/batch?topicName=test-topic&partition=0&startOffset=5&limit=10"))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.records").isArray())
        .andExpect(jsonPath("$.records.length()").value(1))
        .andExpect(jsonPath("$.records[0].partition").value(0))
        .andExpect(jsonPath("$.records[0].offset").value(5))
        .andExpect(jsonPath("$.records[0].key").value("key1"))
        .andExpect(jsonPath("$.records[0].value").value("value1"))
        .andExpect(jsonPath("$.records[0].headers.h1").value("v1"))
        .andExpect(jsonPath("$.hasMore").value(false));
  }

  @Test
  @SneakyThrows
  @DisplayName("batch poll should return records for timestamp-based query")
  void shouldBatchPollByTimestamp() {

    // prepare
    var batchRecord = new KafkaOpsBatchResponse.KafkaOpsBatchRecord(
        0, 10L, 1000L, "key2", "value2", Map.of());
    var batchResponse = new KafkaOpsBatchResponse(List.of(batchRecord), true);
    when(service.batchPoll(eq("test-topic"), isNull(), isNull(), eq(1000L), eq(10)))
        .thenReturn(batchResponse);

    // when
    this.mockMvc.perform(get(RETRY_CONSUMER_API_URI + "/batch?topicName=test-topic&startTimestamp=1000&limit=10"))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.records[0].value").value("value2"))
        .andExpect(jsonPath("$.hasMore").value(true));
  }

  @Test
  @SneakyThrows
  @DisplayName("batch poll should return 400 when both offset and timestamp provided")
  void shouldReturn400WhenBothOffsetAndTimestamp() {

    // when
    this.mockMvc.perform(get(RETRY_CONSUMER_API_URI
            + "/batch?topicName=test-topic&partition=0&startOffset=5&startTimestamp=1000&limit=10"))
        .andDo(print())
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Provide either partition+startOffset or startTimestamp, not both"));
  }

  @Test
  @SneakyThrows
  @DisplayName("batch poll should return 400 when neither offset nor timestamp provided")
  void shouldReturn400WhenNeitherOffsetNorTimestamp() {

    // when
    this.mockMvc.perform(get(RETRY_CONSUMER_API_URI + "/batch?topicName=test-topic&limit=10"))
        .andDo(print())
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Provide either partition+startOffset or startTimestamp, not both"));
  }

  @Test
  @SneakyThrows
  @DisplayName("corrections end point with topic in the path succeeds with valid request")
  void testCorrectionsWithTopicInPathHappyPath() {

    // prepare
    doNothing().when(service).process(anyString(), anyString());

    // when
    this.mockMvc.perform(post(CORRECTIONS_API_URI + "/topic_name").contentType(MediaType.APPLICATION_JSON)
            .content(CORRECTIONS_PAYLOAD_ONLY))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").exists());
  }

  @Test
  @SneakyThrows
  @DisplayName("corrections end point with topic in the path should fail with empty request")
  void testCorrectionsWithTopicInPathInvalidRequest() {

    // prepare
    doNothing().when(service).process(anyString(), anyString());

    // when
    this.mockMvc.perform(post(CORRECTIONS_API_URI + "/topic_name").contentType(MediaType.APPLICATION_JSON)
            .content(""))
        .andDo(print())
        .andExpect(status().is4xxClientError());
  }

  @Test
  @SneakyThrows
  @DisplayName("correction should fail when process throw")
  void testCorrectionsWithTopicInPathThrowsException() {

    // prepare
    doThrow(new RuntimeException("schema mismatch")).when(service).process(anyString(), anyString());

    // when
    this.mockMvc.perform(post(CORRECTIONS_API_URI + "/topic_name").contentType(MediaType.APPLICATION_JSON)
            .content(CORRECTIONS_PAYLOAD_ONLY))
        .andDo(print())
        .andExpect(status().is5xxServerError())
        .andExpect(jsonPath("$.message").value("schema mismatch"));
  }

  @Test
  @SneakyThrows
  @DisplayName("should return 404 when consumer not found exception is thrown from correction")
  void testCorrectionsWithTopicInPath404() {

    // prepare
    doThrow(new NoConsumerFoundException("consumer missing")).when(service).process(anyString(), anyString());

    // when
    this.mockMvc.perform(post(CORRECTIONS_API_URI + "/topic_name").contentType(MediaType.APPLICATION_JSON)
            .content(CORRECTIONS_PAYLOAD_ONLY))
        .andDo(print())
        .andExpect(status().is4xxClientError())
        .andExpect(jsonPath("$.message").value("consumer missing"));
  }
}
