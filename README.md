# Spring Boot Starter Kafka Ops

A Spring Boot starter that adds REST endpoints to your service for Kafka message operations — retry, poll, and send corrections — without any extra infrastructure.

## What it does

When your Kafka consumer fails to process a message, you typically need to manually re-consume it or write custom tooling. This starter gives you that tooling out of the box:

- **Poll** — Inspect a message at a specific topic/partition/offset
- **Retry** — Re-consume a message through your existing consumer logic
- **Correct** — Send a corrected payload directly to your consumer

## Installation

**Maven:**
```xml
<dependency>
    <groupId>io.github.ahmedsarie</groupId>
    <artifactId>spring-boot-starter-kafka-ops</artifactId>
    <version>0.1.1</version>
</dependency>
```

**Gradle:**
```kotlin
implementation("io.github.ahmedsarie:spring-boot-starter-kafka-ops:0.1.1")
```

## Usage

### 1. Enable the REST API

```yaml
kafka:
  ops:
    rest-api:
      enabled: true
```

### 2. Implement `KafkaOpsAwareConsumer` on your Kafka consumers

**Java:**
```java
@Service
public class OrderConsumer implements KafkaOpsAwareConsumer<String, OrderEvent> {

    @KafkaListener(topics = "orders")
    @Override
    public void consume(ConsumerRecord<String, OrderEvent> record) {
        // your existing consumer logic
    }

    @Override
    public String getTopicName() {
        return "orders";
    }
}
```

**Kotlin:**
```kotlin
@Service
class OrderConsumer : KafkaOpsAwareConsumer<String, OrderEvent> {

    @KafkaListener(topics = ["orders"])
    override fun consume(record: ConsumerRecord<String, OrderEvent>) {
        // your existing consumer logic
    }

    override fun getTopicName() = "orders"
}
```

That's it. The endpoints are now available for any topic registered through a `KafkaOpsAwareConsumer` bean.

## REST Endpoints

### Poll a message
```
GET /operational/consumer-retries?topicName=orders&partition=0&offset=42
```
Returns the message value as JSON.

### Retry a message
```
POST /operational/consumer-retries
Content-Type: application/json

{"topic": "orders", "partition": 0, "offset": 42}
```
Re-consumes the message through your consumer's `consume()` method.

### Send a correction
```
POST /operational/consumer-retries/corrections/orders
Content-Type: application/json

{"orderId": "123", "status": "corrected"}
```
Sends the payload directly to your consumer without reading from Kafka.

## Configuration

| Property | Default | Description |
|---|---|---|
| `kafka.ops.rest-api.enabled` | `false` | Enable the REST endpoints |
| `kafka.ops.rest-api.retry-endpoint-url` | `operational/consumer-retries` | Base path for all endpoints |
| `kafka.ops.group-id` | `default-ops-group-id` | Consumer group ID used for polling |
| `kafka.ops.max-poll-interval-ms` | `5000` | Poll timeout in milliseconds |

## Avro support

If your consumer uses Avro, override `getSchema()` to enable automatic JSON-to-Avro conversion for the corrections endpoint:

```java
@Override
public Schema getSchema() {
    return OrderEvent.getClassSchema();
}
```

## Custom container factory

If your consumer uses a custom `KafkaListenerContainerFactory`, override `getContainerName()` so the library uses the correct deserializer configuration:

```java
@Override
public String getContainerName() {
    return "myCustomContainerFactory";
}
```

## Development

```bash
git clone git@github.com:AhmedSarie/spring-boot-starter-kafka-ops.git
cd spring-boot-starter-kafka-ops
./mvnw verify
```

## License

MIT
