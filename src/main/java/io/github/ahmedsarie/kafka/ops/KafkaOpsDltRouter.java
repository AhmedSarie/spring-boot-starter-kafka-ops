package io.github.ahmedsarie.kafka.ops;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.event.ListenerContainerIdleEvent;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ConsumerAwareRebalanceListener;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.util.backoff.FixedBackOff;

@Slf4j
@RequiredArgsConstructor
class KafkaOpsDltRouter implements InitializingBean, DisposableBean {

  private static final String RETRY_COUNT_HEADER = "kafka-ops-retry-count";

  private final ListableBeanFactory beanFactory;
  private final KafkaOpsProperties kafkaOpsProperties;
  private final Map<String, RouteState> routes = new ConcurrentHashMap<>();
  private KafkaTemplate<byte[], byte[]> kafkaTemplate;

  void setKafkaTemplate(KafkaTemplate<byte[], byte[]> template) {
    this.kafkaTemplate = template;
  }

  void setForceForTesting(String mainTopic, boolean force) {
    var state = routes.get(mainTopic);
    if (state != null) {
      state.setForce(force);
    }
  }

  @Override
  public void afterPropertiesSet() {
    for (var consumer : KafkaOpsFactoryUtils.getConsumerBeans(beanFactory)) {
      var mainTopicName = consumer.getTopic().getName();
      var dltTopicName = consumer.getTopic().getDltTopic();
      var retryTopicName = consumer.getTopic().getRetryTopic();

      if (dltTopicName == null && retryTopicName == null) {
        continue;
      }
      if (dltTopicName == null || retryTopicName == null) {
        log.warn("Consumer for topic={} declares only {} — both withDlt() and withRetry() required for DLT routing",
            mainTopicName, dltTopicName != null ? "DLT" : "retry");
        continue;
      }

      if (kafkaTemplate == null) {
        kafkaTemplate = KafkaOpsFactoryUtils.createByteArrayKafkaTemplate(consumer, beanFactory);
      }

      var consumerFactory = createConsumerFactory(consumer, kafkaOpsProperties.getGroupId() + "-dlt-router");
      var container = buildContainer(consumerFactory, dltTopicName, retryTopicName, mainTopicName, null);

      routes.put(mainTopicName, new RouteState(container, retryTopicName, consumer));
      log.info("Registered DLT router: {} -> {} -> {}", dltTopicName, retryTopicName, mainTopicName);
    }
  }

  void start(String mainTopic) {
    var state = getRouteOrThrow(mainTopic);
    state.setWasEverStarted(true);
    if (!state.getContainer().isRunning()) {
      state.setForce(false);
      state.getContainer().start();
      log.info("Started DLT router for topic={}", mainTopic);
    } else {
      log.info("DLT router for topic={} is already running", mainTopic);
    }
  }

  void startFromTimestamp(String mainTopic, long timestamp, boolean force) {
    var state = getRouteOrThrow(mainTopic);

    if (state.getContainer().isRunning()) {
      state.getContainer().stop();
    }

    var groupId = kafkaOpsProperties.getGroupId() + "-dlt-router-" + timestamp;
    var consumerFactory = createConsumerFactory(state.getConsumer(), groupId);
    var listener = buildTimestampSeekListener(mainTopic, timestamp);
    var newContainer = buildContainer(consumerFactory,
        state.getConsumer().getTopic().getDltTopic(), state.getRetryTopic(), mainTopic, listener);

    state.setContainer(newContainer);
    state.setForce(force);
    state.setWasEverStarted(true);
    newContainer.start();
    log.info("Started DLT router for topic={} from timestamp={}, force={}", mainTopic, timestamp, force);
  }

  @Scheduled(cron = "${kafka.ops.dlt-routing.restart-cron:0 */30 * * * *}")
  void scheduledRestart() {
    routes.forEach((mainTopic, state) -> {
      if (state.isWasEverStarted() && !state.getContainer().isRunning()) {
        state.getContainer().start();
        log.info("Scheduled restart of DLT router for topic={}", mainTopic);
      }
    });
  }

  @EventListener
  void onIdleEvent(ListenerContainerIdleEvent event) {
    var idleContainer = event.getContainer(ConcurrentMessageListenerContainer.class);
    if (idleContainer == null) {
      return;
    }

    for (var entry : routes.entrySet()) {
      var mainTopic = entry.getKey();
      var state = entry.getValue();

      if (state.getContainer() == idleContainer && state.getContainer().isRunning()) {
        state.getContainer().stop();
        log.info("Stopped idle DLT router for topic={}", mainTopic);
        break;
      }
    }
  }

  @Override
  public void destroy() {
    routes.values().forEach(state -> {
      if (state.getContainer().isRunning()) {
        state.getContainer().stop();
      }
    });
  }

  // --- Container creation ---

  private ConcurrentMessageListenerContainer<byte[], byte[]> buildContainer(
      DefaultKafkaConsumerFactory<byte[], byte[]> consumerFactory,
      String dltTopicName, String retryTopicName, String mainTopicName,
      ConsumerAwareRebalanceListener rebalanceListener
  ) {
    var containerProps = new ContainerProperties(dltTopicName);
    containerProps.setAckMode(ContainerProperties.AckMode.RECORD);
    containerProps.setIdleEventInterval(
        kafkaOpsProperties.getDltRouting().getIdleShutdownMinutes() * 60_000L);
    containerProps.setMessageListener((MessageListener<byte[], byte[]>) record ->
        routeRecord(record, retryTopicName, mainTopicName));
    if (rebalanceListener != null) {
      containerProps.setConsumerRebalanceListener(rebalanceListener);
    }

    var container = new ConcurrentMessageListenerContainer<>(consumerFactory, containerProps);
    container.setAutoStartup(false);
    container.setConcurrency(1);
    container.setCommonErrorHandler(new DefaultErrorHandler(new FixedBackOff(1000L, 3L)));
    return container;
  }

  private DefaultKafkaConsumerFactory<byte[], byte[]> createConsumerFactory(
      KafkaOpsAwareConsumer consumer, String groupId) {
    var props = KafkaOpsFactoryUtils.extractConnectionProps(consumer, beanFactory);
    props.put("key.deserializer", ByteArrayDeserializer.class);
    props.put("value.deserializer", ByteArrayDeserializer.class);
    props.put("auto.offset.reset", "earliest");
    props.put("group.id", groupId);
    return new DefaultKafkaConsumerFactory<>(props);
  }

  private ConsumerAwareRebalanceListener buildTimestampSeekListener(String mainTopic, long timestamp) {
    return new ConsumerAwareRebalanceListener() {
      @Override
      public void onPartitionsAssigned(
          org.apache.kafka.clients.consumer.Consumer<?, ?> kafkaConsumer,
          Collection<TopicPartition> partitions
      ) {
        var timestampsToSearch = new HashMap<TopicPartition, Long>();
        partitions.forEach(tp -> timestampsToSearch.put(tp, timestamp));

        kafkaConsumer.offsetsForTimes(timestampsToSearch).forEach((tp, offsetAndTimestamp) -> {
          if (offsetAndTimestamp != null) {
            kafkaConsumer.seek(tp, offsetAndTimestamp.offset());
          } else {
            var endOffsets = kafkaConsumer.endOffsets(List.of(tp));
            kafkaConsumer.seek(tp, endOffsets.getOrDefault(tp, 0L));
          }
        });
        log.info("Seeked DLT router for topic={} to timestamp={}", mainTopic, timestamp);
      }
    };
  }

  // --- Record routing ---

  void routeRecord(ConsumerRecord<byte[], byte[]> record, String retryTopic, String mainTopic) {
    var state = routes.get(mainTopic);
    var maxRetryCount = kafkaOpsProperties.getDltRouting().getMaxRetryCount();
    var currentRetryCount = readRetryCount(record);

    if (!state.isForce() && currentRetryCount >= maxRetryCount) {
      log.error("Max retry count ({}) exceeded for topic={}, partition={}, offset={}. Skipping.",
          maxRetryCount, record.topic(), record.partition(), record.offset());
      return;
    }

    var newRetryCount = state.isForce() ? 0 : currentRetryCount + 1;
    record.headers().remove(RETRY_COUNT_HEADER);
    record.headers().add(RETRY_COUNT_HEADER, String.valueOf(newRetryCount).getBytes(StandardCharsets.UTF_8));

    try {
      kafkaTemplate.send(new ProducerRecord<>(retryTopic, null, record.key(), record.value(), record.headers())).get();
      log.info("Routed DLT record topic={}, partition={}, offset={} -> {} (retryCount={})",
          record.topic(), record.partition(), record.offset(), retryTopic, newRetryCount);
    } catch (Exception e) {
      log.error("Failed to route DLT record topic={}, partition={}, offset={} -> {}",
          record.topic(), record.partition(), record.offset(), retryTopic, e);
      throw new RuntimeException("Failed to route record to retry topic", e);
    }
  }

  private int readRetryCount(ConsumerRecord<byte[], byte[]> record) {
    Header header = record.headers().lastHeader(RETRY_COUNT_HEADER);
    if (header == null || header.value() == null) {
      return 0;
    }
    try {
      return Integer.parseInt(new String(header.value(), StandardCharsets.UTF_8));
    } catch (NumberFormatException e) {
      log.warn("Invalid retry count header value, defaulting to 0");
      return 0;
    }
  }

  // --- Helpers ---

  private RouteState getRouteOrThrow(String mainTopic) {
    var state = routes.get(mainTopic);
    if (state == null) {
      throw new KafkaOpsService.NoConsumerFoundException("No DLT router configured for topic=" + mainTopic);
    }
    return state;
  }

  // --- Route state ---

  @Getter
  private static class RouteState {
    private ConcurrentMessageListenerContainer<byte[], byte[]> container;
    private final String retryTopic;
    private final KafkaOpsAwareConsumer consumer;
    private volatile boolean force;
    private volatile boolean wasEverStarted;

    RouteState(ConcurrentMessageListenerContainer<byte[], byte[]> container,
               String retryTopic, KafkaOpsAwareConsumer consumer) {
      this.container = container;
      this.retryTopic = retryTopic;
      this.consumer = consumer;
    }

    void setContainer(ConcurrentMessageListenerContainer<byte[], byte[]> container) {
      this.container = container;
    }

    void setForce(boolean force) {
      this.force = force;
    }

    void setWasEverStarted(boolean wasEverStarted) {
      this.wasEverStarted = wasEverStarted;
    }
  }
}
