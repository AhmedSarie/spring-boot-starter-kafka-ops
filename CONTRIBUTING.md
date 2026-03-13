# Contributing

Thanks for your interest in contributing to Spring Boot Starter Kafka Ops!

## Getting started

```bash
git clone git@github.com:AhmedSarie/spring-boot-starter-kafka-ops.git
cd spring-boot-starter-kafka-ops
./mvnw verify
```

## Making changes

1. Fork the repository and create a branch from `main`
2. Make your changes
3. Add or update tests as needed
4. Run `./mvnw verify` to make sure everything passes
5. Open a pull request against `main`

## Code style

- Follow the existing code conventions (see `CLAUDE.md` for details)
- Package-private by default — only make classes public if they're part of the library's API
- Use Lombok annotations (`@Slf4j`, `@RequiredArgsConstructor`, `@Getter`)
- Constructor injection via Lombok — no `@Autowired` on fields
- Tests use `// prepare`, `// when`, `// then` sections with `@DisplayName`

## Reporting bugs

Open an issue using the bug report template. Include:

- What you expected to happen
- What actually happened
- Steps to reproduce
- Spring Boot version and library version

## Suggesting features

Open an issue using the feature request template. Describe the use case and why it would be useful.

## License

By contributing, you agree that your contributions will be licensed under the MIT License.
