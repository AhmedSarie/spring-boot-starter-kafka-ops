# Web Console

The library ships an embedded web UI at `/kafka-ops/index.html`. No separate deployment, no CDN, works fully offline.

<!-- TODO: Add screenshot of the console here -->
<!-- ![Console screenshot](assets/console-screenshot.png) -->

## Tech stack

The console uses [Mithril.js](https://mithril.js.org) and [Pico CSS](https://picocss.com), both vendored in the JAR. No Node.js, no npm, no build step, no runtime internet required.

## Features

### Consumer sidebar

A tree layout showing your consumers and their associated topics:

```
orders                    1,500 msgs
  orders.DLT                 12 msgs    [Drain DLT]
  orders-retry                0 msgs
payments                  8,200 msgs
  payments.DLT                3 msgs    [Drain DLT]
  payments-retry              0 msgs
```

- Resizable sidebar for long topic names
- Message count badges on each topic
- DLT routing config (cron, max cycles, idle timeout) displayed in the footer when enabled
- One-click "Drain DLT" button for any DLT topic

### Poll view

Inspect a single message by partition and offset.

- Full metadata badges: key, timestamp, partition, offset
- Collapsible headers section
- JSON tree viewer for the message value
- Poll history — last 10 partition/offset pairs per topic, persisted across browser sessions

### Batch browse

Browse messages by timestamp or offset range.

- **By timestamp** — defaults to "last hour" for quick access to recent messages
- **By offset** — specify partition and starting offset
- Expandable results table with message details
- Collapsible headers for each record
- Browse history saved per topic

### Retry and correct

- **Retry** — one-click to re-consume a message through your consumer logic
- **Correct** — edit the JSON payload in-place, with a diff confirmation popup before sending

### DLT routing

Start DLT routing directly from the sidebar for any consumer with a configured DLT topic. Monitor progress by refreshing — the DLT message count decreases as messages are routed to the retry topic.

## Enabling the console

```yaml
kafka:
  ops:
    rest-api:
      enabled: true   # required — the console calls these endpoints
    console:
      enabled: true
```

The console requires the REST API to be enabled. Without it, the UI loads but cannot fetch data.
