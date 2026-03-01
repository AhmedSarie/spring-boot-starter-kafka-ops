# ADR-001: Embedded UI for Kafka Operations Dashboard

## Status

Accepted

## Context

The library currently exposes REST endpoints for Kafka message operations (poll, retry, corrections). These endpoints work well for programmatic access but require tools like curl or Postman to use. L1 support engineers need a simple browser-based interface to inspect and operate on Kafka messages without developer tooling.

The UI should be embedded in the library JAR and work out of the box — similar to how Swagger UI or H2 Console are available when their dependencies are added. It must work fully offline with no CDN or runtime internet dependency.

## Decision

### Technology: Mithril.js (vendored) + Pico CSS (vendored)

**Mithril.js** (~20KB minified) is a complete SPA framework in a single file — routing, XHR, virtual DOM, and components. It handles JSON APIs natively via `m.request()` which auto-parses responses and triggers re-renders. No import maps, no ES modules — a plain `<script>` tag.

**Pico CSS** (~80KB minified) provides semantic HTML styling — `<article>` becomes a card, `<nav>` becomes a navbar, forms and buttons are styled automatically. Dark mode is built in via `data-theme` attribute.

Both are vendored directly in the JAR (committed to source control). No WebJar dependency, no build step, no Node.js.

**Why Mithril.js over alternatives:**

| Approach | Build step | JSON API | Routing | Components | Size | Single file |
|---|---|---|---|---|---|---|
| htmx | None | Poor (HTML-fragment design) | No | No | ~43KB | Yes |
| Alpine.js | None | Good (fetch) | No | Weak (inline) | ~47KB | Yes |
| Preact + htm | None (ES modules) | Excellent | Add-on | Excellent | ~10KB | No (needs 2-3) |
| **Mithril.js** | **None** | **Excellent (built-in)** | **Built-in** | **Excellent** | **~20KB** | **Yes** |
| React/Vue SPA | Node.js + npm | Excellent | Add-on | Excellent | ~500KB+ | No |

### File structure

```
src/main/resources/static/kafka-ops/
├── index.html
├── css/
│   ├── pico.min.css         (vendored)
│   └── app.css              (custom overrides)
└── js/
    ├── mithril.min.js       (vendored)
    └── app/
        ├── main.js          (routes + mount)
        ├── api.js           (API client)
        ├── state.js         (shared state)
        ├── components/      (reusable UI pieces)
        └── views/           (route-level pages)
```

Spring Boot auto-serves files from `classpath:/static/` — no resource handler configuration needed.

### Backend

A dedicated `KafkaOpsConsoleController` at `/kafka-ops/api/` provides fixed endpoints for the UI, decoupling it from the configurable REST API base path. Endpoints delegate to `KafkaOpsService`:

- `GET /kafka-ops/api/consumers` — list registered consumer topics
- `GET /kafka-ops/api/poll` — poll a message
- `POST /kafka-ops/api/retry` — retry a message
- `POST /kafka-ops/api/corrections/{topic}` — send a correction

### Configuration

The console is enabled when the REST API is enabled:

```yaml
kafka:
  ops:
    rest-api:
      enabled: true
```

### UI Layout

Single-page dashboard with sidebar + content area. Mithril routing enables future multi-view expansion.

### User flow

1. **See registered consumers** — Sidebar lists all topics with a `KafkaOpsAwareConsumer` bean. Clicking a topic auto-fills the topic input.
2. **Poll a message** — Enter topic, partition, offset → click Poll → pretty-printed JSON with syntax highlighting renders in the result area.
3. **Retry** — After polling, click Retry → same topic/partition/offset is sent to the retry endpoint. Success/error toast appears.
4. **Edit & Correct** — Click "Edit & Correct" → polled JSON is copied into an editable text area. User modifies values, clicks "Send Correction" → payload sent to the corrections endpoint.

## Consequences

### Positive

- Zero additional dependencies for consuming applications
- No build tooling required (no Node.js, no npm)
- Works fully offline — no CDN, no external fonts, no runtime internet
- Total JAR size increase: ~100KB (Mithril + Pico CSS + app code)
- Toggleable via existing configuration property
- Existing JSON API is untouched
- Built-in routing supports future multi-page features

### Negative

- Vendored files (mithril.min.js, pico.min.css) must be manually updated for new versions
- Mithril.js hyperscript syntax has a small learning curve
- UI capabilities are limited compared to a full SPA framework (acceptable for an ops dashboard)

### Future extensibility

The routing + component architecture supports adding new features as new routes:

- `#!/fetch` — Fetch N messages from a topic starting at a given timestamp
- `#!/reconsume` — Reconsume a topic from a specific timestamp
- `#!/dlq` — Dead Letter Queue viewer
- `#!/bulk-retry` — Bulk retry operations

Each feature is a new view file in `views/` and a route in `main.js`.
