# Spring Boot Starter Kafka Ops

A Spring Boot starter that adds REST endpoints and an embedded web console to your service for Kafka message operations — poll, retry, and send corrections — without any extra infrastructure.

## What it does

When your Kafka consumer fails to process a message, you typically need to manually re-consume it or write custom tooling. This starter gives you that tooling out of the box:

- **Poll** — Inspect a message at a specific topic/partition/offset
- **Retry** — Re-consume a message through your existing consumer logic
- **Correct** — Edit and send a corrected payload directly to your consumer
- **Web Console** — Browser-based UI with no separate deployment needed

## Web Console

The library ships an embedded web UI at `/kafka-ops/index.html` — no separate deployment, no CDN, works fully offline.

![Dark theme console with sidebar, JSON viewer, and correction editor](docs/console-screenshot.png)

**Features:**
- **Consumer sidebar** — Auto-discovers registered topics, resizable for long names
- **Message polling** — Poll by partition/offset with collapsible JSON tree viewer
- **Retry & Correct** — One-click retry or edit JSON and send corrections with diff confirmation
- **Poll history** — Last 10 partition/offset pairs per topic, persisted across sessions
- **Error messages** — Actionable error messages from the backend, not just HTTP status codes

The console uses [Mithril.js](https://mithril.js.org) and [Pico CSS](https://picocss.com), both vendored in the JAR. No Node.js, no npm, no build step, no runtime internet required.

## Installation

**Maven:**
```xml
<dependency>
    <groupId>io.github.ahmedsarie</groupId>
    <artifactId>spring-boot-starter-kafka-ops</artifactId>
    <version>0.1.2</version>
</dependency>
```

**Gradle:**
```kotlin
implementation("io.github.ahmedsarie:spring-boot-starter-kafka-ops:0.1.2")
```

## Usage

### 1. Enable the REST API

```yaml
kafka:
  ops:
    rest-api:
      enabled: true
```

This enables both the REST endpoints and the web console.

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

That's it. The endpoints and console are now available for any topic registered through a `KafkaOpsAwareConsumer` bean.

### 3. Open the console

Navigate to `http://localhost:8080/kafka-ops/index.html` — select a consumer from the sidebar, enter partition and offset, and poll away.

## REST Endpoints

All endpoints are served at a configurable base path (default: `/operational/consumer-retries`).

### List registered consumers
```
GET /operational/consumer-retries/consumers
```
Returns the list of registered consumer topic names.

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

### Console config (used by the UI)
```
GET /kafka-ops/api/config
```
Returns the configured API base path so the UI can discover endpoints dynamically.

## Configuration

| Property | Default | Description |
|---|---|---|
| `kafka.ops.rest-api.enabled` | `false` | Enable the REST endpoints and web console |
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

## Security

The library does not provide authentication, authorization, or CSRF protection. Secure the endpoints and console using your application's existing security infrastructure (Spring Security, API gateway, etc.) — the same way you would secure Swagger UI or Spring Boot Actuator.

If your application uses Spring Security with CSRF protection (enabled by default), the retry and correction POST endpoints will require a valid CSRF token. If your application does not use Spring Security, consider restricting access to these endpoints to trusted networks since they perform state-changing operations.

The console's static files (`/kafka-ops/**`) are served by Spring Boot's default resource handler whenever the library is on the classpath. The API endpoints require `kafka.ops.rest-api.enabled=true`, so the console UI is non-functional without that property — but the static HTML/JS/CSS files remain accessible. To fully block access to the static files, use Spring Security to restrict `/kafka-ops/**`.

## Development

```bash
git clone git@github.com:AhmedSarie/spring-boot-starter-kafka-ops.git
cd spring-boot-starter-kafka-ops
./mvnw verify
```

## License

MIT
