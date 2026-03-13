# Spring Boot Starter Kafka Ops

A Spring Boot starter that adds REST endpoints and an embedded web console to your service for Kafka message operations — poll, retry, corrections, batch browse, and DLT routing — without any extra infrastructure.

## Why?

When your Kafka consumer fails to process a message, you typically need to manually re-consume it or write custom tooling. This starter gives you that tooling out of the box:

- **Poll** — Inspect a message at a specific topic/partition/offset with full metadata
- **Batch browse** — Browse multiple messages by offset range or timestamp
- **Retry** — Re-consume a message through your existing consumer logic
- **Correct** — Edit and send a corrected payload directly to your consumer
- **DLT routing** — Route messages from a Dead Letter Topic back to the retry topic automatically
- **Web Console** — Browser-based UI embedded in your service, no separate deployment

## How is this different from Kafka UI / AKHQ / Kafdrop?

| Capability | Standalone Kafka UIs | kafka-ops |
|---|---|---|
| Retry through your app's business logic | No | Yes |
| Edit & correct, re-process through your app | No | Yes |
| Auto-discovers your consumers at startup | No | Yes |
| Uses your app's own serialization | Schema Registry only | Yes |
| Zero deployment (embedded in your service) | No | Yes |
| DLT drain on demand | No | Yes |
| Topic/schema/ACL management | Yes | No (out of scope) |

This library is an **embedded ops tool** for message-level investigation and remediation. It is not a Kafka cluster management platform.

## Quick links

- [Getting Started](getting-started.md) — Install and set up in 3 steps
- [Configuration](configuration.md) — All available properties
- [REST API](rest-api.md) — Full endpoint reference
- [Web Console](console.md) — Console features and screenshots
- [DLT Routing](dlt-routing.md) — Automatic Dead Letter Topic routing
- [Message Formats](value-formats.md) — Avro, Protobuf, custom key and value codecs
- [Security](security.md) — Securing the endpoints and console
