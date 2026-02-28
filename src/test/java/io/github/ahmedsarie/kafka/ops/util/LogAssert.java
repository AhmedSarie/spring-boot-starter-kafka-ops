package io.github.ahmedsarie.kafka.ops.util;

import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.springframework.util.CollectionUtils.isEmpty;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.slf4j.LoggerFactory;

public class LogAssert {

  private final Logger logger;
  private final LoggerAppender appender;

  public LogAssert(Class<?> clazz) {
    logger = (Logger) LoggerFactory.getLogger(clazz);
    appender = new LoggerAppender();
  }

  public void start() {
    logger.addAppender(appender);
    appender.start();
  }

  public void reset() {
    appender.reset();
  }

  public void close() {
    appender.close();
    logger.detachAppender(appender);
  }

  public void assertLog(String substring, int timeout) {
    try {
      Awaitility.await().atMost(timeout, SECONDS).until(() -> !isEmpty(findLogs(substring)));
    } catch (Exception e) {
      Assertions.fail(format("%s not found in logs", substring), e);
    }
  }

  private List<ILoggingEvent> findLogs(String substring) {
    return appender.getLoggingEvents()
        .stream()
        .filter(loggingEvent -> loggingEvent.getFormattedMessage().contains(substring))
        .collect(Collectors.toList());
  }

  private class LoggerAppender extends AppenderBase<ILoggingEvent> {

    public final List<ILoggingEvent> loggingEvents = new ArrayList<>();

    public LoggerAppender() {
      setName(logger.getName() + "Appender");
    }

    @Override
    protected void append(ILoggingEvent loggingEvent) {
      loggingEvents.add(loggingEvent);
    }

    public void reset() {
      loggingEvents.clear();
    }

    public void close() {
      loggingEvents.clear();
      stop();
    }

    public List<ILoggingEvent> getLoggingEvents() {
      return loggingEvents;
    }
  }
}
