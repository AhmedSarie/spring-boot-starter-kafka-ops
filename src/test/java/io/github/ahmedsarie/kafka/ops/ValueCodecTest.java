package io.github.ahmedsarie.kafka.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import io.github.ahmedsarie.kafka.ops.avro.TestRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ValueCodecTest {

  @Nested
  @DisplayName("AvroValueCodec")
  class AvroValueCodecTests {

    private final AvroValueCodec<TestRecord> codec = new AvroValueCodec<>(TestRecord.getClassSchema());

    @Test
    @DisplayName("toJson converts Avro record to JSON string")
    void shouldConvertAvroToJson() {
      // prepare
      var record = TestRecord.newBuilder().setName("junit").setDesc("test").build();

      // when
      var json = codec.toJson(record);

      // then
      assertEquals("{\"name\":\"junit\",\"desc\":\"test\"}", json);
    }

    @Test
    @DisplayName("fromJson converts JSON string to Avro record")
    void shouldConvertJsonToAvro() {
      // prepare
      var json = "{\"name\":\"junit\",\"desc\":\"test\"}";

      // when
      var record = codec.fromJson(json);

      // then
      assertNotNull(record);
      assertEquals("junit", record.getName().toString());
      assertEquals("test", record.getDesc().toString());
    }

    @Test
    @DisplayName("round-trip preserves data")
    void shouldRoundTrip() {
      // prepare
      var original = TestRecord.newBuilder().setName("junit").setDesc("round-trip").build();

      // when
      var json = codec.toJson(original);
      var restored = codec.fromJson(json);

      // then
      assertEquals(original.getName(), restored.getName());
      assertEquals(original.getDesc(), restored.getDesc());
    }

    @Test
    @DisplayName("fromJson throws on malformed JSON")
    void shouldThrowOnMalformedJson() {
      assertThrows(IllegalArgumentException.class, () -> codec.fromJson("not valid json"));
    }
  }

  @Nested
  @DisplayName("ProtoValueCodec")
  class ProtoValueCodecTests {

    private final ProtoValueCodec<Struct> codec = new ProtoValueCodec<>(Struct.getDefaultInstance());

    @Test
    @DisplayName("toJson converts proto message to JSON string")
    void shouldConvertProtoToJson() {
      // prepare
      var struct = Struct.newBuilder()
          .putFields("name", Value.newBuilder().setStringValue("junit").build())
          .build();

      // when
      var json = codec.toJson(struct);

      // then
      assertTrue(json.contains("\"name\""));
      assertTrue(json.contains("junit"));
    }

    @Test
    @DisplayName("fromJson converts JSON string to proto message")
    void shouldConvertJsonToProto() {
      // prepare
      var json = "{\"name\": \"junit\"}";

      // when
      var struct = codec.fromJson(json);

      // then
      assertNotNull(struct);
      assertTrue(struct.getFieldsMap().containsKey("name"));
      assertEquals("junit", struct.getFieldsMap().get("name").getStringValue());
    }

    @Test
    @DisplayName("round-trip preserves data")
    void shouldRoundTrip() {
      // prepare
      var original = Struct.newBuilder()
          .putFields("id", Value.newBuilder().setNumberValue(42).build())
          .putFields("name", Value.newBuilder().setStringValue("round-trip").build())
          .build();

      // when
      var json = codec.toJson(original);
      var restored = codec.fromJson(json);

      // then
      assertEquals(42, restored.getFieldsMap().get("id").getNumberValue());
      assertEquals("round-trip", restored.getFieldsMap().get("name").getStringValue());
    }

    @Test
    @DisplayName("fromJson ignores unknown fields")
    void shouldIgnoreUnknownFields() {
      // prepare
      var json = "{\"name\": \"junit\", \"unknownField\": 123}";

      // when
      var struct = codec.fromJson(json);

      // then
      assertNotNull(struct);
      assertTrue(struct.getFieldsMap().containsKey("name"));
    }
  }
}
