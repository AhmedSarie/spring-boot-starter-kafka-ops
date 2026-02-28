# Complete Session Context for spring-boot-starter-kafka-ops

## Project Background
- **Original Repo**: kafka-reconsumer (a Spring Boot starter created for your previous company)
- **Purpose**: Error handling strategy for Kafka messages with REST API endpoints for retry, poll, and corrections
- **Original Package**: com.wayfair.kafka.retry
- **Original Config Prefix**: wayfair.kafka.re-consumer.*
- **User Requirement**: Create a company-agnostic version focusing ONLY on REST API components for direct operations:
  - Poll messages by topic/partition/offset
  - Retry messages by topic/partition/offset
  - Send corrections directly to topics
- **What to Remove**: All Kafka event publishing/consuming functionality (the consumer/async approach)
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
- **Controllers** (To Include):
  - RetryConsumerController: Endpoints for retry, poll, and corrections

- **Models** (To Include):
  - KafkaRetryRequest: Topic, partition, offset
  - KafkaCorrectionRequest: Topic, payload
  - KafkaRetryResponse: Response ID
  - KafkaPollResponse: Payload result

- **Core Interfaces** (To Include):
  - KafkaRetryAwareConsumer: Interface for consumer integration

- **Services** (To Include):
  - KafkaTopicConsumerService: Core service for operations (simplified version)
  - ManualKafkaConsumer: For manual Kafka consumption (simplified version)
  - KafkaRetryConsumerRegistry: Registry for consumers (simplified version)

- **Configuration** (To Include):
  - KafkaOpsConfiguration: Auto-configuration (simplified version)
  - KafkaOpsProperties: Configuration properties (simplified version)

- **Components to Exclude**:
  - RetryRequestPublisherController: Not needed as we won't implement Kafka publishing
  - RetryRequestPublisherService: Not needed as we won't implement Kafka publishing
  - KafkaRetryRequestConsumer: Not needed as we won't implement Kafka-based retry requests

## REST Endpoints to Implement

We will only implement the following endpoints:

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

3. **Alternative Correction Endpoint** (this was previously #4):
   ```
   POST /operational/consumer-retries/corrections/my-topic
   Content-Type: application/json
   {"field":"value"}
   ```

Note: The original endpoint #3 (Process Correction via POST to /corrections) will be removed to simplify the implementation.

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

## Code Migration Approach
1. **Package Renaming**:
   - From: `com.wayfair.kafka.retry.*`
   - To: `io.github.ahmedsarie.kafka.ops.*`

2. **Class Renaming**:
   - From: `KafkaRetry*` classes
   - To: `KafkaOps*` classes

3. **File Copying Strategy**:
   - DO NOT directly copy files from the old repository
   - Create new files with clean implementations
   - Reference the old code structure but implement with simplified logic
   - Remove all company-specific code, comments, and identifiers

4. **Implementation Priorities**:
   - First: Core interfaces and models
   - Second: Service layer with simplified logic
   - Third: REST controller
   - Fourth: Configuration and properties
   - Fifth: Unit tests

## Configuration Properties Translation
| Original Property | New Property | Include? |
|-------------------|--------------|----------|
| wayfair.kafka.re-consumer.group-id | kafka.ops.group-id | Yes |
| wayfair.kafka.re-consumer.max-poll-interval-ms | kafka.ops.max-poll-interval-ms | Yes |
| wayfair.kafka.re-consumer.rest-api.enabled | kafka.ops.rest-api.enabled | Yes |
| wayfair.kafka.re-consumer.rest-api.retry-endpoint-url | kafka.ops.rest-api.retry-endpoint-url | Yes |
| wayfair.kafka.re-consumer.rest-api.publish-endpoint-url | - | No - Not needed |
| wayfair.kafka.re-consumer.kafka-api.topic | - | No - Not needed |
| wayfair.kafka.re-consumer.kafka-api.bootstrap-servers | - | No - Not needed |

## Additional Notes
- Current project structure set up but no Java classes implemented yet
- Planning to use standard Maven package structure
- Will significantly simplify the implementation by:
  - Removing all Kafka-based publisher/consumer functionality
  - Focusing only on the REST API for direct message operations
  - Simplifying the consumer registry and service classes
  - Reducing configuration properties to only what's needed
- Will maintain REST API signatures for the selected endpoints with company-agnostic naming
- The core focus is on endpoints 1, 2, and the alternative correction endpoint (previously #4)
- This simplification will make the library more focused, easier to maintain, and more aligned with the requirements

## Dependency Replacement
### Replace Company-Specific Dependencies:

1. **Parent POM Replacement**:
   - **Original**: `<parent><groupId>com.wayfair</groupId><artifactId>library-starter</artifactId>...</parent>`
   - **Replace with**: Standard Spring Boot parent
     ```xml
     <parent>
       <groupId>org.springframework.boot</groupId>
       <artifactId>spring-boot-starter-parent</artifactId>
       <version>3.2.2</version>
       <relativePath/>
     </parent>
     ```

2. **Repository Replacement**:
   - **Remove**: All internal repository references
     ```xml
     <repository>
       <id>central</id>
       <url>https://artifactorybase.service.csnzoo.com/artifactory/mvn-all</url>
       ...
     </repository>
     ```
   - **Replace with**: Standard Maven Central and required public repositories
     ```xml
     <repository>
       <id>confluent</id>
       <url>https://packages.confluent.io/maven/</url>
     </repository>
     <repository>
       <id>maven-central</id>
       <url>https://repo.maven.apache.org/maven2/</url>
     </repository>
     ```

3. **Dependency Versions**:
   - Define all version numbers in properties section
   - Use standard open-source versions that are widely available

4. **Custom Utilities**:
   - If any internal utility classes were used, replace with standard Java/Spring alternatives
   - For example, use standard logging instead of any proprietary logging implementations