# Complete Session Context for spring-boot-starter-kafka-ops

## Project Background
- **Original Repo**: kafka-reconsumer (a Spring Boot starter created for your previous company)
- **Purpose**: Error handling strategy for Kafka messages with REST API endpoints for retry, poll, and corrections
- **Original Package**: com.wayfair.kafka.retry
- **Original Config Prefix**: wayfair.kafka.re-consumer.*
- **User Requirement**: Create a company-agnostic version with focus on REST API components (consumer/async approach not required)
- **Future Plans**: Add embedded UI for message operations

## Key Decisions
1. **Keep Java** instead of converting to Kotlin for better compatibility with both Java and Kotlin services
2. **Project Name**: spring-boot-starter-kafka-ops (chosen because it aligns with "operational" endpoints)
3. **Group ID**: io.github.ahmedsarie
4. **Package Structure**: io.github.ahmedsarie.kafka.ops
5. **Config Prefix**: kafka.ops.*
6. **License**: MIT

## Setup Progress
1. **SSH Configuration**:
   - Created SSH key: ~/.ssh/github_personal for ahmed-sarie@hotmail.com
   - Added to SSH agent and GitHub account
   - Updated ~/.ssh/config to support multiple GitHub accounts:
     ```
     Host github.com-personal
       HostName github.com
       User git
       IdentityFile ~/.ssh/github_personal
       IdentitiesOnly yes
     ```

2. **Repository Setup**:
   - Created GitHub repository: https://github.com/AhmedSarie/spring-boot-starter-kafka-ops
   - Initialized local repository: ~/IdeaProjects/personal/spring-boot-starter-kafka-ops
   - Connected with remote: git@github.com-personal:AhmedSarie/spring-boot-starter-kafka-ops.git
   - Created initial commit with basic structure

3. **Initial Files**:
   - pom.xml with Spring Boot parent and necessary dependencies
   - README.md with documentation and usage instructions
   - LICENSE file (MIT)
   - .gitignore for Java/Maven projects
   - src/main/resources/META-INF/spring.factories for autoconfiguration

## Original Component Analysis
- **Controllers**:
  - RetryConsumerController: Endpoints for retry, poll, and corrections
  - RetryRequestPublisherController: Endpoint for publishing retry requests

- **Models**:
  - KafkaRetryRequest: Topic, partition, offset
  - KafkaCorrectionRequest: Topic, payload
  - KafkaRetryResponse: Response ID
  - KafkaPollResponse: Payload result

- **Core Interfaces**:
  - KafkaRetryAwareConsumer: Interface for consumer integration

- **Services**:
  - KafkaTopicConsumerService: Core service for operations
  - ManualKafkaConsumer: For manual Kafka consumption
  - KafkaRetryConsumerRegistry: Registry for consumers

- **Configuration**:
  - KafkaRetryConfiguration: Auto-configuration
  - KafkaRetryProperties: Configuration properties

## REST Endpoints to Implement
1. **Poll Message**:
   ```
   GET /operational/consumer-retries?topicName=my-topic&partition=0&offset=10
   ```

2. **Retry Message**:
   ```
   POST /operational/consumer-retries
   Content-Type: application/json
   {
     "topic": "my-topic",
     "partition": 0,
     "offset": 10
   }
   ```

3. **Process Correction**:
   ```
   POST /operational/consumer-retries/corrections
   Content-Type: application/json
   {
     "topic": "my-topic",
     "payload": "{\"field\":\"value\"}"
   }
   ```

4. **Alternative Correction Endpoint**:
   ```
   POST /operational/consumer-retries/corrections/my-topic
   Content-Type: application/json
   {"field":"value"}
   ```

## Implementation Steps
1. **Core Classes**:
   - Create interface package with KafkaRetryAwareConsumer
   - Create model package with request/response classes
   - Create service package with core services

2. **Configuration**:
   - Create KafkaOpsProperties
   - Create KafkaOpsConfiguration for auto-configuration

3. **Controllers**:
   - Create web package with controller classes

4. **Testing**:
   - Create comprehensive unit tests
   - Create integration tests with embedded Kafka

5. **Documentation**:
   - Update README.md with comprehensive usage instructions
   - Add Javadoc to all public classes and methods

## Original Code References
- The original code used Jakarta EE validation annotations
- Used Spring Boot 3.x compatibility
- Used Lombok for reducing boilerplate
- Used Spring Kafka for integration
- Original endpoint paths had a configurable prefix with defaults:
  - Retry: /${wayfair.kafka.re-consumer.rest-api.retry-endpoint-url:operational/consumer-retries}
  - Publish: /${wayfair.kafka.re-consumer.rest-api.publish-endpoint-url:operational/publish-consumer-retries}

## Configuration Properties Translation
| Original Property | New Property |
|-------------------|--------------|
| wayfair.kafka.re-consumer.group-id | kafka.ops.group-id |
| wayfair.kafka.re-consumer.max-poll-interval-ms | kafka.ops.max-poll-interval-ms |
| wayfair.kafka.re-consumer.rest-api.enabled | kafka.ops.rest-api.enabled |
| wayfair.kafka.re-consumer.rest-api.retry-endpoint-url | kafka.ops.rest-api.retry-endpoint-url |
| wayfair.kafka.re-consumer.rest-api.publish-endpoint-url | kafka.ops.rest-api.publish-endpoint-url |
| wayfair.kafka.re-consumer.kafka-api.topic | kafka.ops.kafka-api.topic |
| wayfair.kafka.re-consumer.kafka-api.bootstrap-servers | kafka.ops.kafka-api.bootstrap-servers |

## Additional Notes
- Current project structure set up but no Java classes implemented yet
- Planning to use standard Maven package structure
- Will need to implement but simplify the Kafka consumer registry logic
- Will maintain REST API signatures but with company-agnostic naming