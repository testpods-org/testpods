package org.testpods.core;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class PropertyContext {
  private final Map<String, Supplier<String>> properties = new ConcurrentHashMap<>();

  // Properties are suppliers - resolved lazily after pods start
  public void publish(String key, Supplier<String> valueSupplier) {}

  public Supplier<String> get(String key) {
    return null;
  }

  public String resolve(String key) // Fails if pod not started
      {
    return null;
  }

  public Map<String, String> resolveAll(String... keys) {
    return null;
  }

  // Convenience for ${property.name} interpolation
  public String interpolate(String template) {
    return null;
  }
}
