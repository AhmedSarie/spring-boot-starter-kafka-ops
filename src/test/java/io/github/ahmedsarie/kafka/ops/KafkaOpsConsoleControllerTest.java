package io.github.ahmedsarie.kafka.ops;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import lombok.SneakyThrows;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(KafkaOpsConsoleController.class)
@ContextConfiguration(classes = KafkaOpsConsoleController.class)
class KafkaOpsConsoleControllerTest {

    private static final String CONSOLE_API_URI = "/kafka-ops/api";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private KafkaOpsProperties properties;

    @Test
    @SneakyThrows
    @DisplayName("should return config with retry endpoint url")
    void shouldReturnConfigWithRetryEndpointUrl() {

        // prepare
        var restApi = new KafkaOpsProperties.RestApi(true, "custom/retry-path");
        when(properties.getRestApi()).thenReturn(restApi);

        // when
        this.mockMvc.perform(get(CONSOLE_API_URI + "/config"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.retryEndpointUrl").value("custom/retry-path"));
    }

    @Test
    @SneakyThrows
    @DisplayName("should return default retry endpoint url when rest-api config is null")
    void shouldReturnDefaultRetryEndpointUrl() {

        // prepare
        when(properties.getRestApi()).thenReturn(null);

        // when
        this.mockMvc.perform(get(CONSOLE_API_URI + "/config"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.retryEndpointUrl").value("operational/consumer-retries"));
    }
}
