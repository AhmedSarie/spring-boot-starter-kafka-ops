package io.github.ays.kafka.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import io.github.ays.kafka.ops.avro.TestRecord;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ValueCodecTest {

  @Nested
  @DisplayName("AvroMessageCodec")
  class AvroMessageCodecTests {

    private final AvroMessageCodec<TestRecord> codec = new AvroMessageCodec<>(TestRecord.getClassSchema());

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
  @DisplayName("JsonMessageCodec")
  class JsonMessageCodecTests {

    private final JsonMessageCodec<TestPojo> codec = new JsonMessageCodec<>(TestPojo.class);

    @Test
    @DisplayName("toJson converts POJO to JSON string")
    void shouldConvertPojoToJson() {
      // prepare
      var pojo = new TestPojo("junit", 42);

      // when
      var json = codec.toJson(pojo);

      // then
      assertTrue(json.contains("\"name\":\"junit\""));
      assertTrue(json.contains("\"value\":42"));
    }

    @Test
    @DisplayName("fromJson converts JSON string to POJO")
    void shouldConvertJsonToPojo() {
      // prepare
      var json = "{\"name\":\"junit\",\"value\":42}";

      // when
      var pojo = codec.fromJson(json);

      // then
      assertNotNull(pojo);
      assertEquals("junit", pojo.getName());
      assertEquals(42, pojo.getValue());
    }

    @Test
    @DisplayName("round-trip preserves data")
    void shouldRoundTrip() {
      // prepare
      var original = new TestPojo("round-trip", 99);

      // when
      var json = codec.toJson(original);
      var restored = codec.fromJson(json);

      // then
      assertEquals(original.getName(), restored.getName());
      assertEquals(original.getValue(), restored.getValue());
    }

    @Test
    @DisplayName("fromJson throws on malformed JSON")
    void shouldThrowOnMalformedJson() {
      assertThrows(Exception.class, () -> codec.fromJson("not valid json"));
    }
  }

  @Nested
  @DisplayName("ProtoMessageCodec")
  class ProtoMessageCodecTests {

    private final ProtoMessageCodec<Struct> codec = new ProtoMessageCodec<>(Struct.getDefaultInstance());

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

  @Nested
  @DisplayName("StringMessageCodec")
  class StringMessageCodecTests {

    private final StringMessageCodec codec = new StringMessageCodec();

    @Test
    @DisplayName("toJson returns value as-is")
    void shouldReturnValueAsIs() {
      assertEquals("hello", codec.toJson("hello"));
    }

    @Test
    @DisplayName("fromJson returns json as-is")
    void shouldReturnJsonAsIs() {
      assertEquals("{\"key\":1}", codec.fromJson("{\"key\":1}"));
    }

    @Test
    @DisplayName("round-trip preserves data")
    void shouldRoundTrip() {
      // prepare
      var original = "some-string-value";

      // when
      var json = codec.toJson(original);
      var restored = codec.fromJson(json);

      // then
      assertEquals(original, restored);
    }

    @Test
    @DisplayName("handles null")
    void shouldHandleNull() {
      assertNotNull(codec);
      assertEquals(null, codec.toJson(null));
      assertEquals(null, codec.fromJson(null));
    }
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  static class TestPojo {
    private String name;
    private int value;
  }
}
