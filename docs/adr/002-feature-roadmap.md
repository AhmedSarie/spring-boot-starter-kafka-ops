# ADR-002: Feature Roadmap — Batch Browse, Enriched Responses, Consumer Info

## Status

Proposed

## Context

The library currently supports single-message operations: poll by exact offset, retry, and correction. In practice, L1 support engineers often need to browse multiple messages around the time of an incident, understand consumer state, and have richer message metadata (headers, keys, timestamps) to diagnose issues.

This ADR defines the next phase of features, informed by a comparison with GCP Pub/Sub admin capabilities and standalone Kafka UIs (Kafdrop, AKHQ, Kafka UI).

### Competitive positioning

The library's moat vs standalone Kafka tools:

| Capability                               | Kafdrop / AKHQ / Kafka UI | kafka-ops (this library)       |
|------------------------------------------|---------------------------|--------------------------------|
| Retry through app's business logic       | No                        | **Yes**                        |
| Edit & correct, re-process through app   | No                        | **Yes**                        |
| Auto-discovers consumers at startup      | No                        | **Yes**                        |
| Understands app's serialization natively | Schema Registry only      | **Yes (same ConsumerFactory)** |
| Zero deployment (embedded in service)    | No (separate infra)       | **Yes**                        |
| Topic/schema/ACL management              | Yes                       | No (out of scope)              |
| Multi-cluster support                    | Yes                       | No (embedded = one cluster)    |

**What we are**: An embedded ops tool for message-level investigation and remediation, leveraging the app's own consumer logic.

**What we are not**: A Kafka cluster management platform. Topic CRUD, schema registry, Connect, ACLs, lag dashboards belong in standalone tools.

## Decision

### Phase 1: Batch Browse, Enriched Responses, Consumer Info

#### 1.1 Enrich poll response with headers, key, and timestamp

**Current state**: `KafkaPollResponse` returns only `consumerRecordValue` (a string).

**Change**: Add `key`, `timestamp`, `partition`, `offset`, and `headers` to the poll response. This is backward-compatible (new fields with Jackson defaults). Gives support engineers trace IDs, retry counts, and message keys without needing to check logs.

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

**Effort**: Small. Changes to `KafkaPollResponse` and `KafkaOpsService.recordValueAsString()`.

#### 1.2 Consumer info endpoint

**Current state**: The sidebar lists topic names but provides no metadata about each topic.

**Change**: New endpoint `GET /{base-path}/consumers/{topic}/info` returning partition count and end offsets. Leverages the existing `KafkaConsumer` in the registry (call `endOffsets()`, `partitionsFor()`).

```json
{
  "topic": "orders",
  "partitions": 6,
  "endOffsets": { "0": 15420, "1": 12893, "2": 18001, "3": 9544, "4": 11232, "5": 14100 }
}
```

**UI**: Show partition count and total message count in the sidebar or as a header when a topic is selected. Helps users pick the right partition and understand the offset range.

**Effort**: Small. New method in `KafkaOpsService`, new endpoint in `KafkaOpsController`.

#### 1.3 Batch browse by timestamp or offset range

**Current state**: Only single-message poll by exact topic/partition/offset.

**Change**: New endpoint for fetching N messages from a starting point:

```
GET /{base-path}/batch?topicName={topic}&startTimestamp={epoch_ms}&limit={n}
GET /{base-path}/batch?topicName={topic}&partition={p}&startOffset={offset}&limit={n}
```

Response:
```json
{
  "records": [
    {
      "partition": 0,
      "offset": 42,
      "timestamp": 1709251200000,
      "key": "order-123",
      "value": "{...}",
      "headers": { "traceid": "abc" }
    }
  ],
  "hasMore": true
}
```

**Pagination**: The client sends the last `offset + 1` as the next `startOffset`. No server-side cursor state.

**Filtering**: Client-side only. The UI fetches a page of records and provides a text filter input for live search over JSON values. No server-side regex — avoids unbounded scan times.

**Guard rails**:
- Hard cap on `limit` (configurable via `kafka.ops.batch.max-limit`, default 100)
- Separate poll timeout (`kafka.ops.batch.poll-timeout-ms`)

**Implementation notes**:
- The current `KafkaOpsConsumerRegistry` creates consumers with `max.poll.records=1`. Batch browse needs a higher value. Options: (a) create a second consumer per topic for batch use, or (b) remove the `max.poll.records=1` restriction and filter results in code (existing single-poll already validates partition+offset). Option (b) is simpler.
- Timestamp-based seeking uses `kafkaConsumer.offsetsForTimes()` which returns the offset at or after the given timestamp per partition.
- The `ManualKafkaConsumer.poll()` is `synchronized`. Batch operations hold this lock longer. Since KafkaConsumer instances are per-topic and not thread-safe, this serialization is correct but should be documented.

**UI**: New route `#!/browse` with a table of records. Text filter input above the table. Clicking a row expands to the full JsonViewer. Existing retry/correct actions apply to the selected row.

**Effort**: Medium. New service method, new endpoint, new UI view. 3-5 days.

### Future Consideration (not in Phase 1)

#### Bulk retry from timestamp range

Retry all messages in a timestamp range through the consumer's business logic. Uses the ops consumer group (never touches the app's consumer group).

**Why deferred**: Sync REST API would timeout on large ranges. Async execution needs job management (progress tracking, cancellation, single-slot-per-topic queuing). This machinery is significant and the need is largely covered by batch browse + individual retry — browse the messages, identify the ones that failed, retry them one by one.

**If built later**: Single-slot-per-topic job queue, progress endpoint, cancel endpoint, hard cap on messages, rate limiting, dry-run mode, gated behind `kafka.ops.batch-retry.enabled=true`.

#### Consumer group offset reset

Reset the app's consumer group to a timestamp so it reconsumes.

**Why deferred / may never build**:
- Kafka's `AdminClient.alterConsumerGroupOffsets()` requires the group to have no active members — impossible while the app is running
- Committing offsets from the ops consumer to the app's group is immediately overwritten by the app's active consumers on their next commit cycle
- Stopping/restarting the app's listener containers from an ops library is a service-level kill switch — too dangerous for L1 support
- GCP Pub/Sub's "seek to timestamp" works because delivery is managed server-side. Kafka's consumer group offsets are client-managed, making external manipulation fundamentally harder

**If ever built**: Only the `kafka-consumer-groups.sh --reset-offsets` equivalent — require the consumer group to be in `Empty` state (no active members), verified by the library before proceeding. Document clearly that the app must be stopped first.

#### Audit log

Log every poll/retry/correction with user identity and message coordinates.

**Why deferred**: Authentication and authorization of the library's endpoints is the consuming app's responsibility (same pattern as Swagger UI). If JWTs are used, user identity is already in request headers and can be logged via the app's existing access logging or MDC. Building a custom audit trail inside the library duplicates what the app's security infrastructure already provides.

**If built later**: In-memory ring buffer of the last N operations, exposed via `GET /audit` and a UI tab.

## Consequences

### Positive

- Batch browse fills the biggest feature gap vs standalone Kafka UIs
- Enriched poll response gives support engineers key context (headers, trace IDs) without checking logs
- Consumer info helps users pick the right partition and understand offset ranges
- Client-side filtering keeps the architecture simple (no unbounded server-side scans)
- All Phase 1 features use the existing ops consumer group — no risk to the app's consumer group

### Negative

- Batch browse with large pages increases memory usage (mitigated by hard cap)
- `ManualKafkaConsumer` lock held longer during batch polls (serialized per-topic, documented)
- Removing `max.poll.records=1` changes the internal polling behavior (correctness maintained by existing offset validation)

### Features explicitly out of scope

- Topic creation/deletion (infrastructure concern)
- Schema Registry management (CI/CD pipeline concern)
- Kafka Connect management (unrelated to library purpose)
- ACL management (infrastructure + security concern)
- Multi-cluster support (library is embedded in one service)
- Consumer group lag dashboards (observability concern — use Prometheus/Grafana)
- Message production to arbitrary topics (dangerous, out of scope)
- Server-side message filtering (unbounded scan time)
