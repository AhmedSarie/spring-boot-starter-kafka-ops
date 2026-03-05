package io.github.ahmedsarie.kafka.ops;

import io.github.ahmedsarie.kafka.ops.KafkaOpsService.NoConsumerFoundException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
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
  private static final String METADATA_GROUP_ID_SUFFIX = "-metadata";
  private final Map<String, Map.Entry<KafkaOpsAwareConsumer, KafkaConsumer>> registryMap = new ConcurrentHashMap<>();
  private final Map<String, KafkaConsumer> metadataConsumers = new ConcurrentHashMap<>();
  private final ListableBeanFactory beanFactory;
  private final String groupId;
  private final Function<Map<String, Object>, KafkaConsumer> consumerFactory;

  Set<String> getRegisteredTopics() {
    return Set.copyOf(registryMap.keySet());
  }

  List<KafkaOpsConsumerInfo> getConsumerDetails() {
    var details = new ArrayList<KafkaOpsConsumerInfo>();
    var consumers = getConsumerBeans();
    for (var consumer : consumers) {
      var mainTopicName = consumer.getTopic().getName();
      var mainInfo = buildTopicInfo(mainTopicName);

      if (consumer.getTopic().getDltTopic() != null) {
        var dltInfo = buildTopicInfo(consumer.getTopic().getDltTopic());
        mainInfo.setDlt(dltInfo);
      }
      if (consumer.getTopic().getRetryTopic() != null) {
        var retryInfo = buildTopicInfo(consumer.getTopic().getRetryTopic());
        mainInfo.setRetry(retryInfo);
      }

      details.add(mainInfo);
    }
    return details;
  }

  private KafkaOpsConsumerInfo buildTopicInfo(String topicName) {
    var metadataConsumer = metadataConsumers.get(topicName);
    if (metadataConsumer == null) {
      return new KafkaOpsConsumerInfo(topicName, -1, -1);
    }
    try {
      var partitionInfos = metadataConsumer.partitionsFor(topicName);
      var partitionCount = partitionInfos != null ? partitionInfos.size() : 0;
      var topicPartitions = new ArrayList<TopicPartition>();
      for (int i = 0; i < partitionCount; i++) {
        topicPartitions.add(new TopicPartition(topicName, i));
      }
      long messageCount = 0;
      if (!topicPartitions.isEmpty()) {
        var endOffsets = metadataConsumer.endOffsets(topicPartitions);
        var beginningOffsets = metadataConsumer.beginningOffsets(topicPartitions);
        for (var tp : topicPartitions) {
          var end = (Long) endOffsets.getOrDefault(tp, 0L);
          var begin = (Long) beginningOffsets.getOrDefault(tp, 0L);
          messageCount += (end - begin);
        }
      }
      return new KafkaOpsConsumerInfo(topicName, partitionCount, messageCount);
    } catch (Exception e) {
      log.warn("Failed to get consumer details for topic={}", topicName, e);
      return new KafkaOpsConsumerInfo(topicName, -1, -1);
    }
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
      var containerConfig = consumer.getContainer();
      var consumerContainer = containerConfig != null ? containerConfig.getName() : DEF_FACTORY_BEAN_NAME;
      var factory = (ConcurrentKafkaListenerContainerFactory) beanFactory.getBean(consumerContainer);
      var props = new HashMap<>(factory.getConsumerFactory().getConfigurationProperties());
      props.put("group.id", groupId);
      props.put("max.poll.records", 1);
      props.put("isolation.level", "read_uncommitted");

      var metadataProps = new HashMap<>(props);
      metadataProps.put("group.id", groupId + METADATA_GROUP_ID_SUFFIX);

      var mainTopicName = consumer.getTopic().getName();
      var mainKafkaConsumer = consumerFactory.apply(props);
      registryMap.put(mainTopicName, Map.entry(consumer, mainKafkaConsumer));
      metadataConsumers.put(mainTopicName, consumerFactory.apply(metadataProps));

      if (consumer.getTopic().getDltTopic() != null) {
        var dltProps = new HashMap<>(props);
        var dltKafkaConsumer = consumerFactory.apply(dltProps);
        registryMap.put(consumer.getTopic().getDltTopic(), Map.entry(consumer, dltKafkaConsumer));
        metadataConsumers.put(consumer.getTopic().getDltTopic(), consumerFactory.apply(metadataProps));
      }

      if (consumer.getTopic().getRetryTopic() != null) {
        var retryProps = new HashMap<>(props);
        var retryKafkaConsumer = consumerFactory.apply(retryProps);
        registryMap.put(consumer.getTopic().getRetryTopic(), Map.entry(consumer, retryKafkaConsumer));
        metadataConsumers.put(consumer.getTopic().getRetryTopic(), consumerFactory.apply(metadataProps));
      }
    });
  }

  @Override
  public void destroy() {
    registryMap.forEach((k, v) -> v.getValue().close());
    metadataConsumers.forEach((k, v) -> v.close());
  }

  private Collection<KafkaOpsAwareConsumer> getConsumerBeans() {
    return beanFactory.getBeansOfType(KafkaOpsAwareConsumer.class).values();
  }
}
