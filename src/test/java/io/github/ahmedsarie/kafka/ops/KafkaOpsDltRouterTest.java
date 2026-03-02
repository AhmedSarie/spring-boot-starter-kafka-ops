package io.github.ahmedsarie.kafka.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.ahmedsarie.kafka.ops.KafkaOpsService.NoConsumerFoundException;
import java.lang.reflect.Field;
import java.util.Map;
import org.apache.kafka.common.serialization.IntegerDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;

class KafkaOpsDltRouterTest {

    private ListableBeanFactory beanFactory;
    private KafkaOpsProperties properties;

    @BeforeEach
    void setUp() {
        beanFactory = mock(ListableBeanFactory.class);
        properties = new KafkaOpsProperties(null, "test-ops-group", null, null,
            new KafkaOpsProperties.DltRouting(true, 5, 30, 3));
    }

    @Test
    @DisplayName("Router creates container when consumer declares both DLT and retry topics")
    void shouldCreateContainerWhenBothDltAndRetryDeclared() {
        // prepare
        var consumer = mock(KafkaOpsAwareConsumer.class);
        when(consumer.getTopic()).thenReturn(TopicConfig.of("orders"));
        when(consumer.getDltTopic()).thenReturn(TopicConfig.of("orders.DLT"));
        when(consumer.getRetryTopic()).thenReturn(TopicConfig.of("orders-retry"));

        when(beanFactory.getBeansOfType(KafkaOpsAwareConsumer.class))
            .thenReturn(Map.of("ordersBean", consumer));

        var mockContainer = mock(ConcurrentKafkaListenerContainerFactory.class);
        when(beanFactory.getBean(anyString())).thenReturn(mockContainer);
        var consumerFactoryMock = mock(DefaultKafkaConsumerFactory.class);
        when(mockContainer.getConsumerFactory()).thenReturn(consumerFactoryMock);
        when(consumerFactoryMock.getConfigurationProperties()).thenReturn(testConsumerProp());

        var router = new KafkaOpsDltRouter(beanFactory, properties);

        // when
        router.afterPropertiesSet();

        // then
        var containers = getRoutesField(router);
        assertTrue(containers.containsKey("orders"));
        assertEquals(1, containers.size());
    }

    @Test
    @DisplayName("Router skips consumer that declares only DLT topic without retry topic")
    void shouldSkipConsumerWithOnlyDlt() {
        // prepare
        var consumer = mock(KafkaOpsAwareConsumer.class);
        when(consumer.getTopic()).thenReturn(TopicConfig.of("payments"));
        when(consumer.getDltTopic()).thenReturn(TopicConfig.of("payments.DLT"));
        when(consumer.getRetryTopic()).thenReturn(null);

        when(beanFactory.getBeansOfType(KafkaOpsAwareConsumer.class))
            .thenReturn(Map.of("paymentsBean", consumer));

        var router = new KafkaOpsDltRouter(beanFactory, properties);

        // when
        router.afterPropertiesSet();

        // then
        var containers = getRoutesField(router);
        assertTrue(containers.isEmpty());
    }

    @Test
    @DisplayName("Router skips consumer that declares only retry topic without DLT topic")
    void shouldSkipConsumerWithOnlyRetry() {
        // prepare
        var consumer = mock(KafkaOpsAwareConsumer.class);
        when(consumer.getTopic()).thenReturn(TopicConfig.of("notifications"));
        when(consumer.getDltTopic()).thenReturn(null);
        when(consumer.getRetryTopic()).thenReturn(TopicConfig.of("notifications-retry"));

        when(beanFactory.getBeansOfType(KafkaOpsAwareConsumer.class))
            .thenReturn(Map.of("notificationsBean", consumer));

        var router = new KafkaOpsDltRouter(beanFactory, properties);

        // when
        router.afterPropertiesSet();

        // then
        var containers = getRoutesField(router);
        assertTrue(containers.isEmpty());
    }

    @Test
    @DisplayName("Router skips consumer with neither DLT nor retry topic")
    void shouldSkipConsumerWithNeitherDltNorRetry() {
        // prepare
        var consumer = mock(KafkaOpsAwareConsumer.class);
        when(consumer.getTopic()).thenReturn(TopicConfig.of("simple"));
        when(consumer.getDltTopic()).thenReturn(null);
        when(consumer.getRetryTopic()).thenReturn(null);

        when(beanFactory.getBeansOfType(KafkaOpsAwareConsumer.class))
            .thenReturn(Map.of("simpleBean", consumer));

        var router = new KafkaOpsDltRouter(beanFactory, properties);

        // when
        router.afterPropertiesSet();

        // then
        var containers = getRoutesField(router);
        assertTrue(containers.isEmpty());
    }

    @Test
    @DisplayName("start throws NoConsumerFoundException when topic has no DLT router configured")
    void shouldThrowWhenStartingUnknownTopic() {
        // prepare
        when(beanFactory.getBeansOfType(KafkaOpsAwareConsumer.class))
            .thenReturn(Map.of());
        var router = new KafkaOpsDltRouter(beanFactory, properties);
        router.afterPropertiesSet();

        // when + then
        assertThrows(NoConsumerFoundException.class, () -> router.start("unknown-topic"));
    }

    @Test
    @DisplayName("startFromTimestamp throws NoConsumerFoundException when topic has no DLT router configured")
    void shouldThrowWhenStartFromTimestampUnknownTopic() {
        // prepare
        when(beanFactory.getBeansOfType(KafkaOpsAwareConsumer.class))
            .thenReturn(Map.of());
        var router = new KafkaOpsDltRouter(beanFactory, properties);
        router.afterPropertiesSet();

        // when + then
        assertThrows(NoConsumerFoundException.class,
            () -> router.startFromTimestamp("unknown-topic", 1000L, false));
    }

    @Test
    @DisplayName("destroy stops all containers and shuts down scheduler")
    void shouldDestroyCleanly() {
        // prepare
        when(beanFactory.getBeansOfType(KafkaOpsAwareConsumer.class))
            .thenReturn(Map.of());
        var router = new KafkaOpsDltRouter(beanFactory, properties);
        router.afterPropertiesSet();

        // when + then (no exception)
        router.destroy();
    }

    @Test
    @DisplayName("Router registers multiple consumers that both have DLT and retry")
    void shouldRegisterMultipleConsumers() {
        // prepare
        var consumer1 = mock(KafkaOpsAwareConsumer.class);
        when(consumer1.getTopic()).thenReturn(TopicConfig.of("orders"));
        when(consumer1.getDltTopic()).thenReturn(TopicConfig.of("orders.DLT"));
        when(consumer1.getRetryTopic()).thenReturn(TopicConfig.of("orders-retry"));

        var consumer2 = mock(KafkaOpsAwareConsumer.class);
        when(consumer2.getTopic()).thenReturn(TopicConfig.of("payments"));
        when(consumer2.getDltTopic()).thenReturn(TopicConfig.of("payments.DLT"));
        when(consumer2.getRetryTopic()).thenReturn(TopicConfig.of("payments-retry"));

        when(beanFactory.getBeansOfType(KafkaOpsAwareConsumer.class))
            .thenReturn(Map.of("ordersBean", consumer1, "paymentsBean", consumer2));

        var mockContainer = mock(ConcurrentKafkaListenerContainerFactory.class);
        when(beanFactory.getBean(anyString())).thenReturn(mockContainer);
        var consumerFactoryMock = mock(DefaultKafkaConsumerFactory.class);
        when(mockContainer.getConsumerFactory()).thenReturn(consumerFactoryMock);
        when(consumerFactoryMock.getConfigurationProperties()).thenReturn(testConsumerProp());

        var router = new KafkaOpsDltRouter(beanFactory, properties);

        // when
        router.afterPropertiesSet();

        // then
        var containers = getRoutesField(router);
        assertEquals(2, containers.size());
        assertTrue(containers.containsKey("orders"));
        assertTrue(containers.containsKey("payments"));
    }

    private Map<Object, Object> testConsumerProp() {
        return Map.of(
            "key.deserializer", IntegerDeserializer.class,
            "value.deserializer", StringDeserializer.class,
            "isolation.level", "read_uncommitted",
            "group.id", "reconsumerId",
            "bootstrap.servers", "127.0.0.1:50120",
            "auto.offset.reset", "earliest"
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, ?> getRoutesField(KafkaOpsDltRouter router) {
        try {
            Field field = KafkaOpsDltRouter.class.getDeclaredField("routes");
            field.setAccessible(true);
            return (Map<String, ?>) field.get(router);
        } catch (Exception e) {
            throw new RuntimeException("Failed to access routes field", e);
        }
    }
}
