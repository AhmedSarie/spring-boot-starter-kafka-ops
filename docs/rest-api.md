# REST API

All endpoints are served under a configurable base path (default: `/operational/consumer-retries`). Examples below use the default path.

## List registered consumers

```
GET /operational/consumer-retries/consumers
```

Returns structured consumer info including partition counts, message counts, and DLT/retry sub-topics.

**Response:**

```json
[
  {
    "name": "orders",
    "partitions": 3,
    "messageCount": 1500,
    "dlt": {
      "name": "orders.DLT",
      "partitions": 3,
      "messageCount": 12
    },
    "retry": {
      "name": "orders-retry",
      "partitions": 3,
      "messageCount": 0
    }
  }
]
```

The `dlt` and `retry` fields are only present when the consumer declares them via `withDlt()` and `withRetry()` on its `TopicConfig`.

## Poll a message

```
GET /operational/consumer-retries?topicName=orders&partition=0&offset=42
```

Returns the message at the exact partition and offset with full metadata.

**Response:**

```json
{
  "consumerRecordValue": "{\"orderId\": \"123\"}",
  "key": "order-key",
  "partition": 0,
  "offset": 42,
  "timestamp": 1700000000000,
  "headers": {
    "traceid": "abc-123"
  }
}
```

## Batch browse

Browse multiple messages by offset range or by timestamp. Provide **either** `partition` + `startOffset`, **or** `startTimestamp` — not both.

### By offset

```
GET /operational/consumer-retries/batch?topicName=orders&partition=0&startOffset=100&limit=20
```

### By timestamp

```
GET /operational/consumer-retries/batch?topicName=orders&startTimestamp=1700000000000&limit=20
```

**Response:**

```json
{
  "records": [
    {
      "partition": 0,
      "offset": 100,
      "timestamp": 1700000000000,
      "key": "k1",
      "value": "...",
      "headers": {}
    }
  ],
  "hasMore": false
}
```

The endpoint returns whatever records are available up to `limit`. If fewer exist, it returns what it finds without waiting. Use `offset + 1` as the next `startOffset` for pagination.

## Retry a message

```
POST /operational/consumer-retries
Content-Type: application/json

{"topic": "orders", "partition": 0, "offset": 42}
```

Re-consumes the message through your consumer's `consume()` method. The message is fetched from Kafka and passed to your consumer logic as if it were consumed normally.

## Send a correction

```
POST /operational/consumer-retries/corrections/orders
Content-Type: application/json

{"orderId": "123", "status": "corrected"}
```

Sends the JSON payload directly to your consumer's `consume()` method without reading from Kafka. The payload is deserialized using your consumer's [value codec](value-formats.md#custom-formats).

## Start DLT routing

```
POST /operational/consumer-retries/dlt-routing/{topic}/start
```

Starts routing messages from the consumer's DLT to its retry topic. See [DLT Routing](dlt-routing.md) for details.

**Requirements:**

- `kafka.ops.dlt-routing.enabled=true`
- The consumer must declare both `withDlt()` and `withRetry()` on its `TopicConfig`

## Console config

```
GET /kafka-ops/api/config
```

Returns the configured API base path and DLT routing settings. Used internally by the web console to resolve endpoints and display configuration.

**Response:**

```json
{
  "apiBasePath": "operational/consumer-retries",
  "dltRouting": {
    "enabled": true,
    "restartCron": "0 */30 * * * *",
    "maxCycles": 10,
    "idleShutdownSeconds": 10
  }
}
```
