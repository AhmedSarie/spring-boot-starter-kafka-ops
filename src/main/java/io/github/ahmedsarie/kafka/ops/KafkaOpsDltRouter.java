package io.github.ahmedsarie.kafka.ops;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
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
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.AcknowledgingMessageListener;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.util.backoff.FixedBackOff;

@Slf4j
@RequiredArgsConstructor
class KafkaOpsDltRouter implements InitializingBean, DisposableBean {

  private static final String CYCLE_COUNT_HEADER = "kafka-ops-dlt-cycle";

  private final ListableBeanFactory beanFactory;
  private final KafkaOpsProperties kafkaOpsProperties;
  private final Map<String, RouteState> routes = new ConcurrentHashMap<>();
  private KafkaTemplate<byte[], byte[]> kafkaTemplate;

  void setKafkaTemplate(KafkaTemplate<byte[], byte[]> template) {
    this.kafkaTemplate = template;
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

      var consumerFactory = createConsumerFactory(consumer);
      var container = buildContainer(consumerFactory, dltTopicName, retryTopicName, mainTopicName);

      routes.put(mainTopicName, new RouteState(container, retryTopicName, consumer));
      log.info("Registered DLT router: {} -> {} -> {}", dltTopicName, retryTopicName, mainTopicName);
    }
  }

  void start(String mainTopic) {
    var state = getRouteOrThrow(mainTopic);
    state.setCutoffTimestamp(System.currentTimeMillis());

    if (!state.getContainer().isRunning()) {
      state.getContainer().start();
      log.info("Started DLT router for topic={}", mainTopic);
    } else {
      log.info("DLT router for topic={} is already running", mainTopic);
    }
  }

  @Scheduled(cron = "${kafka.ops.dlt-routing.restart-cron:0 */30 * * * *}")
  void scheduledRestart() {
    routes.forEach((mainTopic, state) -> {
      if (!state.getContainer().isRunning()) {
        state.setCutoffTimestamp(System.currentTimeMillis());
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
      String dltTopicName, String retryTopicName, String mainTopicName
  ) {
    var containerProps = new ContainerProperties(dltTopicName);
    containerProps.setGroupId(kafkaOpsProperties.getGroupId() + "-dlt-router");
    containerProps.setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
    containerProps.setIdleEventInterval(
        kafkaOpsProperties.getDltRouting().getIdleShutdownMinutes() * 60_000L);
    containerProps.setMessageListener((AcknowledgingMessageListener<byte[], byte[]>) (record, ack) ->
        routeRecord(record, retryTopicName, mainTopicName, ack));

    var container = new ConcurrentMessageListenerContainer<>(consumerFactory, containerProps);
    container.setAutoStartup(false);
    container.setConcurrency(1);
    container.setCommonErrorHandler(new DefaultErrorHandler(new FixedBackOff(1000L, 3L)));
    return container;
  }

  private DefaultKafkaConsumerFactory<byte[], byte[]> createConsumerFactory(KafkaOpsAwareConsumer consumer) {
    var props = KafkaOpsFactoryUtils.extractConnectionProps(consumer, beanFactory);
    props.put("key.deserializer", ByteArrayDeserializer.class);
    props.put("value.deserializer", ByteArrayDeserializer.class);
    props.put("auto.offset.reset", "earliest");
    return new DefaultKafkaConsumerFactory<>(props);
  }

  // --- Record routing ---

  void routeRecord(ConsumerRecord<byte[], byte[]> record, String retryTopic, String mainTopic,
                   Acknowledgment ack) {
    var state = routes.get(mainTopic);

    if (record.timestamp() > state.getCutoffTimestamp()) {
      log.info("DLT record beyond cutoff: topic={}, partition={}, offset={}, recordTimestamp={}, cutoff={}. Stopping router.",
          record.topic(), record.partition(), record.offset(), record.timestamp(), state.getCutoffTimestamp());
      state.getContainer().stop();
      return;
    }

    var maxCycles = kafkaOpsProperties.getDltRouting().getMaxCycles();
    var currentCycle = readCycleCount(record);
    if (currentCycle >= maxCycles) {
      log.warn("Max DLT cycles ({}) reached for topic={}, partition={}, offset={}. Skipping.",
          maxCycles, record.topic(), record.partition(), record.offset());
      ack.acknowledge();
      return;
    }

    record.headers().remove(CYCLE_COUNT_HEADER);
    record.headers().add(CYCLE_COUNT_HEADER,
        String.valueOf(currentCycle + 1).getBytes(StandardCharsets.UTF_8));

    try {
      kafkaTemplate.send(new ProducerRecord<>(retryTopic, null, record.key(), record.value(), record.headers())).get();
      ack.acknowledge();
      log.info("Routed DLT record topic={}, partition={}, offset={} -> {} (cycle={})",
          record.topic(), record.partition(), record.offset(), retryTopic, currentCycle + 1);
    } catch (Exception e) {
      log.error("Failed to route DLT record topic={}, partition={}, offset={} -> {}",
          record.topic(), record.partition(), record.offset(), retryTopic, e);
      throw new RuntimeException("Failed to route record to retry topic", e);
    }
  }

  private int readCycleCount(ConsumerRecord<byte[], byte[]> record) {
    Header header = record.headers().lastHeader(CYCLE_COUNT_HEADER);
    if (header == null || header.value() == null) {
      return 0;
    }
    try {
      return Integer.parseInt(new String(header.value(), StandardCharsets.UTF_8));
    } catch (NumberFormatException e) {
      log.warn("Invalid cycle count header value, defaulting to 0");
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
    private final ConcurrentMessageListenerContainer<byte[], byte[]> container;
    private final String retryTopic;
    private final KafkaOpsAwareConsumer consumer;
    private volatile long cutoffTimestamp;

    RouteState(ConcurrentMessageListenerContainer<byte[], byte[]> container,
               String retryTopic, KafkaOpsAwareConsumer consumer) {
      this.container = container;
      this.retryTopic = retryTopic;
      this.consumer = consumer;
    }

    void setCutoffTimestamp(long cutoffTimestamp) {
      this.cutoffTimestamp = cutoffTimestamp;
    }
  }
}
