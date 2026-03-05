# ADR-003: Library Architecture Review

## Status

Accepted

## Context

After completing the DLT router redesign (ADR-002 Waves 1 & 2) and the console UI improvements, a comprehensive library review was conducted across four specialist domains:

1. **Backend & API Design** — REST API conventions, Spring Boot starter patterns, library ergonomics
2. **Kafka & Distributed Systems** — Consumer group semantics, offset management, multi-pod safety, thread safety
3. **Frontend & UX** — Mithril.js patterns, accessibility, responsiveness, state management
4. **Security & Production Readiness** — Endpoint exposure, input validation, information leakage, CSRF

The review covers all production code on the `feature/adr-002-wave-1` branch at commit `14d80be`.

---

## Review Findings

### 1. Backend & API Design

#### Strengths

- **Textbook Spring Boot starter autoconfiguration**: `@AutoConfiguration`, `@AutoConfigureAfter(KafkaAutoConfiguration.class)`, `@ConditionalOnClass`, `@ConditionalOnMissingBean` — users can override any bean
- **Immutable value objects**: `TopicConfig` and `ContainerConfig` use private constructors with static factory methods and `with*()` copy-builders — clean fluent API
- **Well-structured consumer registry**: `KafkaOpsConsumerRegistry` implements `InitializingBean`/`DisposableBean` for proper lifecycle management with `ConcurrentHashMap` for thread safety
- **Consistent error response shape**: `errorResponse()` helper returns uniform `{status, error, message}` JSON across all endpoints
- **Validation on request DTOs**: `KafkaOpsRequest` uses `@NotEmpty`, `@NotNull`, `@Min(0)` with `@Valid` in the controller
- **Properties defaults well-handled**: `KafkaOpsProperties` constructor provides sensible defaults without requiring any configuration beyond enabling the feature

#### Concerns

| Severity | Issue                                                    | Location                                                  | Detail                                                                                                                             |
|----------|----------------------------------------------------------|-----------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------|
| **High** | Duplicate `DEFAULT_GROUP_ID` with different values       | `KafkaOpsConfiguration:32` vs `KafkaOpsProperties:13`     | `"kafka-ops-group-id"` vs `"default-ops-group-id"` — effective default depends on code path, which is confusing and likely a bug   |
| **High** | `poll()` returns `null` instead of 404/204               | `KafkaOpsService:85`                                      | `.orElse(null)` causes `200 OK` with `null` body when no record found — misleading                                                 |
| **High** | Potential NPE in batch poll validation                   | `KafkaOpsController:99`                                   | If only `partition` is provided without `startOffset`, the XOR check passes and `startOffset` is null-unboxed in the service layer |
| Medium   | Boilerplate try/catch/finally in every controller method | `KafkaOpsController:35-163`                               | Six methods all follow identical pattern — cross-cutting concern should be extracted                                               |
| Medium   | Raw types throughout                                     | `KafkaOpsConsumerRegistry:26`, `KafkaOpsService:58,71,84` | Raw `KafkaOpsAwareConsumer`, `KafkaConsumer`, `ConsumerRecord` — loses type safety, generates compiler warnings                    |
| Medium   | `@ComponentScan` in autoconfiguration                    | `KafkaOpsConfiguration:27`                                | Can pick up unintended beans from the same package in consuming applications — prefer explicit `@Bean` or `@Import`                |
| Medium   | Corrections create fake `ConsumerRecord`                 | `KafkaOpsService:58-61`                                   | Synthesized with `partition=0, offset=0` — could produce incorrect behavior if consumer uses partition/offset for idempotency      |
| Low      | `hasMore` heuristic is imprecise                         | `KafkaOpsService:105`                                     | Returns true when batch size == limit, even if no more records exist                                                               |
| Low      | Duplicate `DEFAULT_RETRY_ENDPOINT_URL`                   | `KafkaOpsConsoleController:21`                            | Also defined in controller annotation SpEL default — out-of-sync risk                                                              |

#### Recommendations

**P0 — Bugs / Correctness**
1. Fix duplicate default group ID — remove redundant default from `KafkaOpsConfiguration` and rely solely on `KafkaOpsProperties` constructor default, or unify them
2. Return `Optional<KafkaPollResponse>` from `poll()` and have controller return 204 or 404 instead of 200-with-null-body
3. Add explicit null checks in batch poll — validate that `partition` and `startOffset` are both present or both absent

**P1 — API Quality**
4. Extract repeated try/catch/MDC pattern into a `@ControllerAdvice(assignableTypes = KafkaOpsController.class)` — scoped to the library's controller only, so the host application's exception handling is unaffected
5. Type the `ResponseEntity` return values or add OpenAPI annotations

**P2 — Maintainability**
6. Eliminate raw types — add type parameters to `KafkaOpsAwareConsumer`, `KafkaConsumer`, and `ConsumerRecord` references
7. Replace `@ComponentScan` with explicit `@Import` or `@Bean` declarations
8. Consolidate duplicated constants into a single location

---

### 2. Kafka & Distributed Systems

#### Strengths

- **Sound DLT routing architecture**: Fixed consumer group (`{groupId}-dlt-router`) — broker-managed group coordination, no application-level distributed locking needed for multi-pod deployments
- **Correct ack semantics**: `MANUAL_IMMEDIATE` ack mode combined with `ack.acknowledge()` only after synchronous `kafkaTemplate.send(...).get()` gives at-least-once delivery. Cutoff-exceeded path correctly does NOT acknowledge, preserving offset for next window
- **Timestamp cutoff over retry counting**: Using `record.timestamp()` as termination condition is elegant — same JVM means no clock skew. Cleanly avoids infinite re-routing within a single window
- **Cycle counter circuit breaker**: `kafka-ops-dlt-cycle` header survives full cycle (router → retry → consumer fails → Spring DLT handler → router reads it). Configurable max cycles (default 10)
- **Synchronous send with `.get()`**: Correct for routing — guarantees broker acknowledgment before committing source offset
- **`max.poll.records=1` on manual consumers**: Correctly limits fetch size for single-record operations

#### Concerns

| Severity | Issue                                                                  | Location                         | Detail                                                                                                                                                                                                                                                                                     |
|----------|------------------------------------------------------------------------|----------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **High** | `KafkaConsumer` used without synchronization in `getConsumerDetails()` | `KafkaOpsConsumerRegistry:56-84` | Calls `partitionsFor()`, `endOffsets()`, `beginningOffsets()` on shared `KafkaConsumer` instances while `ManualKafkaConsumer` synchronizes on a different lock. Concurrent `/consumers` call during a poll will cause `ConcurrentModificationException`                                    |
| **High** | Global lock on `ManualKafkaConsumer` serializes all topics             | `ManualKafkaConsumer:28,38,49`   | All poll methods `synchronized` on `this` — two concurrent requests for different topics block each other unnecessarily                                                                                                                                                                    |
| Medium   | `extractConnectionProps` uses allow-list that may miss properties      | `KafkaOpsFactoryUtils:33-39`     | Only copies `bootstrap.`, `security.`, `sasl.`, `ssl.` prefixes — misses `client.dns.lookup`, `retry.*`, `reconnect.*`, custom interceptors. Should copy all properties and override only `group.id` and deserializer configs                                                              |
| Medium   | Silent fallback on malformed cycle header                              | `KafkaOpsDltRouter:192-203`      | `NumberFormatException` caught and defaults to 0 — could allow extra routing cycles if header is corrupted                                                                                                                                                                                 |
| Medium   | DLT router creates shared `KafkaTemplate` from first consumer's props  | `KafkaOpsDltRouter:61-63`        | The library already supports multi-cluster via `getContainerName()`. The DLT router should follow the same pattern — create a per-route `KafkaTemplate` using the same container factory's connection properties as the consumer's declared container, falling back to the default factory |
| Low      | Concurrency=1 hardcoded for DLT containers                             | `KafkaOpsDltRouter:140`          | Multi-partition DLT topics processed by single thread — fine for typical low-volume DLT, but slow for high-volume scenarios                                                                                                                                                                |
| Low      | At-least-once semantics not documented                                 | `KafkaOpsDltRouter:180-188`      | Crash between successful send and ack causes re-routing on restart — consumers on retry topic must be idempotent                                                                                                                                                                           |

#### Recommendations

**P1 — Fix thread-safety of `KafkaConsumer` in `getConsumerDetails()`**
Create dedicated `KafkaConsumer` instances for metadata queries (`partitionsFor`, `endOffsets`, `beginningOffsets`). These consumers are lightweight when idle — just a TCP connection to the broker. This cleanly separates metadata queries from polling without shared-lock complexity.

**P2 — Switch from global lock to per-consumer lock**
Replace `synchronized` on the method with `synchronized(kafkaConsumer)` to allow concurrent operations on different topics. Simple change with significant throughput improvement.

**P3 — Switch `extractConnectionProps` from allow-list to deny-list**
Copy all properties from the consumer factory and override only `group.id` and deserializer configs (`key.deserializer`, `value.deserializer`). This ensures cloud-specific properties (`client.dns.lookup`), retry/reconnect settings, and custom interceptors are inherited automatically.

**P4 — Change malformed cycle header fallback**
Default to `maxCycles - 1` (conservative) instead of 0, and log at ERROR.

**P5 — Per-route `KafkaTemplate` for multi-cluster DLT routing**
The library already supports multi-cluster via `getContainerName()`. The DLT router should follow the same pattern: create a `KafkaTemplate` per route using the same container factory's connection properties as the consumer's declared container, falling back to the default factory. This ensures DLT and retry topics are always on the same cluster as the original topic.

**P6 — Document at-least-once semantics**
Add a note in README that DLT routing provides at-least-once delivery, and consumers on the retry topic should be idempotent.

---

### 3. Frontend & UX

#### Strengths

- **Clean component architecture**: IIFE-closure pattern for PollView/BrowseView encapsulates per-view state; shared state (`AppState`, `PollHistory`) kept minimal in `state.js`
- **Solid CSS design system**: Custom properties layered on Pico CSS; `.btn` composition pattern avoids style duplication; JSON syntax highlighting is readable
- **DiffModal is genuinely useful**: Flat-diff (flatten to dot-notation, compare) gives clear field-level changed/added/removed sections — standout feature for corrections
- **Thoughtful micro-interactions**: Toast animations, fade-in for new content, smooth scrollbar styling, spinner on async buttons
- **Good keyboard support**: Enter/Space on sidebar items, Escape to close editors/modals, `role="option"` and `aria-selected`, focus-visible indicators
- **Collapsible JSON tree viewer**: HTML escaping, collapse/expand with item counts, syntax coloring

#### Concerns

| Severity | Issue                                                          | Location                                      | Detail                                                                                                                                        |
|----------|----------------------------------------------------------------|-----------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------|
| **High** | DiffModal has no focus trap                                    | `DiffModal.js:96-177`                         | Focus not moved into modal on open, Tab escapes behind overlay, no `aria-modal`, no `role="dialog"`, no focus restoration on close            |
| **High** | No confirmation for "Drain DLT" action                         | `Sidebar.js:72-97`                            | Clicking "Drain" immediately fires `Api.startDltRouting()` with no confirm step — reprocesses ALL DLT messages                                |
| **High** | Toast notifications not announced to screen readers            | `Toast.js`                                    | `.toast-container` has no `role="alert"` or `aria-live="polite"`                                                                              |
| Medium   | ~100 lines of duplicated logic between views                   | `PollView.js:76-121`, `BrowseView.js:154-199` | Correction editor, retry logic, `formatTimestamp`, `anyBusy`, JSON error handling all duplicated                                              |
| Medium   | `JsonViewer` collapsed state is global                         | `JsonViewer.js:3`                             | Module-level `collapsed` object shared across all viewer instances — toggling in one view affects another                                     |
| Medium   | `expandedIndex` is fragile                                     | `BrowseView.js:14,130-140`                    | Integer index into filtered results — "Load More" appending records shifts which row is expanded. Should use `partition:offset` composite key |
| Medium   | Correction targets selected topic, not record's original topic | `BrowseView.js:190`                           | Browsing a DLT topic and editing sends correction to DLT topic, not original topic                                                            |
| Medium   | No mobile/tablet breakpoints                                   | `app.css`                                     | No `@media` queries; sidebar fixed at `20rem`; browse table unusable on small screens                                                         |
| Low      | Sidebar DLT item clickable area inconsistent                   | `Sidebar.js:57-98`                            | Only inner `.consumer-item-label` triggers navigation, not the container — breaks listbox contract                                            |
| Low      | `m.trust()` scattered for SVG icons                            | Throughout all components                     | Safe (hardcoded), but should be centralized into a shared `Icons` object                                                                      |
| Low      | Direct DOM access in `openEditor`                              | `PollView.js:85-87`, `BrowseView.js:162-165`  | `document.querySelector` with `setTimeout` — should use Mithril's `oncreate` lifecycle                                                        |

#### Recommendations

**P0 — Accessibility fixes**
1. Add focus trap and ARIA attributes to DiffModal (`role="dialog"`, `aria-modal="true"`, auto-focus, trap Tab, restore focus on close)
2. Add `role="alert"` or `aria-live="polite"` to toast container
3. Add confirmation step before "Drain DLT" — even `window.confirm()` suffices

**P1 — State management fixes**
4. Make `JsonViewer` collapsed state instance-scoped (key the component or use `vnode.state`)
5. Replace `expandedIndex` integer with `partition:offset` composite key
6. Fix correction endpoint to use the record's original topic (from DLT headers) when browsing a DLT topic

**P2 — Code quality**
7. Extract shared utilities (`formatTimestamp`, `truncate`, `escapeHtml`) into `utils.js`
8. Extract shared components (correction editor, retry/edit buttons, metadata badges) to eliminate duplication
9. Centralize SVG icons into shared `Icons` object
10. Add basic responsive breakpoint to collapse sidebar on small screens

---

### 4. Security & Production Readiness

#### Strengths

- **Disabled by default**: Both `rest-api.enabled` and `console.enabled` default to `false`. DLT routing also requires explicit opt-in
- **Registry acts as an allowlist**: Only topics registered via `KafkaOpsAwareConsumer` beans are accessible through the API — arbitrary topic access is blocked by `NoConsumerFoundException`
- **DLT stacktrace filtering**: `kafka_dlt-exception-stacktrace` stripped from API responses — prevents leaking internal stack traces
- **Defensive BigEndian decoding**: Checks `header.value().length == 8/4` before `ByteBuffer` decoding — prevents `BufferUnderflowException`
- **Batch limit capping**: User-supplied `limit` capped to `batchMaxLimit` (default 100)
- **Connection property filtering**: `KafkaOpsFactoryUtils` only copies `bootstrap.*`, `security.*`, `sasl.*`, `ssl.*` — avoids leaking unrelated consumer config to DLT producer
- **MDC cleanup**: Every controller method clears MDC in `finally` blocks
- **Graceful shutdown**: Both registry and DLT router implement `DisposableBean`

#### Concerns

| Severity | Issue | Location | Detail |
|----------|-------|----------|--------|
| **High** | Log injection via user-controlled topic names | `KafkaOpsController:52,73,96,122,149` | `String.format()` with unsanitized `topicName` — newline characters (`%0D%0A`) inject fake log entries. SLF4J `{}` placeholders also don't sanitize newlines |
| **High** | Correction payload has no size limit | `KafkaOpsController:118` | `@RequestBody String` without `@Size` — potential memory exhaustion (mitigated by server defaults but not explicitly controlled) |
| Medium | Exception messages exposed in error responses | `KafkaOpsController:165-168` | Generic `Exception` catch returns `e.getMessage()` — intentional for ops usability but could expose internal details. Should be configurable |
| Medium | `isolation.level=read_uncommitted` not documented | `KafkaOpsConsumerRegistry:104` | Ops consumer can read transactional messages that were never committed — intentional for debugging but should be documented |
| Medium | Static files served regardless of `enabled` flags | Spring Boot default resource handler | `/kafka-ops/**` HTML/JS/CSS served when JAR is on classpath, even with both features disabled — reveals library is installed |
| Medium | No CSRF protection on POST endpoints | `KafkaOpsController:48,118,138` | Without Spring Security, POST endpoints are vulnerable to CSRF from any origin |
| Medium | Consumer record values logged at INFO | `ManualKafkaConsumer:108` | `log.info("Returning consumer record: {}", item)` logs full record value — could contain PII or sensitive business data |
| Low | `poll()` returns 200 with null body | `KafkaOpsService:85` | Should be 204 or 404 |
| Low | DLT consumer group ID is predictable | `KafkaOpsDltRouter:131` | `{groupId}-dlt-router` — rogue consumer in same group could steal DLT messages (requires broker network access) |
| Low | Consumers endpoint exposes Kafka topology | `KafkaOpsController:34-45` | Topic names, partition counts, message counts, DLT/retry relationships — useful for reconnaissance |

#### Recommendations

**P0 — Must fix**
1. Sanitize topic names in log statements — validate against `^[a-zA-Z0-9._-]+$` regex, reject with 400 if invalid. This also serves as input validation for all topic parameters
2. Add `@Size(max = 1_048_576)` to correction payload or document expected server max size
3. Make error detail exposure configurable via `kafka.ops.rest-api.expose-error-details` (default `true` for backward compatibility). When `false`, generic exceptions return "Internal server error" instead of `e.getMessage()`

**P1 — Should fix**
4. Synchronize `getConsumerDetails()` through the same lock as `ManualKafkaConsumer`, or use separate `KafkaConsumer` instances for metadata queries (also listed under Kafka concerns)
5. Document `isolation.level=read_uncommitted` as intentional design choice in README
6. Redact consumer record values from INFO logs — log only topic/partition/offset

**P2 — Document**
7. Add "Security Considerations" section to README covering: auth delegation model, CSRF with/without Spring Security, static file exposure, rate limiting responsibility
8. Document at-least-once semantics for DLT routing (consumers must be idempotent)

---

## Summary of Priorities

### P0 — Fix Before Release

| # | Area | Action | Severity |
|---|------|--------|----------|
| 1 | Backend | Fix duplicate `DEFAULT_GROUP_ID` constants (different values = bug) | High |
| 2 | Kafka | Fix `KafkaConsumer` thread-safety in `getConsumerDetails()` — concurrent access without synchronization | High |
| 3 | Security | Validate topic names with regex and reject invalid — prevents log injection and serves as input validation | High |
| 4 | Backend | Fix `poll()` to return 204/404 instead of 200-with-null | High |
| 5 | Backend | Guard against NPE in batch poll when only `partition` is provided | High |
| 6 | Frontend | Add confirmation dialog for "Drain DLT" action | High |
| 7 | Frontend | Add focus trap and ARIA attributes to DiffModal | High |
| 8 | Frontend | Add `role="alert"` to toast container for screen readers | High |

### P1 — Fix Soon

| # | Area | Action | Severity |
|---|------|--------|----------|
| 9 | Kafka | Switch `ManualKafkaConsumer` from global lock to per-consumer lock | High |
| 10 | Security | Add `@Size` limit on correction payload | High |
| 11 | Security | Make error detail exposure configurable (`expose-error-details` property) | Medium |
| 12 | Backend | Extract boilerplate try/catch/MDC into `@ControllerAdvice(assignableTypes = KafkaOpsController.class)` | Medium |
| 13 | Kafka | Switch `extractConnectionProps` from allow-list to deny-list (copy all, override `group.id` + deserializers) | Medium |
| 14 | Kafka | Change malformed cycle header fallback from 0 to `maxCycles - 1` | Medium |
| 15 | Kafka | Create per-route `KafkaTemplate` using same container factory as consumer's `getContainerName()` | Medium |
| 16 | Frontend | Fix `JsonViewer` global collapsed state and `expandedIndex` fragility | Medium |
| 17 | Security | Redact consumer record values from INFO logs | Medium |

### P2 — Improve / Document

| # | Area | Action | Severity |
|---|------|--------|----------|
| 18 | Security | Add "Security Considerations" section to README | Medium |
| 19 | Security | Document `isolation.level=read_uncommitted` | Medium |
| 20 | Frontend | Extract shared utilities and components to reduce ~100-line duplication | Medium |
| 21 | Backend | Replace `@ComponentScan` with explicit `@Import` or `@Bean` | Medium |
| 22 | Backend | Eliminate raw types throughout | Medium |
| 23 | Kafka | Document at-least-once semantics | Low |
| 24 | Frontend | Add mobile responsive breakpoints | Medium |
| 25 | Frontend | Centralize SVG icons, use Mithril `oncreate` for scroll-into-view | Low |

---

## Consequences

### Positive

- Comprehensive understanding of library's strengths and gaps across all four domains
- Two real bugs identified: duplicate group ID constants and `KafkaConsumer` thread-safety in `getConsumerDetails()`
- Clear prioritized action list — P0 items are small, high-impact fixes
- No critical security vulnerabilities — the delegation model (host app owns auth) is sound
- Kafka patterns are fundamentally correct — `MANUAL_IMMEDIATE`, timestamp cutoff, cycle counter, byte-level forwarding are all production-safe
- Frontend is functional with good accessibility foundations — main gaps are DiffModal focus trap and code duplication

### Negative

- P0 items include one concurrency bug (`getConsumerDetails()`) that requires careful testing
- Extracting controller boilerplate into `@ControllerAdvice` requires verifying it doesn't affect host applications
- Frontend accessibility fixes (focus trap, ARIA) require understanding Mithril lifecycle hooks
- Per-route `KafkaTemplate` adds complexity to DLT router initialization

### Risks Accepted

- **No built-in auth/rate-limiting**: Intentional — the library is an embedded tool, not a standalone service. Host applications bring their own security stack. Will be documented in README.
- **No DLT routing stop endpoint**: Intentionally omitted. In multi-pod deployments, `POST /stop` on one pod doesn't stop the consumer group — the operator would need to hit every pod. The self-stop via timestamp cutoff + idle timeout is the correct distributed pattern. Containers also stop when all pre-cutoff messages are processed.
- **Buffered records after `container.stop()`**: After the cutoff stop, already-fetched records in the internal buffer may continue to be delivered. This is not an issue — offsets are not committed for cutoff-exceeded records, so they will be reprocessed on the next routing window.
- **Global synchronization on `ManualKafkaConsumer`**: Kafka consumers are not thread-safe. The current global lock is correct but suboptimal — per-consumer locking is an optimization (P1), not a correctness fix.
- **No CSP header**: Low risk for an internal ops tool. Can be added by host application's security configuration.
- **Static files served when disabled**: Spring Boot default behavior. Documenting mitigation (Spring Security path deny) is sufficient.
- **`isolation.level=read_uncommitted`**: Intentional for debugging — ops engineers need to see all messages including uncommitted transactions. Will be documented.
- **Error messages in responses**: Intentional for ops usability — exposing `e.getMessage()` helps operators diagnose issues without checking logs. Will be made configurable via property for production environments that need to hide internal details.
