# Spring Boot Starter Kafka Ops

[![Maven Central](https://img.shields.io/maven-central/v/io.github.ahmedsarie/spring-boot-starter-kafka-ops)](https://central.sonatype.com/artifact/io.github.ahmedsarie/spring-boot-starter-kafka-ops)
[![CI](https://github.com/AhmedSarie/spring-boot-starter-kafka-ops/actions/workflows/ci.yml/badge.svg)](https://github.com/AhmedSarie/spring-boot-starter-kafka-ops/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

A Spring Boot starter that adds REST endpoints and an embedded web console for Kafka message operations — poll, retry, corrections, batch browse, and DLT routing — without any extra infrastructure.

- **Poll & browse** — Inspect messages by offset or timestamp with full metadata (key, headers, timestamp)
- **Retry** — Re-consume a failed message through your existing consumer logic
- **Correct** — Edit a payload and send it directly to your consumer
- **DLT routing** — Automatically drain Dead Letter Topics back to retry topics
- **Web console** — Browser-based UI embedded in your service, no separate deployment
- **Zero config** — Auto-discovers your consumers at startup, uses your app's own serialization

![Console Demo](docs/assets/console-demo.gif)

## Installation

**Maven:**
```xml
<dependency>
    <groupId>io.github.ahmedsarie</groupId>
    <artifactId>spring-boot-starter-kafka-ops</artifactId>
    <version>0.1.7</version>
</dependency>
```

**Gradle:**
```kotlin
implementation("io.github.ahmedsarie:spring-boot-starter-kafka-ops:0.1.7")
```

## Quick Start

**1. Enable the API and console:**

```yaml
kafka:
  ops:
    rest-api:
      enabled: true
    console:
      enabled: true
```

**2. Implement `KafkaOpsAwareConsumer` on your Kafka consumer:**

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

**3. Open the console** at [http://localhost:8080/kafka-ops/index.html](http://localhost:8080/kafka-ops/index.html)

## Documentation

Full documentation is available at **[ahmedsarie.github.io/spring-boot-starter-kafka-ops](https://ahmedsarie.github.io/spring-boot-starter-kafka-ops)**:

- [Getting Started](https://ahmedsarie.github.io/spring-boot-starter-kafka-ops/getting-started/) — Installation and setup
- [Configuration](https://ahmedsarie.github.io/spring-boot-starter-kafka-ops/configuration/) — All available properties
- [REST API](https://ahmedsarie.github.io/spring-boot-starter-kafka-ops/rest-api/) — Full endpoint reference
- [Web Console](https://ahmedsarie.github.io/spring-boot-starter-kafka-ops/console/) — Console features
- [DLT Routing](https://ahmedsarie.github.io/spring-boot-starter-kafka-ops/dlt-routing/) — Dead Letter Topic routing
- [Message Formats](https://ahmedsarie.github.io/spring-boot-starter-kafka-ops/value-formats/) — Avro, Protobuf, custom key and value codecs
- [Security](https://ahmedsarie.github.io/spring-boot-starter-kafka-ops/security/) — Securing endpoints and console

## Security

This library does not provide its own authentication or authorization. Secure the endpoints and console using your application's existing security infrastructure (Spring Security, API gateway, etc.). See the [security guide](https://ahmedsarie.github.io/spring-boot-starter-kafka-ops/security/) for details.

## Development

```bash
git clone git@github.com:AhmedSarie/spring-boot-starter-kafka-ops.git
cd spring-boot-starter-kafka-ops
./mvnw verify
```

## License

MIT
