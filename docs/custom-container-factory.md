# Custom Container Factory

If your consumer uses a custom `KafkaListenerContainerFactory`, tell the library so it resolves the correct deserializer configuration for poll and browse operations.

## Usage

Override `getContainer()` on your consumer:

=== "Java"

    ```java
    @Override
    public ContainerConfig getContainer() {
        return ContainerConfig.of("myCustomContainerFactory");
    }
    ```

=== "Kotlin"

    ```kotlin
    override fun getContainer() = ContainerConfig.of("myCustomContainerFactory")
    ```

The string must match the bean name of your `KafkaListenerContainerFactory`.

## When you need this

You typically need this when:

- You have multiple `KafkaListenerContainerFactory` beans with different deserializer configs
- Your consumer's `@KafkaListener` specifies a `containerFactory` attribute
- The default container factory doesn't match the serialization format your consumer uses

If you only have one container factory (the default), you don't need to set this.
