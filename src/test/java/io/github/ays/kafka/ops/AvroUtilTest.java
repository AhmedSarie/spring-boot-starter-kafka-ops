package io.github.ays.kafka.ops;

import static io.github.ays.kafka.ops.AvroUtil.avroToJson;
import static io.github.ays.kafka.ops.AvroUtil.jsonToAvro;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.ays.kafka.ops.avro.TestRecord;
import org.junit.jupiter.api.Test;

class AvroUtilTest {

  @Test
  void jsonToAvroSucceeds() {
    var avroJsonMsg = "{\"name\":\"junit\",\"desc\":\"serialise!\"}";
    var record = jsonToAvro(avroJsonMsg, TestRecord.SCHEMA$);
    assertTrue(record instanceof TestRecord);
    TestRecord testRecord = (TestRecord) record;
    assertEquals("junit", testRecord.getName());
    assertEquals("serialise!", testRecord.getDesc());
  }

  @Test
  void jsonToAvroFails() {
    var avroJsonMsg = "INVALID";
    assertThrows(IllegalArgumentException.class, () -> jsonToAvro(avroJsonMsg, TestRecord.SCHEMA$));
  }

  @Test
  void avroToJsonSucceeds() {
    var avroJsonMsg = "{\"name\":\"junit\",\"desc\":\"serialise!\"}";
    TestRecord junit = TestRecord.newBuilder().setName("junit").setDesc("serialise!").build();
    var record = avroToJson(junit);
    assertEquals(avroJsonMsg, record);
  }
}
