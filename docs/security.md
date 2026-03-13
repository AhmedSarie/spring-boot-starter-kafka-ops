# Security

The library does not provide authentication, authorization, or CSRF protection. Secure the endpoints and console using your application's existing security infrastructure — the same way you would secure Swagger UI or Spring Boot Actuator.

## Spring Security

If your application uses Spring Security with CSRF protection (enabled by default), the POST endpoints will require a valid CSRF token.

If your application does **not** use Spring Security, consider restricting access to these endpoints to trusted networks since they perform state-changing operations (retry, corrections, DLT routing).

## Static file access

The console's static files (`/kafka-ops/**`) are served by Spring Boot's default resource handler whenever the library is on the classpath. The API endpoints require `kafka.ops.rest-api.enabled=true`, so the console UI is non-functional without that property — but the static HTML/JS/CSS files remain accessible.

To fully block access to the console files, use Spring Security to restrict `/kafka-ops/**`:

=== "Java"

    ```java
    @Bean
    public SecurityFilterChain kafkaOpsSecurityChain(HttpSecurity http) throws Exception {
        return http
            .securityMatcher("/kafka-ops/**", "/operational/consumer-retries/**")
            .authorizeHttpRequests(auth -> auth.anyRequest().hasRole("OPS"))
            .build();
    }
    ```

=== "Kotlin"

    ```kotlin
    @Bean
    fun kafkaOpsSecurityChain(http: HttpSecurity): SecurityFilterChain =
        http
            .securityMatcher("/kafka-ops/**", "/operational/consumer-retries/**")
            .authorizeHttpRequests { it.anyRequest().hasRole("OPS") }
            .build()
    ```

## Recommendations

- Run the console and API only in environments where operators need access (staging, production support)
- Use your API gateway or network policies to restrict access if Spring Security is not in use
- The DLT router produces messages to Kafka — ensure your Kafka ACLs allow the application's principal to write to retry topics
