package io.github.ahmedsarie.kafka.ops;

import static java.util.Objects.isNull;

import io.github.ahmedsarie.kafka.ops.KafkaOpsService.NoConsumerFoundException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;

@Slf4j
@RequiredArgsConstructor
class KafkaOpsConsumerRegistry implements InitializingBean, DisposableBean {

  private static final String DEF_FACTORY_BEAN_NAME = "kafkaListenerContainerFactory";
  private final Map<String, Map.Entry<KafkaOpsAwareConsumer, KafkaConsumer>> registryMap = new ConcurrentHashMap<>();
  private final ListableBeanFactory beanFactory;
  private final String groupId;

  Map.Entry<KafkaOpsAwareConsumer, KafkaConsumer> find(String topic) {
    var entry = registryMap.get(topic);
    if (entry == null) {
      throw new NoConsumerFoundException(
          "unable to find consumer for topic=" + topic + " in the registry");
    }
    return entry;
  }

  @Override
  public void afterPropertiesSet() {
    beanFactory.getBeansOfType(KafkaOpsAwareConsumer.class).values().forEach(consumer -> {
      var consumerContainerName = consumer.getContainerName();
      var consumerContainer = isNull(consumerContainerName) ? DEF_FACTORY_BEAN_NAME : consumerContainerName;
      var factory = (ConcurrentKafkaListenerContainerFactory) beanFactory.getBean(consumerContainer);
      var props = new HashMap<>(factory.getConsumerFactory().getConfigurationProperties());
      props.replace("group.id", groupId);
      props.put("max.poll.records", 1);
      props.put("isolation.level", "read_uncommitted");
      var kafkaConsumer = new KafkaConsumer<>(props);
      registryMap.put(consumer.getTopicName(), Map.entry(consumer, kafkaConsumer));
    });
  }

  @Override
  public void destroy() {
    registryMap.forEach((k, v) -> v.getValue().close());
  }
}
