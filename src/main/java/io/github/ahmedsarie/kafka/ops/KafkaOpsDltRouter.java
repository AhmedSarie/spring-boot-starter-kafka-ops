package io.github.ahmedsarie.kafka.ops;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.Getter;
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
import org.springframework.util.backoff.FixedBackOff;

@Slf4j
class KafkaOpsDltRouter implements InitializingBean, DisposableBean {

  private static final String RETRY_COUNT_HEADER = "kafka-ops-retry-count";

  private final ListableBeanFactory beanFactory;
  private final KafkaOpsProperties kafkaOpsProperties;
  private final Map<String, RouteState> routes = new ConcurrentHashMap<>();
  private final ScheduledExecutorService scheduler;
  private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
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

  KafkaOpsDltRouter(ListableBeanFactory beanFactory, KafkaOpsProperties kafkaOpsProperties) {
    this.beanFactory = beanFactory;
    this.kafkaOpsProperties = kafkaOpsProperties;
    this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
      var thread = new Thread(r, "dlt-router-scheduler");
      thread.setDaemon(true);
      return thread;
    });
  }

  @Override
  public void afterPropertiesSet() {
    for (var consumer : KafkaOpsFactoryUtils.getConsumerBeans(beanFactory)) {
      var mainTopicName = consumer.getTopic().getName();

      if (consumer.getDltTopic() == null && consumer.getRetryTopic() == null) {
        continue;
      }
      if (consumer.getDltTopic() == null || consumer.getRetryTopic() == null) {
        log.warn("Consumer for topic={} declares only {} — both getDltTopic() and getRetryTopic() required",
            mainTopicName, consumer.getDltTopic() != null ? "DLT" : "retry");
        continue;
      }

      if (kafkaTemplate == null) {
        kafkaTemplate = KafkaOpsFactoryUtils.createByteArrayKafkaTemplate(consumer, beanFactory);
      }

      var consumerFactory = createConsumerFactory(consumer, kafkaOpsProperties.getGroupId() + "-dlt-router");
      var container = buildContainer(consumerFactory,
          consumer.getDltTopic().getName(), consumer.getRetryTopic().getName(), mainTopicName, null);

      routes.put(mainTopicName, new RouteState(container, consumer.getRetryTopic().getName(), consumer));
      log.info("Registered DLT router: {} -> {} -> {}",
          consumer.getDltTopic().getName(), consumer.getRetryTopic().getName(), mainTopicName);
    }
  }

  void start(String mainTopic) {
    var state = getRouteOrThrow(mainTopic);
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
        state.getConsumer().getDltTopic().getName(), state.getRetryTopic(), mainTopic, listener);

    state.setContainer(newContainer);
    state.setForce(force);
    newContainer.start();
    log.info("Started DLT router for topic={} from timestamp={}, force={}", mainTopic, timestamp, force);
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
        scheduleRestart(mainTopic, state);
        break;
      }
    }
  }

  @Override
  public void destroy() {
    shuttingDown.set(true);
    routes.values().forEach(state -> {
      if (state.getScheduledRestart() != null) {
        state.getScheduledRestart().cancel(false);
      }
      if (state.getContainer().isRunning()) {
        state.getContainer().stop();
      }
    });
    scheduler.shutdownNow();
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

  private void scheduleRestart(String mainTopic, RouteState state) {
    var interval = kafkaOpsProperties.getDltRouting().getRestartIntervalMinutes();
    if (interval > 0 && !shuttingDown.get()) {
      var future = scheduler.schedule(() -> {
        if (!shuttingDown.get() && !state.getContainer().isRunning()) {
          state.getContainer().start();
          log.info("Restarted DLT router for topic={} after {} minute(s)", mainTopic, interval);
        }
      }, interval, TimeUnit.MINUTES);
      state.setScheduledRestart(future);
    }
  }

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
    private volatile ScheduledFuture<?> scheduledRestart;

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

    void setScheduledRestart(ScheduledFuture<?> future) {
      this.scheduledRestart = future;
    }
  }
}
