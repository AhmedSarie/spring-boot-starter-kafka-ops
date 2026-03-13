package io.github.ays.kafka.ops.avro;

import org.apache.avro.message.BinaryMessageDecoder;
import org.apache.avro.message.BinaryMessageEncoder;
import org.apache.avro.message.SchemaStore;
import org.apache.avro.specific.SpecificData;

public class TestRecord extends org.apache.avro.specific.SpecificRecordBase
    implements org.apache.avro.specific.SpecificRecord {

  private static final long serialVersionUID = -1217865813249356411L;

  public static final org.apache.avro.Schema SCHEMA$ = new org.apache.avro.Schema.Parser().parse(
      "{\"type\":\"record\",\"name\":\"TestRecord\",\"namespace\":\"io.github.ays.kafka.ops.avro\",\"fields\":[{\"name\":\"name\",\"type\":{\"type\":\"string\",\"avro.java.string\":\"String\"}},{\"name\":\"desc\",\"type\":{\"type\":\"string\",\"avro.java.string\":\"String\"}}]}");

  public static org.apache.avro.Schema getClassSchema() {
    return SCHEMA$;
  }

  private static final SpecificData MODEL$ = new SpecificData();

  private static final BinaryMessageEncoder<TestRecord> ENCODER = new BinaryMessageEncoder<>(MODEL$, SCHEMA$);

  private static final BinaryMessageDecoder<TestRecord> DECODER = new BinaryMessageDecoder<>(MODEL$, SCHEMA$);

  public static BinaryMessageEncoder<TestRecord> getEncoder() {
    return ENCODER;
  }

  public static BinaryMessageDecoder<TestRecord> getDecoder() {
    return DECODER;
  }

  public static BinaryMessageDecoder<TestRecord> createDecoder(SchemaStore resolver) {
    return new BinaryMessageDecoder<>(MODEL$, SCHEMA$, resolver);
  }

  public java.nio.ByteBuffer toByteBuffer() throws java.io.IOException {
    return ENCODER.encode(this);
  }

  public static TestRecord fromByteBuffer(java.nio.ByteBuffer b) throws java.io.IOException {
    return DECODER.decode(b);
  }

  private String name;
  private String desc;

  public TestRecord() {
  }

  public TestRecord(String name, String desc) {
    this.name = name;
    this.desc = desc;
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
        return name;
      case 1:
        return desc;
      default:
        throw new IndexOutOfBoundsException("Invalid index: " + field$);
    }
  }

  public void put(int field$, Object value$) {
    switch (field$) {
      case 0:
        name = value$ != null ? value$.toString() : null;
        break;
      case 1:
        desc = value$ != null ? value$.toString() : null;
        break;
      default:
        throw new IndexOutOfBoundsException("Invalid index: " + field$);
    }
  }

  public String getName() {
    return name;
  }

  public void setName(String value) {
    this.name = value;
  }

  public String getDesc() {
    return desc;
  }

  public void setDesc(String value) {
    this.desc = value;
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

  public static Builder newBuilder(TestRecord other) {
    if (other == null) {
      return new Builder();
    } else {
      return new Builder(other);
    }
  }

  @org.apache.avro.specific.AvroGenerated
  public static class Builder extends org.apache.avro.specific.SpecificRecordBuilderBase<TestRecord>
      implements org.apache.avro.data.RecordBuilder<TestRecord> {

    private String name;
    private String desc;

    private Builder() {
      super(SCHEMA$, MODEL$);
    }

    private Builder(Builder other) {
      super(other);
      if (isValidValue(fields()[0], other.name)) {
        this.name = data().deepCopy(fields()[0].schema(), other.name);
        fieldSetFlags()[0] = other.fieldSetFlags()[0];
      }
      if (isValidValue(fields()[1], other.desc)) {
        this.desc = data().deepCopy(fields()[1].schema(), other.desc);
        fieldSetFlags()[1] = other.fieldSetFlags()[1];
      }
    }

    private Builder(TestRecord other) {
      super(SCHEMA$, MODEL$);
      if (isValidValue(fields()[0], other.name)) {
        this.name = data().deepCopy(fields()[0].schema(), other.name);
        fieldSetFlags()[0] = true;
      }
      if (isValidValue(fields()[1], other.desc)) {
        this.desc = data().deepCopy(fields()[1].schema(), other.desc);
        fieldSetFlags()[1] = true;
      }
    }

    public String getName() {
      return name;
    }

    public Builder setName(String value) {
      validate(fields()[0], value);
      this.name = value;
      fieldSetFlags()[0] = true;
      return this;
    }

    public boolean hasName() {
      return fieldSetFlags()[0];
    }

    public Builder clearName() {
      name = null;
      fieldSetFlags()[0] = false;
      return this;
    }

    public String getDesc() {
      return desc;
    }

    public Builder setDesc(String value) {
      validate(fields()[1], value);
      this.desc = value;
      fieldSetFlags()[1] = true;
      return this;
    }

    public boolean hasDesc() {
      return fieldSetFlags()[1];
    }

    public Builder clearDesc() {
      desc = null;
      fieldSetFlags()[1] = false;
      return this;
    }

    @Override
    public TestRecord build() {
      try {
        TestRecord record = new TestRecord();
        record.name = fieldSetFlags()[0] ? this.name : (String) defaultValue(fields()[0]);
        record.desc = fieldSetFlags()[1] ? this.desc : (String) defaultValue(fields()[1]);
        return record;
      } catch (org.apache.avro.AvroMissingFieldException e) {
        throw e;
      } catch (Exception e) {
        throw new org.apache.avro.AvroRuntimeException(e);
      }
    }
  }

  @SuppressWarnings("unchecked")
  private static final org.apache.avro.io.DatumWriter<TestRecord> WRITER$ = (org.apache.avro.io.DatumWriter<TestRecord>) MODEL$.createDatumWriter(
      SCHEMA$);

  @Override
  public void writeExternal(java.io.ObjectOutput out) throws java.io.IOException {
    WRITER$.write(this, SpecificData.getEncoder(out));
  }

  @SuppressWarnings("unchecked")
  private static final org.apache.avro.io.DatumReader<TestRecord> READER$ = (org.apache.avro.io.DatumReader<TestRecord>) MODEL$.createDatumReader(
      SCHEMA$);

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
    out.writeString(this.name);
    out.writeString(this.desc);
  }

  @Override
  public void customDecode(org.apache.avro.io.ResolvingDecoder in) throws java.io.IOException {
    org.apache.avro.Schema.Field[] fieldOrder = in.readFieldOrderIfDiff();
    if (fieldOrder == null) {
      this.name = in.readString();
      this.desc = in.readString();
    } else {
      for (int i = 0; i < 2; i++) {
        switch (fieldOrder[i].pos()) {
          case 0:
            this.name = in.readString();
            break;
          case 1:
            this.desc = in.readString();
            break;
          default:
            throw new java.io.IOException("Corrupt ResolvingDecoder.");
        }
      }
    }
  }
}
