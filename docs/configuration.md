# Configuration

All properties are under the `kafka.ops` prefix.

## Core properties

| Property | Default | Description |
|---|---|---|
| `kafka.ops.rest-api.enabled` | `false` | Enable the REST endpoints |
| `kafka.ops.console.enabled` | `false` | Enable the web console UI |
| `kafka.ops.rest-api.retry-endpoint-url` | `operational/consumer-retries` | Base path for all REST endpoints |
| `kafka.ops.group-id` | `default-ops-group-id` | Consumer group ID used for polling |
| `kafka.ops.max-poll-interval-ms` | `5000` | Timeout for single-message poll |
| `kafka.ops.batch.max-limit` | `100` | Maximum records returned by batch browse |

## DLT routing properties

These require `kafka.ops.dlt-routing.enabled=true` and at least one consumer with both `withDlt()` and `withRetry()` declared on its `TopicConfig`.

| Property | Default | Description |
|---|---|---|
| `kafka.ops.dlt-routing.enabled` | `false` | Enable the DLT router bean |
| `kafka.ops.dlt-routing.idle-shutdown-seconds` | `10` | Stop the router after this many seconds with no new DLT messages |
| `kafka.ops.dlt-routing.restart-cron` | `0 */30 * * * *` | Cron expression for automatic router restarts. Use `-` to disable. |
| `kafka.ops.dlt-routing.max-cycles` | `10` | Skip a DLT message after this many routing cycles (prevents infinite loops) |

## Example configuration

```yaml
kafka:
  ops:
    rest-api:
      enabled: true
      retry-endpoint-url: operational/consumer-retries
    console:
      enabled: true
    group-id: my-service-ops
    max-poll-interval-ms: 5000
    batch:
      max-limit: 100
    dlt-routing:
      enabled: true
      idle-shutdown-seconds: 10
      restart-cron: "0 0 */6 * * *"  # every 6 hours
      max-cycles: 10
```

## Notes

- The **REST API and console** are disabled by default — you must opt in.
- The **console requires the REST API** to be enabled. It calls the same endpoints.
- The **DLT router** is independent of the REST API — it can run without the console or endpoints being enabled.
- The **consumer group ID** is shared across all poll and browse operations. Use a dedicated group ID to avoid interfering with your application's consumer groups.
- The **base path** controls where all REST endpoints are mounted. Change it to match your API gateway or reverse proxy setup.
