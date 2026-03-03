package io.github.ahmedsarie.kafka.ops;

import java.util.Objects;

public class TopicConfig {

  private final String name;

  private TopicConfig(String name) {
    this.name = name;
  }

  public static TopicConfig of(String name) {
    return new TopicConfig(name);
  }

  public String getName() {
    return name;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    return Objects.equals(name, ((TopicConfig) o).name);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name);
  }

  @Override
  public String toString() {
    return "TopicConfig{name='" + name + "'}";
  }
}
