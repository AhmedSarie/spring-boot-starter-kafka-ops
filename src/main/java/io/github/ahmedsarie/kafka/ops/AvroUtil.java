package io.github.ahmedsarie.kafka.ops;

import java.io.ByteArrayOutputStream;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericContainer;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.Decoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.io.JsonEncoder;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.avro.specific.SpecificRecord;

@Slf4j
class AvroUtil {

  private AvroUtil() {
  }

  static <T extends GenericContainer> String avroToJson(T specificRecord) {
    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      DatumWriter<GenericContainer> writer = new SpecificDatumWriter<>(specificRecord.getSchema());
      JsonEncoder encoder = EncoderFactory.get().jsonEncoder(specificRecord.getSchema(), out);
      writer.write(specificRecord, encoder);
      encoder.flush();
      return out.toString();
    } catch (Exception e) {
      log.warn("avro to json conversion failed ", e);
      throw new IllegalStateException("Failed to read SpecificRecord ", e);
    }
  }

  static <T extends SpecificRecord> T jsonToAvro(String avroJson, Schema schema) {
    try {
      Decoder decoder = DecoderFactory.get().jsonDecoder(schema, avroJson);
      DatumReader<T> reader = new SpecificDatumReader<>(schema);
      return reader.read(null, decoder);
    } catch (Exception e) {
      log.warn("json to avro conversion failed ", e);
      throw new IllegalArgumentException("Failed to read json ", e);
    }
  }
}
