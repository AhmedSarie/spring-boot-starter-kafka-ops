# Spring Boot Starter Kafka Ops

A Spring Boot starter that adds REST endpoints and an embedded web console to your service for Kafka message operations — poll, retry, corrections, batch browse, and DLT routing — without any extra infrastructure.

## What it does

When your Kafka consumer fails to process a message, you typically need to manually re-consume it or write custom tooling. This starter gives you that tooling out of the box:

- **Poll** — Inspect a message at a specific topic/partition/offset with full metadata (key, headers, timestamp)
- **Batch browse** — Browse multiple messages by offset range or timestamp
- **Retry** — Re-consume a message through your existing consumer logic
- **Correct** — Edit and send a corrected payload directly to your consumer
- **DLT routing** — Route messages from a Dead Letter Topic back to the retry topic automatically
- **Web Console** — Browser-based UI with no separate deployment needed

## Web Console

The library ships an embedded web UI at `/kafka-ops/index.html` — no separate deployment, no CDN, works fully offline.

**Features:**
- **Consumer sidebar** — Tree layout showing main → DLT → retry topics with live message counts. Resizable for long topic names.
- **Poll view** — Poll by partition/offset with full metadata badges (key, timestamp, headers) and a collapsible JSON tree viewer
- **Batch browse** — Browse messages by timestamp or partition/offset with an expandable results table
- **Retry & Correct** — One-click retry or edit JSON and send corrections with diff confirmation popup
- **Drain DLT** — Start DLT routing directly from the sidebar for any configured DLT topic
- **Poll history** — Last 10 partition/offset pairs per topic, persisted across browser sessions

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

### 1. Enable the REST API and console

```yaml
kafka:
  ops:
    rest-api:
      enabled: true    # REST endpoints for programmatic access
    console:
      enabled: true    # Web console UI
```

The REST API and console can be enabled independently. The console requires the REST API to also be enabled — it calls the same endpoints under the hood.

### 2. Implement `KafkaOpsAwareConsumer` on your Kafka consumers

**Minimal (topic name only):**

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

**With per-topic retry backoff:**

```java
@Override
public TopicConfig getTopic() {
    // Fixed: 3 attempts, 1 second between retries
    return TopicConfig.withFixedRetry("orders", 3, 1000);

    // Exponential: 3 attempts, starting at 500ms, doubling each time
    return TopicConfig.withExponentialRetry("orders", 3, 500, 2.0);
}
```

When a `RetryConfig` is declared, the library auto-creates a `DefaultErrorHandler` bean with the configured backoff — you don't need to define one yourself.

**With DLT and retry topics:**

```java
@Override
public TopicConfig getTopic() {
    return TopicConfig.withFixedRetry("orders", 3, 1000);
}

@Override
public TopicConfig getDltTopic() {
    return TopicConfig.of("orders.DLT");
}

@Override
public TopicConfig getRetryTopic() {
    return TopicConfig.withFixedRetry("orders-retry", 5, 2000);
}
```

Declaring both `getDltTopic()` and `getRetryTopic()` enables:
- Automatic routing in the `DefaultErrorHandler`: `orders` → `orders-retry` → `orders.DLT`
- DLT routing via the API: route messages from `orders.DLT` back to `orders-retry` for reprocessing

**Kotlin:**
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

### 3. Open the console

Navigate to `http://localhost:8080/kafka-ops/index.html` — select a consumer from the sidebar to start polling or browsing.

## REST Endpoints

All endpoints are served at a configurable base path (default: `/operational/consumer-retries`).

### List registered consumers
```
GET /operational/consumer-retries/consumers
```
Returns structured consumer info including partition counts, message counts, and DLT/retry sub-topics.

```json
[
  {
    "name": "orders",
    "partitions": 3,
    "messageCount": 1500,
    "dlt": { "name": "orders.DLT", "partitions": 3, "messageCount": 12 },
    "retry": { "name": "orders-retry", "partitions": 3, "messageCount": 0 }
  }
]
```

### Poll a message
```
GET /operational/consumer-retries?topicName=orders&partition=0&offset=42
```
Returns the message with full metadata:
```json
{
  "consumerRecordValue": "{\"orderId\": \"123\"}",
  "key": "order-key",
  "partition": 0,
  "offset": 42,
  "timestamp": 1700000000000,
  "headers": { "traceid": "abc-123" }
}
```

### Batch browse
```
GET /operational/consumer-retries/batch?topicName=orders&partition=0&startOffset=100&limit=20
GET /operational/consumer-retries/batch?topicName=orders&startTimestamp=1700000000000&limit=20
```
Provide either `partition` + `startOffset`, or `startTimestamp` — not both. Returns whatever records are available up to `limit`; if fewer exist, it returns what it finds without waiting.

```json
{
  "records": [
    { "partition": 0, "offset": 100, "timestamp": 1700000000000, "key": "k1", "value": "...", "headers": {} }
  ],
  "hasMore": false
}
```

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

### Start DLT routing
```
POST /operational/consumer-retries/dlt-routing/orders/start
POST /operational/consumer-retries/dlt-routing/orders/start?fromTimestamp=1700000000000
POST /operational/consumer-retries/dlt-routing/orders/start?fromTimestamp=1700000000000&force=true
```
Starts routing messages from `orders.DLT` → `orders-retry`. Requires `kafka.ops.dlt-routing.enabled=true` and the consumer to declare both `getDltTopic()` and `getRetryTopic()`.

- `fromTimestamp` — seek to a specific point in the DLT before routing (useful for replaying a specific incident window)
- `force=true` — reset the `kafka-ops-retry-count` header to 0, allowing exhausted messages to be retried again

The router stops automatically after the topic is idle (configurable), and restarts periodically to catch new DLT messages.

### Console config (used by the UI)
```
GET /kafka-ops/api/config
```
Returns the configured API base path so the UI can resolve endpoints dynamically.

## Configuration

| Property                                         | Default                        | Description                                                                           |
|--------------------------------------------------|--------------------------------|---------------------------------------------------------------------------------------|
| `kafka.ops.rest-api.enabled`                     | `false`                        | Enable the REST endpoints                                                             |
| `kafka.ops.console.enabled`                      | `false`                        | Enable the web console UI                                                             |
| `kafka.ops.rest-api.retry-endpoint-url`          | `operational/consumer-retries` | Base path for all REST endpoints                                                      |
| `kafka.ops.group-id`                             | `default-ops-group-id`         | Consumer group ID used for polling                                                    |
| `kafka.ops.max-poll-interval-ms`                 | `5000`                         | Timeout for single-message poll                                                       |
| `kafka.ops.batch.max-limit`                      | `100`                          | Maximum records returned by batch browse                                              |
| `kafka.ops.dlt-routing.enabled`                  | `false`                        | Enable the DLT router bean                                                            |
| `kafka.ops.dlt-routing.idle-shutdown-minutes`    | `5`                            | Stop the router after this many minutes with no new DLT messages                      |
| `kafka.ops.dlt-routing.restart-interval-minutes` | `30`                           | Restart the router every N minutes to check for new DLT messages                      |
| `kafka.ops.dlt-routing.max-retry-count`          | `3`                            | Skip a DLT message after it has been routed this many times (prevents infinite loops) |

## Auto-configured DefaultErrorHandler

When any `KafkaOpsAwareConsumer` declares a `RetryConfig` (via `TopicConfig.withFixedRetry` / `withExponentialRetry`) or declares DLT/retry topics, the library automatically registers a `DefaultErrorHandler` bean with:

- **Per-topic backoff** — each topic uses its own retry interval and attempt count
- **Automatic routing** — failed records on `orders` go to `orders-retry`; exhausted retries on `orders-retry` go to `orders.DLT`
- **`@ConditionalOnMissingBean`** — if you define your own `DefaultErrorHandler`, the library's bean is skipped entirely

## Avro support

If your consumer uses Avro, override `getSchema()` to enable automatic JSON-to-Avro conversion for the corrections endpoint:

```java
@Override
public Schema getSchema() {
    return OrderEvent.getClassSchema();
}
```

## Custom container factory

If your consumer uses a custom `KafkaListenerContainerFactory`, override `getContainerName()` so the library resolves the correct deserializer configuration:

```java
@Override
public String getContainerName() {
    return "myCustomContainerFactory";
}
```

## Security

The library does not provide authentication, authorization, or CSRF protection. Secure the endpoints and console using your application's existing security infrastructure (Spring Security, API gateway, etc.) — the same way you would secure Swagger UI or Spring Boot Actuator.

If your application uses Spring Security with CSRF protection (enabled by default), the POST endpoints will require a valid CSRF token. If your application does not use Spring Security, consider restricting access to these endpoints to trusted networks since they perform state-changing operations.

The console's static files (`/kafka-ops/**`) are served by Spring Boot's default resource handler whenever the library is on the classpath. The API endpoints require `kafka.ops.rest-api.enabled=true`, so the console UI is non-functional without that property — but the static HTML/JS/CSS files remain accessible. To fully block access, use Spring Security to restrict `/kafka-ops/**`.

## Development

```bash
git clone git@github.com:AhmedSarie/spring-boot-starter-kafka-ops.git
cd spring-boot-starter-kafka-ops
./mvnw verify
```

## License

MIT
