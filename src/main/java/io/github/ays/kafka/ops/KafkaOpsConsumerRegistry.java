package io.github.ays.kafka.ops;

import io.github.ays.kafka.ops.KafkaOpsService.NoConsumerFoundException;
import java.lang.reflect.InvocationTargetException;
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
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.core.ResolvableType;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;

@Slf4j
@RequiredArgsConstructor
class KafkaOpsConsumerRegistry implements InitializingBean, DisposableBean {

  private static final String DEF_FACTORY_BEAN_NAME = "kafkaListenerContainerFactory";
  private final Map<String, RegisteredConsumerEntry> registryMap = new ConcurrentHashMap<>();
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
      var entry = registryMap.get(mainTopicName);
      var keyFormat = entry != null ? codecFormat(entry.getKeyCodec()) : null;
      var valueFormat = entry != null ? codecFormat(entry.getValueCodec()) : null;
      var mainInfo = buildTopicInfo(mainTopicName, keyFormat, valueFormat);

      if (consumer.getTopic().getDltTopic() != null) {
        var dltInfo = buildTopicInfo(consumer.getTopic().getDltTopic(), null, null);
        mainInfo.setDlt(dltInfo);
      }
      if (consumer.getTopic().getRetryTopic() != null) {
        var retryInfo = buildTopicInfo(consumer.getTopic().getRetryTopic(), null, null);
        mainInfo.setRetry(retryInfo);
      }

      details.add(mainInfo);
    }
    return details;
  }

  private KafkaOpsConsumerInfo buildTopicInfo(String topicName, String keyFormat, String valueFormat) {
    var entry = registryMap.get(topicName);
    if (entry == null) {
      return new KafkaOpsConsumerInfo(topicName, -1, -1);
    }
    var kafkaConsumer = entry.getKafkaConsumer();
    synchronized (kafkaConsumer) {
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
        return new KafkaOpsConsumerInfo(topicName, partitionCount, messageCount, keyFormat, valueFormat);
      } catch (Exception e) {
        log.warn("Failed to get consumer details for topic={}", topicName, e);
        return new KafkaOpsConsumerInfo(topicName, -1, -1);
      }
    }
  }

  private static String codecFormat(MessageCodec codec) {
    if (codec instanceof AvroMessageCodec) return "avro";
    if (codec instanceof ProtoMessageCodec) return "proto";
    if (codec instanceof JsonMessageCodec) return "json";
    if (codec instanceof StringMessageCodec) return "string";
    return "custom";
  }

  RegisteredConsumerEntry find(String topic) {
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

      var keyCodec = resolveKeyCodec(consumer);
      var valueCodec = resolveValueCodec(consumer);

      var mainTopicName = consumer.getTopic().getName();
      var mainKafkaConsumer = consumerFactory.apply(props);
      registryMap.put(mainTopicName, new RegisteredConsumerEntry(consumer, mainKafkaConsumer, keyCodec, valueCodec));

      if (consumer.getTopic().getDltTopic() != null) {
        var dltProps = new HashMap<>(props);
        var dltKafkaConsumer = consumerFactory.apply(dltProps);
        registryMap.put(consumer.getTopic().getDltTopic(), new RegisteredConsumerEntry(consumer, dltKafkaConsumer, keyCodec, valueCodec));
      }

      if (consumer.getTopic().getRetryTopic() != null) {
        var retryProps = new HashMap<>(props);
        var retryKafkaConsumer = consumerFactory.apply(retryProps);
        registryMap.put(consumer.getTopic().getRetryTopic(), new RegisteredConsumerEntry(consumer, retryKafkaConsumer, keyCodec, valueCodec));
      }
    });
  }

  @SuppressWarnings("unchecked")
  private MessageCodec resolveKeyCodec(KafkaOpsAwareConsumer consumer) {
    var declared = consumer.getKeyCodec();
    if (declared != null) {
      return declared;
    }
    var keyType = resolveGenericType(consumer, 0);
    return resolveCodecForType(keyType, consumer, "key");
  }

  @SuppressWarnings("unchecked")
  private MessageCodec resolveValueCodec(KafkaOpsAwareConsumer consumer) {
    var declared = consumer.getValueCodec();
    if (declared != null) {
      return declared;
    }
    var valueType = resolveGenericType(consumer, 1);
    return resolveCodecForType(valueType, consumer, "value");
  }

  @SuppressWarnings("unchecked")
  private MessageCodec resolveCodecForType(Class<?> type, KafkaOpsAwareConsumer consumer, String label) {
    if (type == null) {
      log.debug("Could not resolve {} type for consumer={}, falling back to StringMessageCodec",
          label, consumer.getClass().getSimpleName());
      return new StringMessageCodec();
    }
    if (type == String.class) {
      log.debug("Auto-resolved String {} codec for consumer={}", label, consumer.getClass().getSimpleName());
      return new StringMessageCodec();
    }
    if (SpecificRecord.class.isAssignableFrom(type)) {
      log.debug("Auto-resolved Avro {} codec for consumer={}, type={}", label, consumer.getClass().getSimpleName(), type.getSimpleName());
      return createAvroCodec(type);
    }
    if (com.google.protobuf.Message.class.isAssignableFrom(type)) {
      log.debug("Auto-resolved Proto {} codec for consumer={}, type={}", label, consumer.getClass().getSimpleName(), type.getSimpleName());
      return createProtoCodec(type);
    }
    log.debug("Auto-resolved JSON {} codec for consumer={}, type={}", label, consumer.getClass().getSimpleName(), type.getSimpleName());
    return new JsonMessageCodec<>(type);
  }

  @SuppressWarnings("unchecked")
  private AvroMessageCodec createAvroCodec(Class<?> type) {
    try {
      var schema = (org.apache.avro.Schema) type.getMethod("getClassSchema").invoke(null);
      return new AvroMessageCodec<>(schema);
    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
      throw new IllegalStateException("Failed to create AvroMessageCodec for " + type.getName()
          + ". Ensure the class has a static getClassSchema() method.", e);
    }
  }

  @SuppressWarnings("unchecked")
  private ProtoMessageCodec createProtoCodec(Class<?> type) {
    try {
      var defaultInstance = (com.google.protobuf.Message) type.getMethod("getDefaultInstance").invoke(null);
      return new ProtoMessageCodec<>(defaultInstance);
    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
      throw new IllegalStateException("Failed to create ProtoMessageCodec for " + type.getName()
          + ". Ensure the class has a static getDefaultInstance() method.", e);
    }
  }

  private Class<?> resolveGenericType(KafkaOpsAwareConsumer consumer, int index) {
    var resolvableType = ResolvableType.forClass(consumer.getClass()).as(KafkaOpsAwareConsumer.class);
    var generic = resolvableType.getGeneric(index);
    return generic.resolve();
  }

  @Override
  public void destroy() {
    registryMap.forEach((k, v) -> v.getKafkaConsumer().close());
  }

  private Collection<KafkaOpsAwareConsumer> getConsumerBeans() {
    return beanFactory.getBeansOfType(KafkaOpsAwareConsumer.class).values();
  }
}
