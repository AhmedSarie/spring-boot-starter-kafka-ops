package io.github.ays.kafka.ops;

import lombok.RequiredArgsConstructor;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericContainer;
import org.apache.avro.specific.SpecificRecord;

/**
 * {@link ValueCodec} for Apache Avro records.
 *
 * <pre>{@code
 * @Override
 * public ValueCodec<MyAvroRecord> getValueCodec() {
 *     return new AvroValueCodec<>(MyAvroRecord.getClassSchema());
 * }
 * }</pre>
 *
 * @param <T> the Avro record type
 */
@RequiredArgsConstructor
public class AvroValueCodec<T extends SpecificRecord> implements ValueCodec<T> {

  private final Schema schema;

  @Override
  public String toJson(T value) {
    return AvroUtil.avroToJson((GenericContainer) value);
  }

  @Override
  public T fromJson(String json) {
    return AvroUtil.jsonToAvro(json, schema);
  }
}
