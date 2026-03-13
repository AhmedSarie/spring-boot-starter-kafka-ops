# DLT Routing

The DLT router automatically drains messages from a Dead Letter Topic back to the retry topic for reprocessing. No custom code needed — declare your topic names and the library handles the rest.

## How it works

1. Your consumer fails to process a message after all retries are exhausted
2. Spring Kafka's `DeadLetterPublishingRecoverer` sends the message to the DLT
3. The DLT router picks up the message and forwards it to the retry topic
4. Your consumer (listening on the retry topic) processes it again

The router operates at the **byte level** — it copies key, value, and all headers verbatim. No serialization or deserialization, zero schema risk.

## Setup

### 1. Declare DLT and retry topics on your consumer

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

Both `withDlt()` and `withRetry()` are required for routing. If only one is configured, routing is not available for that consumer.

### 2. Enable the router

```yaml
kafka:
  ops:
    dlt-routing:
      enabled: true
```

### 3. Trigger routing

From the web console, click "Drain DLT" next to any DLT topic. Or call the API:

```
POST /operational/consumer-retries/dlt-routing/orders/start
```

## Consumer group and multi-pod safety

The router uses a fixed consumer group (`{group-id}-dlt-router`). In a multi-pod deployment, the Kafka broker handles partition assignment across pods automatically — no coordination needed on your side.

!!! note
    On first deploy after switching to this library, old committed offsets from a previous consumer group may cause the router to skip already-processed messages. If needed, reset offsets using `kafka-consumer-groups.sh --reset-offsets`.

## Timestamp cutoff

Only messages that existed **before the trigger time** are routed. Messages that arrive after the router starts are left for the next run. This prevents the router from chasing an endlessly growing DLT.

The cutoff uses `record.timestamp()` (broker-assigned UTC epoch millis). Since the DLT producer is the same application, there is no clock skew concern.

## Cycle counter (infinite loop prevention)

Each time a message is routed from DLT to retry, the router stamps a `kafka-ops-dlt-cycle` header and increments it. When the cycle count reaches `max-cycles` (default 10), the message is acknowledged and skipped — it stays in the DLT but is no longer routed.

This prevents infinite loops where a message keeps failing, going to DLT, being routed back, failing again, etc.

### How the header survives the full cycle

```
Router sets kafka-ops-dlt-cycle=1
  → Message sent to retry topic
  → Consumer processes and fails
  → Spring DLT handler sends to DLT (preserves headers)
  → Router reads kafka-ops-dlt-cycle=1, increments to 2
  → ...repeats until max-cycles reached
```

!!! info
    Spring Kafka's own DLT headers (`kafka_dlt-original-topic`, etc.) are **overwritten** on each DLT publish, not appended. That's why the library uses its own `kafka-ops-dlt-cycle` header for cycle counting.

## Automatic restart

The router stops automatically after the DLT is idle for `idle-shutdown-seconds` (default 10). A cron schedule then restarts it to check for new DLT messages.

**Tuning automatic retry duration:** The total time a message keeps being retried automatically is roughly:

```
total retry window ≈ max-cycles × restart-cron interval
```

For example, with `max-cycles=10` and `restart-cron` every 6 hours (`0 0 */6 * * *`), a message is retried for up to ~2.5 days before being permanently skipped.

| Goal | Adjustment |
|---|---|
| Retry longer | Increase `max-cycles` or decrease cron frequency |
| Retry shorter | Decrease `max-cycles` or increase cron frequency |
| Disable auto-restart | Set `restart-cron` to `-` |
| Stop all retries immediately | Disable `dlt-routing.enabled` and redeploy |

## DLT header decoding

Spring Kafka's `DeadLetterPublishingRecoverer` writes certain headers as binary (BigEndian):

| Header | Encoding | Decoded as |
|---|---|---|
| `kafka_dlt-original-offset` | 8-byte BigEndian long | Readable number |
| `kafka_dlt-original-timestamp` | 8-byte BigEndian long | Readable number |
| `kafka_dlt-original-partition` | 4-byte BigEndian int | Readable number |
| `kafka_dlt-exception-stacktrace` | String | Filtered out (too large) |

The library automatically decodes these in both API responses and the web console.

## Idle shutdown details

The router uses `MANUAL_IMMEDIATE` acknowledgment mode — an offset is only committed after the message is successfully sent to the retry topic. If a send fails, the offset is not committed and the message will be re-delivered on the next poll.

!!! warning
    Manually created listener containers (like the DLT router uses) require `ApplicationEventPublisher` to be set explicitly for idle events to fire. The library handles this automatically, but if you're building custom containers, be aware that without it, `ListenerContainerIdleEvent` will never fire and the container runs forever.

## Configuration reference

| Property | Default | Description |
|---|---|---|
| `kafka.ops.dlt-routing.enabled` | `false` | Enable the DLT router |
| `kafka.ops.dlt-routing.idle-shutdown-seconds` | `10` | Idle timeout before auto-stop |
| `kafka.ops.dlt-routing.restart-cron` | `0 */30 * * * *` | Cron for automatic restarts. `-` to disable. |
| `kafka.ops.dlt-routing.max-cycles` | `10` | Max routing cycles per message |
