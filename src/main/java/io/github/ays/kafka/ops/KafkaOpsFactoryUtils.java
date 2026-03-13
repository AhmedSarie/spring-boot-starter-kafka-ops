package io.github.ays.kafka.ops;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;

final class KafkaOpsFactoryUtils {

  private static final String DEF_FACTORY_BEAN_NAME = "kafkaListenerContainerFactory";
  private static final Set<String> DENY_LIST_KEYS = Set.of(
      "group.id", "key.deserializer", "value.deserializer", "client.id");

  private KafkaOpsFactoryUtils() {
  }

  @SuppressWarnings("unchecked")
  static ConcurrentKafkaListenerContainerFactory resolveFactory(
      KafkaOpsAwareConsumer consumer, ListableBeanFactory beanFactory) {
    var container = consumer.getContainer();
    var name = container != null ? container.getName() : DEF_FACTORY_BEAN_NAME;
    return (ConcurrentKafkaListenerContainerFactory) beanFactory.getBean(name);
  }

  static Map<String, Object> extractConnectionProps(
      KafkaOpsAwareConsumer consumer, ListableBeanFactory beanFactory) {
    var factory = resolveFactory(consumer, beanFactory);
    var originalProps = factory.getConsumerFactory().getConfigurationProperties();
    var props = new HashMap<String, Object>();
    originalProps.forEach((k, v) -> {
      var key = String.valueOf(k);
      if (!DENY_LIST_KEYS.contains(key)) {
        props.put(key, v);
      }
    });
    return props;
  }

  static KafkaTemplate<byte[], byte[]> createByteArrayKafkaTemplate(
      KafkaOpsAwareConsumer consumer, ListableBeanFactory beanFactory) {
    var producerProps = extractConnectionProps(consumer, beanFactory);
    producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
    producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
    return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(producerProps));
  }

  static Collection<KafkaOpsAwareConsumer> getConsumerBeans(ListableBeanFactory beanFactory) {
    return beanFactory.getBeansOfType(KafkaOpsAwareConsumer.class).values();
  }
}
