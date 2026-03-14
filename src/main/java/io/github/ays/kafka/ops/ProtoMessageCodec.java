package io.github.ays.kafka.ops;

import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

/**
 * {@link MessageCodec} for Protocol Buffer messages.
 *
 * <pre>{@code
 * @Override
 * public MessageCodec<MyProtoMessage> getValueCodec() {
 *     return new ProtoMessageCodec<>(MyProtoMessage.getDefaultInstance());
 * }
 * }</pre>
 *
 * @param <T> the protobuf message type
 */
@RequiredArgsConstructor
public class ProtoMessageCodec<T extends Message> implements MessageCodec<T> {

  private final T defaultInstance;

  @Override
  @SneakyThrows
  public String toJson(T value) {
    return JsonFormat.printer().print(value);
  }

  @Override
  @SuppressWarnings("unchecked")
  @SneakyThrows
  public T fromJson(String json) {
    var builder = defaultInstance.newBuilderForType();
    JsonFormat.parser().ignoringUnknownFields().merge(json, builder);
    return (T) builder.build();
  }
}
