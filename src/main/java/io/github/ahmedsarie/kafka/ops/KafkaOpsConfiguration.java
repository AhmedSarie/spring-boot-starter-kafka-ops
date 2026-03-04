package io.github.ahmedsarie.kafka.ops;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.util.ObjectUtils;

@Slf4j
@AutoConfiguration
@RequiredArgsConstructor
@EnableConfigurationProperties(KafkaOpsProperties.class)
@ComponentScan(basePackageClasses = {KafkaOpsController.class})
@ConditionalOnClass(ConcurrentKafkaListenerContainerFactory.class)
@AutoConfigureAfter(KafkaAutoConfiguration.class)
public class KafkaOpsConfiguration {

  private static final String DEFAULT_GROUP_ID = "kafka-ops-group-id";
  private final KafkaOpsProperties kafkaOpsProperties;

  @Bean
  @ConditionalOnMissingBean
  public KafkaOpsConsumerRegistry kafkaOpsConsumerRegistry(ListableBeanFactory listableBeanFactory) {
    var groupId = ObjectUtils.isEmpty(kafkaOpsProperties.getGroupId())
        ? DEFAULT_GROUP_ID
        : kafkaOpsProperties.getGroupId();
    return new KafkaOpsConsumerRegistry(listableBeanFactory, groupId, KafkaConsumer::new);
  }

  @Bean
  @ConditionalOnMissingBean
  public KafkaOpsService kafkaOpsService(KafkaOpsConsumerRegistry registry) {
    var manualKafkaConsumer = new ManualKafkaConsumer(Duration.ofMillis(kafkaOpsProperties.getMaxPollIntervalMs()));
    var batchMaxLimit = kafkaOpsProperties.getBatch().getMaxLimit();
    return new KafkaOpsService(registry, manualKafkaConsumer, batchMaxLimit);
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(value = "kafka.ops.dlt-routing.enabled", havingValue = "true")
  KafkaOpsDltRouter kafkaOpsDltRouter(ListableBeanFactory beanFactory,
                                      ApplicationEventPublisher eventPublisher) {
    return new KafkaOpsDltRouter(beanFactory, kafkaOpsProperties, eventPublisher);
  }

  @Configuration
  @EnableScheduling
  @ConditionalOnProperty(value = "kafka.ops.dlt-routing.enabled", havingValue = "true")
  static class DltSchedulingConfiguration {
  }
}
