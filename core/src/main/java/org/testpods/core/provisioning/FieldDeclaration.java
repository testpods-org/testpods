package org.testpods.core.provisioning;

import org.testpods.junit.TestPod;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Represents a static field annotated with {@link TestPod} that has no initializer
 * (declaration-only).
 */
public record FieldDeclaration(
    Field field,
    String fieldName,
    Class<?> fieldType,
    TestPod annotation,
    Class<?> declaringClass,
    boolean isPrivate
) {

  public String podName() {
    if (!annotation.podName().isEmpty()) {
      return annotation.podName();
    }
    if (!annotation.value().isEmpty()) {
      return annotation.value();
    }
    return fieldName;
  }

  public int modifiers() {
    return field.getModifiers();
  }

  public boolean isFinal() {
    return Modifier.isFinal(field.getModifiers());
  }
}
