package org.testpods.core;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PropertyContext {
  private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}]+)}");

  private final Map<String, Supplier<String>> properties = new ConcurrentHashMap<>();

  // Properties are suppliers - resolved lazily after pods start
  public void publish(String key, Supplier<String> valueSupplier) {
    if (key == null || key.isBlank()) {
      throw new IllegalArgumentException("Property key must not be blank");
    }
    if (valueSupplier == null) {
      throw new IllegalArgumentException("Property supplier must not be null");
    }
    properties.put(key, valueSupplier);
  }

  public Supplier<String> get(String key) {
    return properties.get(key);
  }

  /** Resolve a property value, failing if the key was not published or resolves to null. */
  public String resolve(String key) {
    Supplier<String> valueSupplier = get(key);
    if (valueSupplier == null) {
      throw new IllegalArgumentException("No TestPods property has been published for key: " + key);
    }
    String value = valueSupplier.get();
    if (value == null) {
      throw new IllegalStateException("TestPods property resolved to null for key: " + key);
    }
    return value;
  }

  public Map<String, String> resolveAll(String... keys) {
    Map<String, String> resolved = new LinkedHashMap<>();
    for (String key : keys) {
      resolved.put(key, resolve(key));
    }
    return resolved;
  }

  // Convenience for ${property.name} interpolation
  public String interpolate(String template) {
    if (template == null) {
      return null;
    }
    Matcher matcher = PLACEHOLDER.matcher(template);
    StringBuffer resolved = new StringBuffer();
    while (matcher.find()) {
      matcher.appendReplacement(resolved, Matcher.quoteReplacement(resolve(matcher.group(1))));
    }
    matcher.appendTail(resolved);
    return resolved.toString();
  }

  public static Set<String> referencesIn(String template) {
    Set<String> references = new LinkedHashSet<>();
    if (template == null) {
      return references;
    }
    Matcher matcher = PLACEHOLDER.matcher(template);
    while (matcher.find()) {
      references.add(matcher.group(1));
    }
    return references;
  }
}
