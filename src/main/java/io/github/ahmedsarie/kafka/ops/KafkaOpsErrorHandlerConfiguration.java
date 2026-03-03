package io.github.ahmedsarie.kafka.ops;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.ExponentialBackOff;
import org.springframework.util.backoff.FixedBackOff;

@Slf4j
@Configuration
@RequiredArgsConstructor
@AutoConfigureAfter(KafkaAutoConfiguration.class)
@ConditionalOnClass(ConcurrentKafkaListenerContainerFactory.class)
class KafkaOpsErrorHandlerConfiguration {

  private final ListableBeanFactory beanFactory;

  @Bean
  @ConditionalOnMissingBean(DefaultErrorHandler.class)
  DefaultErrorHandler kafkaOpsDefaultErrorHandler() {
    var consumers = KafkaOpsFactoryUtils.getConsumerBeans(beanFactory);

    if (!needsErrorHandler(consumers)) {
      log.info("No KafkaOpsAwareConsumer with retry config or DLT/retry topics, skipping DefaultErrorHandler");
      return null;
    }

    var topicRetryMap = buildTopicRetryMap(consumers);
    var needsRecoverer = consumers.stream().anyMatch(c ->
        c.getDltTopic() != null || c.getRetryTopic() != null);

    DefaultErrorHandler errorHandler;
    if (needsRecoverer) {
      var template = KafkaOpsFactoryUtils.createByteArrayKafkaTemplate(
          consumers.iterator().next(), beanFactory);
      var recoverer = new DeadLetterPublishingRecoverer(template, buildDestinationResolver(consumers));
      errorHandler = new DefaultErrorHandler(recoverer, new FixedBackOff(0L, 0L));
    } else {
      errorHandler = new DefaultErrorHandler(new FixedBackOff(0L, 0L));
    }

    configureBackOffFunction(errorHandler, topicRetryMap);
    log.info("Auto-configured DefaultErrorHandler with per-topic backoff for {} topic(s)", topicRetryMap.size());
    return errorHandler;
  }

  private boolean needsErrorHandler(Collection<KafkaOpsAwareConsumer> consumers) {
    return consumers.stream().anyMatch(c ->
        c.getTopic().getRetryConfig() != null
            || c.getDltTopic() != null
            || c.getRetryTopic() != null);
  }

  private Map<String, RetryConfig> buildTopicRetryMap(Collection<KafkaOpsAwareConsumer> consumers) {
    var map = new HashMap<String, RetryConfig>();
    for (var consumer : consumers) {
      if (consumer.getTopic().getRetryConfig() != null) {
        map.put(consumer.getTopic().getName(), consumer.getTopic().getRetryConfig());
      }
      if (consumer.getRetryTopic() != null && consumer.getRetryTopic().getRetryConfig() != null) {
        map.put(consumer.getRetryTopic().getName(), consumer.getRetryTopic().getRetryConfig());
      }
    }
    return map;
  }

  private void configureBackOffFunction(DefaultErrorHandler errorHandler, Map<String, RetryConfig> topicRetryMap) {
    errorHandler.setBackOffFunction((record, exception) -> {
      if (record == null) {
        return null;
      }
      var retryConfig = topicRetryMap.get(record.topic());
      return retryConfig != null ? toBackOff(retryConfig) : null;
    });
  }

  private BackOff toBackOff(RetryConfig retryConfig) {
    if (retryConfig.isExponential()) {
      var backOff = new ExponentialBackOff(retryConfig.getIntervalMs(), retryConfig.getMultiplier());
      backOff.setMaxAttempts(retryConfig.getMaxAttempts());
      return backOff;
    }
    return new FixedBackOff(retryConfig.getIntervalMs(), retryConfig.getMaxAttempts());
  }

  BiFunction<ConsumerRecord<?, ?>, Exception, TopicPartition> buildDestinationResolver(
      Collection<KafkaOpsAwareConsumer> consumers) {
    var mainToRetry = new HashMap<String, String>();
    var retryToDlt = new HashMap<String, String>();
    var mainToDlt = new HashMap<String, String>();

    for (var consumer : consumers) {
      var mainName = consumer.getTopic().getName();
      var hasRetry = consumer.getRetryTopic() != null;
      var hasDlt = consumer.getDltTopic() != null;

      if (hasRetry) {
        mainToRetry.put(mainName, consumer.getRetryTopic().getName());
      }
      if (hasRetry && hasDlt) {
        retryToDlt.put(consumer.getRetryTopic().getName(), consumer.getDltTopic().getName());
      }
      if (!hasRetry && hasDlt) {
        mainToDlt.put(mainName, consumer.getDltTopic().getName());
      }
    }

    return (record, exception) -> {
      var source = record.topic();

      var retryTarget = mainToRetry.get(source);
      if (retryTarget != null) {
        log.info("Routing failed record from {} to retry topic {}", source, retryTarget);
        return new TopicPartition(retryTarget, -1);
      }

      var dltFromRetry = retryToDlt.get(source);
      if (dltFromRetry != null) {
        log.info("Routing failed record from retry {} to DLT {}", source, dltFromRetry);
        return new TopicPartition(dltFromRetry, -1);
      }

      var dltFromMain = mainToDlt.get(source);
      if (dltFromMain != null) {
        log.info("Routing failed record from {} to DLT {}", source, dltFromMain);
        return new TopicPartition(dltFromMain, -1);
      }

      log.warn("No DLT/retry mapping for topic {}, using Spring Kafka default", source);
      return null;
    };
  }
}
