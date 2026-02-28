package io.github.ahmedsarie.kafka.ops;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
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
    doThrow(new RuntimeException()).when(service).retry(any());

    // when
    this.mockMvc.perform(post(RETRY_CONSUMER_API_URI).contentType(MediaType.APPLICATION_JSON)
            .content("{\"topic\":\"test-topic\", \"partition\":0, \"offset\":0}"))
        .andDo(print())
        .andExpect(status().is5xxServerError());
  }

  @Test
  @SneakyThrows
  @DisplayName("should return 404 when consumer not found exception is thrown")
  void shouldReturn404WhenConsumerNotFoundExceptionIsThrown() {

    // prepare
    doThrow(new NoConsumerFoundException("")).when(service).retry(any());

    // when
    this.mockMvc.perform(post(RETRY_CONSUMER_API_URI).contentType(MediaType.APPLICATION_JSON)
            .content("{\"topic\":\"test-topic\", \"partition\":0, \"offset\":0}"))
        .andDo(print())
        .andExpect(status().is4xxClientError());
  }

  @Test
  @SneakyThrows
  @DisplayName("should poll successfully")
  void shouldPollSuccessfully() {

    //prepare
    when(service.poll(anyString(), anyInt(), anyLong())).thenReturn("junit");

    // when
    this.mockMvc.perform(
            get(RETRY_CONSUMER_API_URI + "?topicName=test-topic&partition=0&offset=0").contentType(MediaType.APPLICATION_JSON))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));

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
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));

    verify(service).poll("test-topic", 0, 0L);
  }

  @Test
  @SneakyThrows
  @DisplayName("poll should fail when consumer throw")
  void pollShouldFailWhenConsumerThrow() {

    // prepare
    doThrow(new RuntimeException()).when(service).poll(anyString(), anyInt(), anyLong());

    // when
    this.mockMvc.perform(
            get(RETRY_CONSUMER_API_URI + "?topicName=test-topic&partition=0&offset=0").contentType(MediaType.APPLICATION_JSON))
        .andDo(print())
        .andExpect(status().is5xxServerError());
  }

  @Test
  @SneakyThrows
  @DisplayName("should return 404 when consumer not found exception is thrown from poll")
  void shouldReturn404WhenConsumerNotFoundExceptionIsThrownFromPoll() {

    // prepare
    doThrow(new NoConsumerFoundException("")).when(service).poll(anyString(), anyInt(), anyLong());

    // when
    this.mockMvc.perform(
            get(RETRY_CONSUMER_API_URI + "?topicName=test-topic&partition=0&offset=0").contentType(MediaType.APPLICATION_JSON))
        .andDo(print())
        .andExpect(status().is4xxClientError());
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
    doThrow(new RuntimeException()).when(service).process(anyString(), anyString());

    // when
    this.mockMvc.perform(post(CORRECTIONS_API_URI + "/topic_name").contentType(MediaType.APPLICATION_JSON)
            .content(CORRECTIONS_PAYLOAD_ONLY))
        .andDo(print())
        .andExpect(status().is5xxServerError());
  }

  @Test
  @SneakyThrows
  @DisplayName("should return 404 when consumer not found exception is thrown from correction")
  void testCorrectionsWithTopicInPath404() {

    // prepare
    doThrow(new NoConsumerFoundException("")).when(service).process(anyString(), anyString());

    // when
    this.mockMvc.perform(post(CORRECTIONS_API_URI + "/topic_name").contentType(MediaType.APPLICATION_JSON)
            .content(CORRECTIONS_PAYLOAD_ONLY))
        .andDo(print())
        .andExpect(status().is4xxClientError());
  }
}
