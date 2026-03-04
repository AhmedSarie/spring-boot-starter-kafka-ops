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
- **Consumer sidebar** — Tree layout showing main → DLT → retry topics. Resizable for long topic names. Shows DLT routing config (cron, max cycles, idle timeout) when enabled.
- **Topic dashboard** — Partition count and message count badges displayed in the topic heading
- **Poll view** — Poll by partition/offset with full metadata badges (key, timestamp, headers) and a collapsible JSON tree viewer
- **Batch browse** — Browse messages by timestamp (defaults to last hour) or partition/offset with an expandable results table and collapsible headers
- **Retry & Correct** — One-click retry or edit JSON and send corrections with diff confirmation popup
- **Drain DLT** — Start DLT routing directly from the sidebar for any configured DLT topic
- **Poll history** — Last 10 partition/offset pairs per topic in both Poll and Browse views, persisted across browser sessions

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

**With DLT and retry topics:**

```java
@Override
public TopicConfig getTopic() {
    return TopicConfig.of("orders")
        .withDlt("orders.DLT")
        .withRetry("orders-retry");
}
```

Declaring `withDlt()` and `withRetry()` enables DLT routing via the API: route messages from `orders.DLT` back to `orders-retry` for reprocessing.

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
```
Starts routing messages from `orders.DLT` → `orders-retry`. Requires `kafka.ops.dlt-routing.enabled=true` and the consumer to declare both `withDlt()` and `withRetry()` on its `TopicConfig`.

The router uses a fixed consumer group (`{group-id}-dlt-router`) so it is safe in multi-pod deployments — the broker handles partition assignment across pods. Only messages that existed before the trigger time are routed; newer messages are left for the next run. The router stops automatically after the topic is idle (configurable), and restarts periodically via cron to catch new DLT messages.

Each time a message is routed from DLT → retry, the router stamps a `kafka-ops-dlt-cycle` header and increments it on every cycle. Once the cycle count reaches `max-cycles`, the message is skipped (acknowledged without routing) to prevent infinite loops.

**Tuning automatic retry duration:** The total time a message keeps being retried automatically is roughly `max-cycles × restart-cron interval`. For example, with `max-cycles=10` and `restart-cron` set to every 6 hours (`0 0 */6 * * *`), a message is retried for up to ~2.5 days before being permanently skipped. To stop retries sooner, deploy with fewer cycles or a higher cron frequency. To discard DLT data immediately, involve Kafka admins to delete the topic data or adjust the topic retention policy.

**DLT header handling:** Spring Kafka's `DeadLetterPublishingRecoverer` writes headers like `kafka_dlt-original-offset`, `kafka_dlt-original-partition`, and `kafka_dlt-original-timestamp` as binary (BigEndian int/long). The library automatically decodes these into readable numbers in both the API responses and the console. The `kafka_dlt-exception-stacktrace` header is filtered out from responses to keep payloads compact.

### Console config (used by the UI)
```
GET /kafka-ops/api/config
```
Returns the configured API base path and DLT routing settings so the UI can resolve endpoints and display configuration dynamically.

## Configuration

| Property                                      | Default                        | Description                                                                                                                |
|-----------------------------------------------|--------------------------------|----------------------------------------------------------------------------------------------------------------------------|
| `kafka.ops.rest-api.enabled`                  | `false`                        | Enable the REST endpoints                                                                                                  |
| `kafka.ops.console.enabled`                   | `false`                        | Enable the web console UI                                                                                                  |
| `kafka.ops.rest-api.retry-endpoint-url`       | `operational/consumer-retries` | Base path for all REST endpoints                                                                                           |
| `kafka.ops.group-id`                          | `default-ops-group-id`         | Consumer group ID used for polling                                                                                         |
| `kafka.ops.max-poll-interval-ms`              | `5000`                         | Timeout for single-message poll                                                                                            |
| `kafka.ops.batch.max-limit`                   | `100`                          | Maximum records returned by batch browse                                                                                   |
| `kafka.ops.dlt-routing.enabled`               | `false`                        | Enable the DLT router bean                                                                                                 |
| `kafka.ops.dlt-routing.idle-shutdown-seconds` | `10`                           | Stop the router after this many seconds with no new DLT messages                                                           |
| `kafka.ops.dlt-routing.restart-cron`          | `0 */30 * * * *`               | Cron expression controlling when the router restarts to check for new DLT messages. Use `-` to disable automatic restarts. |
| `kafka.ops.dlt-routing.max-cycles`            | `10`                           | Skip a DLT message after it has been routed this many times (prevents infinite loops)                                      |

## Avro support

If your consumer uses Avro, override `getSchema()` to enable automatic JSON-to-Avro conversion for the corrections endpoint:

```java
@Override
public Schema getSchema() {
    return OrderEvent.getClassSchema();
}
```

## Custom container factory

If your consumer uses a custom `KafkaListenerContainerFactory`, override `getContainer()` so the library resolves the correct deserializer configuration:

```java
@Override
public ContainerConfig getContainer() {
    return ContainerConfig.of("myCustomContainerFactory");
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
