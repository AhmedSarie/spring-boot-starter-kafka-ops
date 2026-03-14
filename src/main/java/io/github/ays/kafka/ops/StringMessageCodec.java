package io.github.ays.kafka.ops;

/**
 * Passthrough {@link MessageCodec} for {@code String} types.
 * Returns the value as-is for both serialization and deserialization.
 */
class StringMessageCodec implements MessageCodec<String> {

  @Override
  public String toJson(String value) {
    return value;
  }

  @Override
  public String fromJson(String json) {
    return json;
  }
}
