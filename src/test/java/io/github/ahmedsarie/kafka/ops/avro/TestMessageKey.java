package io.github.ahmedsarie.kafka.ops.avro;

import org.apache.avro.message.BinaryMessageDecoder;
import org.apache.avro.message.BinaryMessageEncoder;
import org.apache.avro.message.SchemaStore;
import org.apache.avro.specific.SpecificData;

@org.apache.avro.specific.AvroGenerated
public class TestMessageKey extends org.apache.avro.specific.SpecificRecordBase
    implements org.apache.avro.specific.SpecificRecord {

  private static final long serialVersionUID = -8044445405595158014L;

  public static final org.apache.avro.Schema SCHEMA$ = new org.apache.avro.Schema.Parser().parse(
      "{\"type\":\"record\",\"name\":\"TestMessageKey\",\"namespace\":\"io.github.ahmedsarie.kafka.ops.avro\",\"fields\":[{\"name\":\"kafkaMessageKey\",\"type\":\"long\"}]}");

  public static org.apache.avro.Schema getClassSchema() {
    return SCHEMA$;
  }

  private static final SpecificData MODEL$ = new SpecificData();

  private static final BinaryMessageEncoder<TestMessageKey> ENCODER =
      new BinaryMessageEncoder<>(MODEL$, SCHEMA$);

  private static final BinaryMessageDecoder<TestMessageKey> DECODER =
      new BinaryMessageDecoder<>(MODEL$, SCHEMA$);

  public static BinaryMessageEncoder<TestMessageKey> getEncoder() {
    return ENCODER;
  }

  public static BinaryMessageDecoder<TestMessageKey> getDecoder() {
    return DECODER;
  }

  public static BinaryMessageDecoder<TestMessageKey> createDecoder(SchemaStore resolver) {
    return new BinaryMessageDecoder<>(MODEL$, SCHEMA$, resolver);
  }

  public java.nio.ByteBuffer toByteBuffer() throws java.io.IOException {
    return ENCODER.encode(this);
  }

  public static TestMessageKey fromByteBuffer(java.nio.ByteBuffer b) throws java.io.IOException {
    return DECODER.decode(b);
  }

  private long kafkaMessageKey;

  public TestMessageKey() {
  }

  public TestMessageKey(Long kafkaMessageKey) {
    this.kafkaMessageKey = kafkaMessageKey;
  }

  public SpecificData getSpecificData() {
    return MODEL$;
  }

  public org.apache.avro.Schema getSchema() {
    return SCHEMA$;
  }

  public Object get(int field$) {
    switch (field$) {
      case 0:
        return kafkaMessageKey;
      default:
        throw new IndexOutOfBoundsException("Invalid index: " + field$);
    }
  }

  @SuppressWarnings(value = "unchecked")
  public void put(int field$, Object value$) {
    switch (field$) {
      case 0:
        kafkaMessageKey = (Long) value$;
        break;
      default:
        throw new IndexOutOfBoundsException("Invalid index: " + field$);
    }
  }

  public long getKafkaMessageKey() {
    return kafkaMessageKey;
  }

  public void setKafkaMessageKey(long value) {
    this.kafkaMessageKey = value;
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  public static Builder newBuilder(Builder other) {
    if (other == null) {
      return new Builder();
    } else {
      return new Builder(other);
    }
  }

  public static Builder newBuilder(TestMessageKey other) {
    if (other == null) {
      return new Builder();
    } else {
      return new Builder(other);
    }
  }

  @org.apache.avro.specific.AvroGenerated
  public static class Builder extends org.apache.avro.specific.SpecificRecordBuilderBase<TestMessageKey>
      implements org.apache.avro.data.RecordBuilder<TestMessageKey> {

    private long kafkaMessageKey;

    private Builder() {
      super(SCHEMA$, MODEL$);
    }

    private Builder(Builder other) {
      super(other);
      if (isValidValue(fields()[0], other.kafkaMessageKey)) {
        this.kafkaMessageKey = data().deepCopy(fields()[0].schema(), other.kafkaMessageKey);
        fieldSetFlags()[0] = other.fieldSetFlags()[0];
      }
    }

    private Builder(TestMessageKey other) {
      super(SCHEMA$, MODEL$);
      if (isValidValue(fields()[0], other.kafkaMessageKey)) {
        this.kafkaMessageKey = data().deepCopy(fields()[0].schema(), other.kafkaMessageKey);
        fieldSetFlags()[0] = true;
      }
    }

    public long getKafkaMessageKey() {
      return kafkaMessageKey;
    }

    public Builder setKafkaMessageKey(long value) {
      validate(fields()[0], value);
      this.kafkaMessageKey = value;
      fieldSetFlags()[0] = true;
      return this;
    }

    public boolean hasKafkaMessageKey() {
      return fieldSetFlags()[0];
    }

    public Builder clearKafkaMessageKey() {
      fieldSetFlags()[0] = false;
      return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public TestMessageKey build() {
      try {
        TestMessageKey record = new TestMessageKey();
        record.kafkaMessageKey = fieldSetFlags()[0] ? this.kafkaMessageKey : (Long) defaultValue(fields()[0]);
        return record;
      } catch (org.apache.avro.AvroMissingFieldException e) {
        throw e;
      } catch (Exception e) {
        throw new org.apache.avro.AvroRuntimeException(e);
      }
    }
  }

  @SuppressWarnings("unchecked")
  private static final org.apache.avro.io.DatumWriter<TestMessageKey> WRITER$ =
      (org.apache.avro.io.DatumWriter<TestMessageKey>) MODEL$.createDatumWriter(SCHEMA$);

  @Override
  public void writeExternal(java.io.ObjectOutput out) throws java.io.IOException {
    WRITER$.write(this, SpecificData.getEncoder(out));
  }

  @SuppressWarnings("unchecked")
  private static final org.apache.avro.io.DatumReader<TestMessageKey> READER$ =
      (org.apache.avro.io.DatumReader<TestMessageKey>) MODEL$.createDatumReader(SCHEMA$);

  @Override
  public void readExternal(java.io.ObjectInput in) throws java.io.IOException {
    READER$.read(this, SpecificData.getDecoder(in));
  }

  @Override
  protected boolean hasCustomCoders() {
    return true;
  }

  @Override
  public void customEncode(org.apache.avro.io.Encoder out) throws java.io.IOException {
    out.writeLong(this.kafkaMessageKey);
  }

  @Override
  public void customDecode(org.apache.avro.io.ResolvingDecoder in) throws java.io.IOException {
    org.apache.avro.Schema.Field[] fieldOrder = in.readFieldOrderIfDiff();
    if (fieldOrder == null) {
      this.kafkaMessageKey = in.readLong();
    } else {
      for (int i = 0; i < 1; i++) {
        switch (fieldOrder[i].pos()) {
          case 0:
            this.kafkaMessageKey = in.readLong();
            break;
          default:
            throw new java.io.IOException("Corrupt ResolvingDecoder.");
        }
      }
    }
  }
}
