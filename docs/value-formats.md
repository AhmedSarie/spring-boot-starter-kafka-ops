# Message Formats

The library auto-detects common key and value types for poll and browse responses: String, Avro, Protobuf, and Jackson-serializable POJOs.

For the **corrections endpoint** — which needs to deserialize JSON back into your value type — you need to provide a codec.

## Avro

=== "Java"

    ```java
    @Override
    public ValueCodec<OrderEvent> getValueCodec() {
        return new AvroValueCodec<>(OrderEvent.getClassSchema());
    }
    ```

=== "Kotlin"

    ```kotlin
    override fun getValueCodec(): ValueCodec<OrderEvent> =
        AvroValueCodec(OrderEvent.getClassSchema())
    ```

Requires `org.apache.avro:avro` on the classpath (declared as `provided` by the library).

## Protobuf

=== "Java"

    ```java
    @Override
    public ValueCodec<OrderEvent> getValueCodec() {
        return new ProtoValueCodec<>(OrderEvent.getDefaultInstance());
    }
    ```

=== "Kotlin"

    ```kotlin
    override fun getValueCodec(): ValueCodec<OrderEvent> =
        ProtoValueCodec(OrderEvent.getDefaultInstance())
    ```

Requires `com.google.protobuf:protobuf-java` and `com.google.protobuf:protobuf-java-util` on the classpath (both declared as `provided`).

## Custom formats

Implement `ValueCodec<T>` directly for any format:

=== "Java"

    ```java
    public class ThriftValueCodec<T> implements ValueCodec<T> {

        @Override
        public String toJson(T value) {
            // serialize your value to JSON string
        }

        @Override
        public T fromJson(String json) {
            // deserialize JSON string to your value type
        }
    }
    ```

=== "Kotlin"

    ```kotlin
    class ThriftValueCodec<T> : ValueCodec<T> {

        override fun toJson(value: T): String {
            // serialize your value to JSON string
        }

        override fun fromJson(json: String): T {
            // deserialize JSON string to your value type
        }
    }
    ```

Then declare it on your consumer:

=== "Java"

    ```java
    @Override
    public ValueCodec<OrderEvent> getValueCodec() {
        return new ThriftValueCodec<>();
    }
    ```

=== "Kotlin"

    ```kotlin
    override fun getValueCodec(): ValueCodec<OrderEvent> = ThriftValueCodec()
    ```

## Key serialization

Keys are auto-detected for built-in types (String, Avro, Protobuf, Jackson POJOs) — no configuration needed for poll and browse.

For non-String key types, override `getKeyCodec()` using the same codecs available for values:

=== "Protobuf key"

    === "Java"

        ```java
        @Override
        public ValueCodec<OrderKey> getKeyCodec() {
            return new ProtoValueCodec<>(OrderKey.getDefaultInstance());
        }
        ```

    === "Kotlin"

        ```kotlin
        override fun getKeyCodec(): ValueCodec<OrderKey> =
            ProtoValueCodec(OrderKey.getDefaultInstance())
        ```

=== "Avro key"

    === "Java"

        ```java
        @Override
        public ValueCodec<OrderKey> getKeyCodec() {
            return new AvroValueCodec<>(OrderKey.getClassSchema());
        }
        ```

    === "Kotlin"

        ```kotlin
        override fun getKeyCodec(): ValueCodec<OrderKey> =
            AvroValueCodec(OrderKey.getClassSchema())
        ```

=== "Custom key"

    === "Java"

        ```java
        @Override
        public ValueCodec<MyKey> getKeyCodec() {
            return new ValueCodec<>() {
                @Override public String toJson(MyKey value) { /* serialize to JSON */ }
                @Override public MyKey fromJson(String json) { /* deserialize from JSON */ }
            };
        }
        ```

    === "Kotlin"

        ```kotlin
        override fun getKeyCodec(): ValueCodec<MyKey> = object : ValueCodec<MyKey> {
            override fun toJson(value: MyKey): String { /* serialize to JSON */ }
            override fun fromJson(json: String): MyKey { /* deserialize from JSON */ }
        }
        ```
