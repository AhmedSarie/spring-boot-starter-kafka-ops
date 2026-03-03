package io.github.ahmedsarie.kafka.ops;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

@Getter
@ToString
@EqualsAndHashCode
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class ContainerConfig {

  private final String name;

  public static ContainerConfig of(String name) {
    return new ContainerConfig(name);
  }
}
