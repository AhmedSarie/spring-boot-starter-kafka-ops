package io.github.ahmedsarie.kafka.ops;

import static java.util.Objects.isNull;

import io.github.ahmedsarie.kafka.ops.KafkaOpsService.NoConsumerFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
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

  Set<String> getRegisteredTopics() {
    return Set.copyOf(registryMap.keySet());
  }

  List<KafkaOpsConsumerInfo> getConsumerDetails() {
    var details = new ArrayList<KafkaOpsConsumerInfo>();
    for (var mapEntry : registryMap.entrySet()) {
      var topicName = mapEntry.getKey();
      var kafkaConsumer = mapEntry.getValue().getValue();
      try {
        var partitionInfos = kafkaConsumer.partitionsFor(topicName);
        var partitionCount = partitionInfos != null ? partitionInfos.size() : 0;
        var topicPartitions = new ArrayList<TopicPartition>();
        for (int i = 0; i < partitionCount; i++) {
          topicPartitions.add(new TopicPartition(topicName, i));
        }
        long messageCount = 0;
        if (!topicPartitions.isEmpty()) {
          var endOffsets = kafkaConsumer.endOffsets(topicPartitions);
          var beginningOffsets = kafkaConsumer.beginningOffsets(topicPartitions);
          for (var tp : topicPartitions) {
            var end = (Long) endOffsets.getOrDefault(tp, 0L);
            var begin = (Long) beginningOffsets.getOrDefault(tp, 0L);
            messageCount += (end - begin);
          }
        }
        details.add(new KafkaOpsConsumerInfo(topicName, partitionCount, messageCount));
      } catch (Exception e) {
        log.warn("Failed to get consumer details for topic={}", topicName, e);
        details.add(new KafkaOpsConsumerInfo(topicName, -1, -1));
      }
    }
    return details;
  }

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
