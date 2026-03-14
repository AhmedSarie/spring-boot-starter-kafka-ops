# Message Formats

The library **auto-resolves codecs** for all built-in types at registration time — no manual override needed for most consumers:

| Type | Auto-resolved codec |
|------|-------------------|
| Plain POJO (Jackson) | `JsonMessageCodec` |
| `String` | `StringMessageCodec` |
| Avro `SpecificRecord` | `AvroMessageCodec` |
| Protobuf `Message` | `ProtoMessageCodec` |

This applies symmetrically to both **keys** and **values**. For example, a consumer with `<ShipmentKey, ShipmentEvent>` (both POJOs) needs no codec overrides — the library detects both types and creates `JsonMessageCodec` instances automatically.

## Explicit codec overrides

You can still override `getKeyCodec()` or `getValueCodec()` if needed (e.g. for custom formats). Explicit codecs take precedence over auto-resolution.

### Avro

=== "Java"

    ```java
    @Override
    public MessageCodec<OrderEvent> getValueCodec() {
        return new AvroMessageCodec<>(OrderEvent.getClassSchema());
    }
    ```

=== "Kotlin"

    ```kotlin
    override fun getValueCodec(): MessageCodec<OrderEvent> =
        AvroMessageCodec(OrderEvent.getClassSchema())
    ```

Requires `org.apache.avro:avro` on the classpath (declared as `provided` by the library).

### Protobuf

=== "Java"

    ```java
    @Override
    public MessageCodec<OrderEvent> getValueCodec() {
        return new ProtoMessageCodec<>(OrderEvent.getDefaultInstance());
    }
    ```

=== "Kotlin"

    ```kotlin
    override fun getValueCodec(): MessageCodec<OrderEvent> =
        ProtoMessageCodec(OrderEvent.getDefaultInstance())
    ```

Requires `com.google.protobuf:protobuf-java` and `com.google.protobuf:protobuf-java-util` on the classpath (both declared as `provided`).

## Custom formats

Implement `MessageCodec<T>` directly for any format:

=== "Java"

    ```java
    public class ThriftMessageCodec<T> implements MessageCodec<T> {

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
    class ThriftMessageCodec<T> : MessageCodec<T> {

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
    public MessageCodec<OrderEvent> getValueCodec() {
        return new ThriftMessageCodec<>();
    }
    ```

=== "Kotlin"

    ```kotlin
    override fun getValueCodec(): MessageCodec<OrderEvent> = ThriftMessageCodec()
    ```

## Key codec overrides

Keys are auto-resolved just like values. Override `getKeyCodec()` only when you need a custom codec:

=== "Protobuf key"

    === "Java"

        ```java
        @Override
        public MessageCodec<OrderKey> getKeyCodec() {
            return new ProtoMessageCodec<>(OrderKey.getDefaultInstance());
        }
        ```

    === "Kotlin"

        ```kotlin
        override fun getKeyCodec(): MessageCodec<OrderKey> =
            ProtoMessageCodec(OrderKey.getDefaultInstance())
        ```

=== "Custom key"

    === "Java"

        ```java
        @Override
        public MessageCodec<MyKey> getKeyCodec() {
            return new MessageCodec<>() {
                @Override public String toJson(MyKey value) { /* serialize to JSON */ }
                @Override public MyKey fromJson(String json) { /* deserialize from JSON */ }
            };
        }
        ```

    === "Kotlin"

        ```kotlin
        override fun getKeyCodec(): MessageCodec<MyKey> = object : MessageCodec<MyKey> {
            override fun toJson(value: MyKey): String { /* serialize to JSON */ }
            override fun fromJson(json: String): MyKey { /* deserialize from JSON */ }
        }
        ```
