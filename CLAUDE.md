# CLAUDE.md

## Build & Test

```bash
./mvnw verify          # compile + run all tests
./mvnw test            # run tests only
./mvnw compile         # compile only
```

## Project Overview

Spring Boot starter that provides REST endpoints for Kafka message operations (poll, retry, corrections). All classes live in `io.github.ays.kafka.ops` — flat package, no subpackages for main sources.

## Code Conventions

### Visibility

- **Package-private by default** for all implementation classes (controllers, services, config, DTOs, utilities)
- **Public only** for: `KafkaOpsAwareConsumer` (consumer interface), `KafkaPollResponse` (response DTO), `KafkaOpsService` (service + its `NoConsumerFoundException` inner class), `KafkaOpsConfiguration` and `KafkaOpsProperties` (required by Spring Boot autoconfiguration)
- Never make a class public unless it's part of the library's API or required by Spring

### Lombok

- `@Slf4j` on every class that logs
- `@RequiredArgsConstructor` for constructor injection (all dependencies are `final` fields)
- `@Getter` / `@Setter` on DTOs — never write getters/setters manually
- `@SneakyThrows` for methods where checked exceptions add no value
- `@Data` only in tests for helper POJOs

### Naming

- Classes: `KafkaOps` prefix for library classes (e.g. `KafkaOpsService`, `KafkaOpsRequest`)
- Constants: `UPPER_SNAKE_CASE`, declared `private static final`
- Local variables: use `var` when the type is obvious from context
- Test methods: descriptive names, pair with `@DisplayName` for readable output

### Formatting

- Egyptian braces (opening brace on same line)
- Wrap method parameters when they exceed ~100 chars
- Single blank line between methods
- No wildcard imports — all imports are explicit
- Static imports for: assertion methods, mockito methods, utility methods (`format`, `isEmpty`, etc.)

### Spring Patterns

- Beans created via `@Bean` methods in `KafkaOpsConfiguration` with `@ConditionalOnMissingBean`
- Controller enabled via `@ConditionalOnProperty(value = "kafka.ops.rest-api.enabled", havingValue = "true")`
- Constructor injection via Lombok `@RequiredArgsConstructor` — never use `@Autowired` on fields
- Configuration properties use `@ConfigurationProperties` with nested static inner classes

### Error Handling

- Custom exceptions extend `RuntimeException` (unchecked)
- Controller maps exceptions to HTTP status: `NoConsumerFoundException` → 404, others → 500
- Use `ResponseStatusException` — no `@ExceptionHandler` classes
- MDC for request tracking: set `api-response-id` in try, `MDC.clear()` in finally

### Logging

- Always use `@Slf4j` — never instantiate loggers manually
- `log.info()` for operations start/finish
- `log.warn()` for recoverable issues (empty poll results)
- `log.error()` for failures with exception object
- Use `String.format()` in controller logs, `{}` placeholders in service/utility logs

## Test Conventions

### Structure

- Comments mark sections: `// prepare`, `// when`, `// then`
- `@DisplayName` on every test for readable output
- `@BeforeEach` to reset mocks between tests

### Unit Tests

- Mock dependencies with `mock(Class.class)`
- Use `@WebMvcTest` + `@ContextConfiguration` for controller tests
- Use `@MockBean` for Spring context mocks
- Verify behavior with `verify()`, not just return values

### Integration Tests

- `@EmbeddedKafka` for real Kafka broker
- `@TestConfiguration` inner static class for test-specific beans
- `@TestInstance(Lifecycle.PER_CLASS)` when sharing state across tests

### Test Utilities

- `LogAssert` (in `util/` subpackage) for asserting log output with Awaitility
- Avro test classes (in `avro/` subpackage) for serialization tests

## README

- After making changes that affect the library's public API, configuration, features, or usage, update `README.md` to reflect the latest state
- This includes: new endpoints, new configuration properties, new UI features, changed defaults, removed features

## CI/CD

- PRs trigger `ci.yml` (build + test)
- Merge to `main` triggers `release.yml` (test → publish to Maven Central → tag → bump version)
- Version in `pom.xml` is always `X.Y.Z-SNAPSHOT` — the release workflow strips `-SNAPSHOT` for publishing
- Patch version auto-increments after each release
- For minor/major bumps, update the version in `pom.xml` as part of your PR
