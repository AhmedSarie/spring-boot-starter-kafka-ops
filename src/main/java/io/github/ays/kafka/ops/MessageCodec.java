package io.github.ays.kafka.ops;

/**
 * Codec for converting consumer record keys or values to/from JSON.
 * <p>
 * Consumers may override {@link KafkaOpsAwareConsumer#getKeyCodec()} and
 * {@link KafkaOpsAwareConsumer#getValueCodec()} to declare explicit codecs.
 * When no codec is declared the library auto-resolves one at registration time:
 * <ul>
 *   <li>Avro ({@code SpecificRecord}) &rarr; {@link AvroMessageCodec}</li>
 *   <li>Protobuf ({@code Message}) &rarr; {@link ProtoMessageCodec}</li>
 *   <li>{@code String} &rarr; {@link StringMessageCodec}</li>
 *   <li>Any other POJO &rarr; {@link JsonMessageCodec}</li>
 * </ul>
 *
 * @param <T> the type to encode/decode
 */
public interface MessageCodec<T> {

  String toJson(T value);

  T fromJson(String json);
}
