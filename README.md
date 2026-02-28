# Spring Boot Starter Kafka Ops

A Spring Boot starter that provides REST API endpoints for Kafka message operations:
- Retry messages by topic, partition, and offset
- Poll messages for inspection and debugging
- Send corrections (custom payloads) directly to consumers

## Features

- **REST API endpoints** for Kafka operations
- **Manual message processing** without additional topics
- **Simple integration** with Spring Kafka consumers
- **Configurable endpoints** and behavior

## Installation

Add the following dependency to your project:

```xml
<dependency>
    <groupId>io.github.ahmedsarie</groupId>
    <artifactId>spring-boot-starter-kafka-ops</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

## Quick Start

1. Add the dependency to your Spring Boot project

2. Implement the `KafkaRetryAwareConsumer` interface in your Kafka consumers:

```java
@Service
public class MyKafkaConsumer implements KafkaRetryAwareConsumer<MyEvent> {

    @KafkaListener(topics = "#{__listener.getTopicName()}")
    @Override
    public void consume(ConsumerRecord<String, MyEvent> consumerRecord) {
        // Your consumer logic
    }

    @Override
    public String getTopicName() {
        return "my-kafka-topic";
    }
}
```

3. Configure the library in your `application.yml`:

```yaml
kafka:
  ops:
    rest-api:
      enabled: true
      retry-endpoint-url: operational/consumer-retries
```

## Available REST Endpoints

### Poll a message
```
GET /operational/consumer-retries?topicName=my-topic&partition=0&offset=10
```

### Retry a message
```
POST /operational/consumer-retries
Content-Type: application/json

{
  "topic": "my-topic",
  "partition": 0,
  "offset": 10
}
```

### Send a correction
```
POST /operational/consumer-retries/corrections
Content-Type: application/json

{
  "topic": "my-topic",
  "payload": "{\"field\":\"value\"}"
}
```

Or using the alternative endpoint (to avoid payload escaping):
```
POST /operational/consumer-retries/corrections/my-topic
Content-Type: application/json

{"field":"value"}
```

## Configuration Options

| Property                               | Description                                  | Default                    |
|----------------------------------------|----------------------------------------------|----------------------------|
| kafka.ops.group-id                     | Consumer group ID for retry consumers        | default-retry-group-id     |
| kafka.ops.max-poll-interval-ms         | Maximum poll interval in milliseconds        | 5000                       |
| kafka.ops.rest-api.enabled             | Enable REST API endpoints                    | false                      |
| kafka.ops.rest-api.retry-endpoint-url  | Base URL for retry endpoints                 | operational/consumer-retries |

## License

This project is licensed under the MIT License - see the LICENSE file for details.