package io.github.ays.kafka.ops;

/**
 * Codec for converting consumer record values to/from JSON.
 * <p>
 * Consumers implement this to declare how their message values are serialized
 * (for poll/browse display) and deserialized (for corrections).
 * <p>
 * Built-in implementations:
 * <ul>
 *   <li>{@link AvroValueCodec} — for Apache Avro records</li>
 *   <li>{@link ProtoValueCodec} — for Protocol Buffer messages</li>
 * </ul>
 *
 * @param <T> the value type of the consumer record
 */
public interface ValueCodec<T> {

  String toJson(T value);

  T fromJson(String json);
}
