# Getting Started

## Installation

=== "Maven"

    ```xml
    <dependency>
        <groupId>io.github.ahmedsarie</groupId>
        <artifactId>spring-boot-starter-kafka-ops</artifactId>
        <version>0.1.7</version>
    </dependency>
    ```

=== "Gradle (Kotlin)"

    ```kotlin
    implementation("io.github.ahmedsarie:spring-boot-starter-kafka-ops:0.1.7")
    ```

=== "Gradle (Groovy)"

    ```groovy
    implementation 'io.github.ahmedsarie:spring-boot-starter-kafka-ops:0.1.7'
    ```

## Step 1: Enable the REST API and console

```yaml
kafka:
  ops:
    rest-api:
      enabled: true
    console:
      enabled: true
```

The REST API and console can be enabled independently. The console requires the REST API to also be enabled — it calls the same endpoints under the hood.

## Step 2: Implement `KafkaOpsAwareConsumer` on your Kafka consumers

=== "Java"

    ```java
    @Service
    public class OrderConsumer implements KafkaOpsAwareConsumer<String, OrderEvent> {

        @KafkaListener(topics = "orders")
        @Override
        public void consume(ConsumerRecord<String, OrderEvent> record) {
            // your existing consumer logic
        }

        @Override
        public TopicConfig getTopic() {
            return TopicConfig.of("orders");
        }
    }
    ```

=== "Kotlin"

    ```kotlin
    @Service
    class OrderConsumer : KafkaOpsAwareConsumer<String, OrderEvent> {

        @KafkaListener(topics = ["orders"])
        override fun consume(record: ConsumerRecord<String, OrderEvent>) {
            // your existing consumer logic
        }

        override fun getTopic() = TopicConfig.of("orders")
    }
    ```

That's it. The library auto-discovers all `KafkaOpsAwareConsumer` beans at startup.

### With DLT and retry topics

If your consumer has a Dead Letter Topic and a retry topic, declare them on `TopicConfig`:

=== "Java"

    ```java
    @Override
    public TopicConfig getTopic() {
        return TopicConfig.of("orders")
            .withDlt("orders.DLT")
            .withRetry("orders-retry");
    }
    ```

=== "Kotlin"

    ```kotlin
    override fun getTopic() = TopicConfig.of("orders")
        .withDlt("orders.DLT")
        .withRetry("orders-retry")
    ```

This enables:

- Browsing and polling DLT/retry topics in the console
- One-click DLT routing (drain DLT messages back to the retry topic)

## Step 3: Open the console

Navigate to [http://localhost:8080/kafka-ops/index.html](http://localhost:8080/kafka-ops/index.html) — select a consumer from the sidebar to start polling or browsing.

## Next steps

- [Configuration](configuration.md) — Tune poll timeouts, batch limits, DLT routing schedules
- [REST API](rest-api.md) — Use the endpoints programmatically
- [DLT Routing](dlt-routing.md) — Set up automatic Dead Letter Topic routing
- [Message Formats](value-formats.md) — Configure Avro, Protobuf, or custom key and value codecs
