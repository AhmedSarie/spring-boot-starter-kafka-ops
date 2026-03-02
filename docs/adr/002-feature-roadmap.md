# ADR-002: Feature Roadmap

## Status

Proposed

## Context

The library currently supports single-message operations: poll by exact offset, retry, and correction. In practice, L1 support engineers need to browse multiple messages, understand consumer state, have richer message metadata, and trigger DLT draining after outages. Service developers need standardized error handling and retry pipelines without writing boilerplate.

This ADR defines the roadmap in four phases, informed by GCP Pub/Sub admin capabilities, standalone Kafka UIs (Kafdrop, AKHQ, Kafka UI), and production incident workflows.

### Competitive positioning

| Capability                                  | Kafdrop / AKHQ / Kafka UI | kafka-ops (this library)       |
|---------------------------------------------|---------------------------|--------------------------------|
| Retry through app's business logic          | No                        | **Yes**                        |
| Edit & correct, re-process through app      | No                        | **Yes**                        |
| Auto-discovers consumers at startup         | No                        | **Yes**                        |
| Understands app's serialization natively    | Schema Registry only      | **Yes (same ConsumerFactory)** |
| Zero deployment (embedded in service)       | No (separate infra)       | **Yes**                        |
| Trigger DLT draining on demand              | No                        | **Yes**                        |
| Auto-configured error handler + DLT routing | No                        | **Yes**                        |
| Topic/schema/ACL management                 | Yes                       | No (out of scope)              |
| Multi-cluster support                       | Yes                       | **Yes (via getContainerName)** |

**What we are**: An embedded ops tool for message-level investigation and remediation, with built-in error handling and DLT routing — leveraging the app's own consumer logic.

**What we are not**: A Kafka cluster management platform. Topic CRUD, schema registry, Connect, ACLs, lag dashboards belong in standalone tools.

---

## Decision

### Phase 1: Batch Browse, Enriched Responses, Enriched Consumers API

#### 1.1 Enrich poll response with headers, key, and timestamp

**Current state**: `KafkaPollResponse` returns only `consumerRecordValue` (a string).

**Change**: Add `key`, `timestamp`, `partition`, `offset`, and `headers` to the poll response. Backward-compatible (new fields with Jackson defaults).

```json
{
  "consumerRecordValue": "{...}",
  "key": "order-123",
  "partition": 0,
  "offset": 42,
  "timestamp": 1709251200000,
  "headers": { "traceid": "abc-123", "retry-count": "3" }
}
```

**Effort**: Small.

#### 1.2 Enriched consumers endpoint

**Current state**: `GET /{base-path}/consumers` returns `Set<String>` — flat list of topic names.

**Change**: Enrich the same endpoint to return structured objects with metadata. The response is built from the `KafkaOpsAwareConsumer` beans directly (not the flat registry keys), so the main topic, DLT, and retry topics are naturally correlated through the bean that declares them. The flat `registryMap` keys are used for `find()` lookups; the consumer bean is the source of truth for topic relationships.

Message counts derived from broker metadata (`endOffsets - beginningOffsets`) at query time. DLT and retry fields present only when the consumer declares them. One API call gives the UI everything needed to render the sidebar tree.

```json
[
  {
    "name": "orders",
    "partitions": 6,
    "messageCount": 82090,
    "dlt": {
      "name": "orders.DLT",
      "messageCount": 12
    },
    "retry": {
      "name": "orders-retry",
      "messageCount": 3
    }
  }
]
```

Breaking change on the response shape, but the only consumer of this API is our own UI.

**Effort**: Small.

#### 1.3 Batch browse by timestamp or offset range

New endpoint for fetching N messages from a starting point:

```
GET /{base-path}/batch?topicName={topic}&startTimestamp={epoch_ms}&limit={n}
GET /{base-path}/batch?topicName={topic}&partition={p}&startOffset={offset}&limit={n}
```

Pagination via `offset + 1` as next `startOffset`. Client-side filtering in the UI. Hard cap on `limit` (configurable, default 100).

**Implementation notes**:
- Remove `max.poll.records=1` restriction and filter results in code (existing single-poll already validates partition+offset)
- Timestamp-based seeking uses `kafkaConsumer.offsetsForTimes()`
- `ManualKafkaConsumer.poll()` is `synchronized` — batch operations hold the lock longer (serialized per-topic, documented)

**UI**: New route `#!/browse` with a table of records, text filter input, click to expand JsonViewer. Existing retry/correct actions apply to the selected row.

**Effort**: Medium (3-5 days).

---

### Phase 2: Interface Redesign + DLT/Retry Topic Awareness

#### 2.1 `TopicConfig` value object

A new public class that wraps a topic name with optional retry configuration. Used consistently across all topic declarations — main, DLT, and retry.

```java
public class TopicConfig {
    private final String name;
    private final RetryConfig retryConfig;  // null = no retry config

    public static TopicConfig of(String name) { ... }
    public static TopicConfig withFixedRetry(String name, int maxAttempts, long intervalMs) { ... }
    public static TopicConfig withExponentialRetry(String name, int maxAttempts, long intervalMs, double multiplier) { ... }
}
```

#### 2.2 Interface changes to `KafkaOpsAwareConsumer`

Breaking change (library not yet in use, acceptable).

```java
public interface KafkaOpsAwareConsumer<K, T> {

    void consume(ConsumerRecord<K, T> consumerRecord);

    TopicConfig getTopic();                                // mandatory (was getTopicName())

    default TopicConfig getDltTopic() { return null; }     // optional
    default TopicConfig getRetryTopic() { return null; }   // optional

    default String getContainerName() { return null; }     // unchanged
    default Schema getSchema() { return null; }            // unchanged
}
```

**Configuration split**:
- **Interface** = consumer-level config (topic names, retry behavior) — owned by service developer, lives in code, reviewed in PRs
- **Properties (YAML)** = operational config (enable/disable features, timeouts, intervals) — environment-specific, can differ between staging/prod

#### 2.3 Registry extension

`KafkaOpsConsumerRegistry` registers DLT and retry topics in the same `registryMap` using the same `ContainerFactory` and deserializers as the main topic. All existing APIs (poll, browse, retry, correct) work on DLT/retry topics automatically with zero controller changes.

The enriched consumers endpoint (Phase 1.2) builds its response from the `KafkaOpsAwareConsumer` beans directly — the bean IS the correlation between main, DLT, and retry topics. No matching or guessing needed.

The sidebar renders the full tree from a single API call:

```
orders
  orders.DLT          12 messages         [Drain DLT]
  orders-retry         3 messages
```

#### 2.4 Service developer usage (minimal)

```java
@Service
public class OrderConsumer implements KafkaOpsAwareConsumer<String, OrderEvent> {

    @KafkaListener(topics = {"orders", "orders-retry"})
    @Override
    public void consume(ConsumerRecord<String, OrderEvent> record) {
        // business logic — same for original and retry
    }

    @Override
    public TopicConfig getTopic() {
        return TopicConfig.withExponentialRetry("orders", 3, 500, 2.0);
    }

    @Override
    public TopicConfig getDltTopic() {
        return TopicConfig.of("orders.DLT");  // no retry on DLT — it's a parking lot
    }

    @Override
    public TopicConfig getRetryTopic() {
        return TopicConfig.withFixedRetry("orders-retry", 5, 1000);
    }
}
```

The service developer declares topic names and retry config. The library handles everything else.

---

### Phase 3: Default Error Handler

#### 3.1 Auto-configured `DefaultErrorHandler` bean

Created with `@ConditionalOnMissingBean(DefaultErrorHandler.class)`. The service developer's own `DefaultErrorHandler` bean always takes precedence.

The library only creates the error handler when at least one consumer has retry config or DLT/retry topics declared. If no consumer configures any of these, the library does not create an error handler bean and Spring Kafka's default behavior applies unchanged.

#### 3.2 Per-topic backoff

Uses `RetryConfig` from each topic's `TopicConfig`. The error handler uses Spring Kafka's `BackOffFunction` to resolve per-topic backoff at runtime. Topics without retry config get Spring Kafka default behavior.

Supports both fixed and exponential backoff, configured per topic through the interface:

```java
// Fixed: 5 retries, 1 second apart
TopicConfig.withFixedRetry("orders-retry", 5, 1000)

// Exponential: 3 retries, starting at 500ms, multiplier 2.0 (500ms, 1s, 2s)
TopicConfig.withExponentialRetry("orders", 3, 500, 2.0)
```

#### 3.3 Recoverer routing logic

The library configures a `DeadLetterPublishingRecoverer` that routes exhausted records based on the consumer's topic declarations:

| Source topic   | Retries exhaust | Route to                                                                   |
|----------------|-----------------|----------------------------------------------------------------------------|
| Original topic | Yes             | Retry topic (if configured), else DLT (if configured), else Spring default |
| Retry topic    | Yes             | DLT (if configured), else Spring default                                   |
| DLT            | N/A             | No error handler retry — DLT is a parking lot                              |

#### 3.4 Override

The service developer defines their own `DefaultErrorHandler` bean to override the library's default entirely. This covers advanced use cases: multiple retry topics, custom backoff strategies, circuit breakers, etc.

---

### Phase 4: DLT Router (Byte-Level Forwarder)

#### 4.1 Design philosophy

The library abstracts DLT-to-retry routing entirely. The service developer declares topic names; the library creates and manages the DLT consumer and producer internally. The service developer does NOT write a DLT handler, a `@KafkaListener` for the DLT, or any routing code.

The DLT is a parking lot. The library's router drains it on demand or on a schedule, forwarding messages to the retry topic where the app's own `consume()` method (listening on both the original and retry topics) reprocesses them.

#### 4.2 `KafkaOpsDltRouter` (package-private)

For each consumer that declares BOTH DLT + retry topics, the library creates:

- A `ConcurrentMessageListenerContainer<byte[], byte[]>` with `ByteArrayDeserializer` — created programmatically (not `@KafkaListener`, not a custom poll loop). Separate from the browsable registry consumer, used exclusively for byte-level forwarding.
- A `KafkaTemplate<byte[], byte[]>` with `ByteArraySerializer` — producer config derived from the consumer's bootstrap/security properties (same pattern as `KafkaOpsConsumerRegistry` uses to create manual consumers).

The container uses `autoStartup = false`.

#### 4.3 Byte-level routing logic

- Reads each DLT record as raw bytes
- Copies key, value, and ALL headers verbatim to a `ProducerRecord` for the retry topic
- Adds/increments a `kafka-ops-retry-count` header
- If retry count exceeds configurable max (default 3): logs at ERROR level and skips the record (offset committed, message effectively parked)
- Manual acknowledgment (`AckMode.RECORD`) — offset committed only after successful send to retry topic. If send fails, offset is not committed and the message is re-delivered on next poll.

Zero serialization risk — bytes in, bytes out. No schema knowledge required.

#### 4.4 Lifecycle management

**Auto-stop**: After configurable idle timeout (default 5 min) via Spring Kafka's `ListenerContainerIdleEvent`. When no messages are polled for the idle interval, the container stops automatically.

**Auto-restart**: After the container stops, a `ScheduledExecutorService` schedules a restart after configurable interval (default 30 min). Setting to 0 disables auto-restart (on-demand only). Each pod runs its own restart cycle independently — the DLT consumer group coordinates partition assignment across pods.

**Shutdown safety**: On application shutdown (`DisposableBean.destroy()`), all pending scheduled restarts are cancelled, a `shuttingDown` flag prevents new container starts, and running containers are stopped gracefully.

#### 4.5 REST API

**Normal start** — resume from last committed offset (idempotent, no-op if already running):
```
POST /{base-path}/dlt-routing/{topic}/start
```

**Start from timestamp** — creates a new consumer group with a timestamp-based suffix (e.g., `{group}-dlt-{timestamp}`), seeks to the given timestamp. Allows draining a subset of DLT messages:
```
POST /{base-path}/dlt-routing/{topic}/start?fromTimestamp={epoch_ms}
```

**Force retry** — same as timestamp start, but resets the `kafka-ops-retry-count` header to 0 for all routed messages. Gives previously-exhausted messages a fresh set of retry attempts. Use after fixing the root cause:
```
POST /{base-path}/dlt-routing/{topic}/start?fromTimestamp={epoch_ms}&force=true
```

Old consumer group offsets expire naturally via Kafka's `offsets.retention.minutes`.

#### 4.6 Validation

- Both DLT and retry topics must be declared for the router to be created. If only one is configured, the library logs a warning and skips router creation for that consumer bean.
- The DLT container's error handler uses bounded retry (3 attempts, 1s apart) then log-and-skip. No `DeadLetterPublishingRecoverer` on the DLT container — prevents a DLT-of-the-DLT loop.

#### 4.7 UI integration

The sidebar shows DLT and retry topics with approximate message counts (from the enriched consumers endpoint). A **"Drain DLT"** button is always visible next to the DLT topic (no conditional state logic — the action is idempotent). L1 monitors progress by refreshing — DLT count decreasing and retry count increasing tells the story. No status API needed.

#### 4.8 Configuration

```yaml
kafka:
  ops:
    dlt-routing:
      enabled: false              # default off — opt-in
      idle-shutdown-minutes: 5    # auto-stop after no messages
      restart-interval-minutes: 30  # auto-restart cycle (0 = on-demand only)
      max-retry-count: 3         # skip messages after this many route attempts
```

---

### Future Consideration (deferred)

#### Consumer group offset reset

**Why deferred / may never build**: Kafka's `AdminClient.alterConsumerGroupOffsets()` requires no active members — impossible while the app is running. Too dangerous for L1 support from an embedded library.

#### Audit log

**Why deferred**: Authentication and authorization is the app's responsibility (same pattern as Swagger UI). JWTs + access logging provide the audit trail.

#### Bulk retry via ManualKafkaConsumer

**Why deferred**: The DLT-based flow (Phase 4) is the production-grade path for bulk retry. The existing `ManualKafkaConsumer` polls from the original upstream topic which may have short retention. The DLT is service-owned with controlled retention, making it the reliable source for retry operations.

---

## Consequences

### Positive

- Batch browse fills the biggest feature gap vs standalone Kafka UIs
- Enriched poll response gives key context (headers, trace IDs) without checking logs
- Enriched consumers endpoint provides topic metadata + DLT/retry correlation in a single API call
- `TopicConfig` consolidates all per-topic configuration into one consistent type
- DLT/retry topics registered in the same registry — all existing APIs work on them for free
- Default error handler eliminates boilerplate for the most common retry/DLT patterns
- Per-topic retry config gives service developers granular control without YAML complexity
- DLT router abstracts the entire DLT drain lifecycle — service developer declares topic names, library handles everything
- Byte-level forwarding eliminates serialization risk entirely
- Force-retry with header reset allows L1 to re-drain previously exhausted messages after root cause fix
- Auto-stop + auto-restart cycle means DLT draining works autonomously without external scheduling
- Manual acknowledgment ensures no message loss if routing fails

### Negative

- `TopicConfig` replacing `getTopicName()` is a breaking change (acceptable — library not yet in use)
- The library now produces to Kafka (byte-level only) — first write capability
- Auto-configured error handler may surprise service developers who expect Spring Kafka defaults (mitigated by `@ConditionalOnMissingBean` and only created when retry config is explicitly provided)
- `ScheduledExecutorService` adds a thread per DLT-configured consumer (lightweight but worth documenting)
- Enriched consumers endpoint response shape changes from `Set<String>` to structured objects (only consumer is our own UI)

### Features explicitly out of scope

- Topic creation/deletion (infrastructure concern)
- Schema Registry management (CI/CD pipeline concern)
- Kafka Connect management (unrelated to library purpose)
- ACL management (infrastructure + security concern)
- Consumer group lag dashboards (observability concern — use Prometheus/Grafana)
- Message production to arbitrary topics (library only produces to declared retry topics via byte forwarding)
- Server-side message filtering (unbounded scan time)
- Idempotent routing state tracking (retry-count header prevents infinite loops instead)
- DLT scheduling via external systems (library provides auto-restart interval; Airflow/cron is optional and outside scope)
- Custom DLT routing logic (service developer overrides `DefaultErrorHandler` bean if the library's default doesn't fit)
