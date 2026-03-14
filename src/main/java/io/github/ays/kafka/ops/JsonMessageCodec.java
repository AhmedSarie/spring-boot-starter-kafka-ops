package io.github.ays.kafka.ops;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

/**
 * {@link MessageCodec} for plain JSON objects serialized with Jackson.
 *
 * <pre>{@code
 * @Override
 * public MessageCodec<OrderEvent> getValueCodec() {
 *     return new JsonMessageCodec<>(OrderEvent.class);
 * }
 * }</pre>
 *
 * @param <T> the POJO type
 */
@RequiredArgsConstructor
public class JsonMessageCodec<T> implements MessageCodec<T> {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final Class<T> type;

  @Override
  @SneakyThrows
  public String toJson(T value) {
    return MAPPER.writeValueAsString(value);
  }

  @Override
  @SneakyThrows
  public T fromJson(String json) {
    return MAPPER.readValue(json, type);
  }
}
