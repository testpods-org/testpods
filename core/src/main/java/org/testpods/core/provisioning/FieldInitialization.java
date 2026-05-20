package org.testpods.core.provisioning;

import org.testpods.junit.TestPod;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Represents a static field annotated with {@link TestPod} that has been initialized
 * (has a non-null value at scan time).
 */
public record FieldInitialization(
    Field field,
    String fieldName,
    Class<?> fieldType,
    TestPod annotation,
    Class<?> declaringClass,
    boolean isPrivate,
    Object instance
) {

  /**
   * The name specified in the annotation, or the field name if not specified.
   * @return
   */
  public String podName() {
    if (!annotation.podName().isEmpty()) {
      return annotation.podName();
    }
    if (!annotation.value().isEmpty()) {
      return annotation.value();
    }
    return fieldName;
  }

  public String podNamePrefixedWithClassName() {
    return declaringClass.getSimpleName() + "#" + podName();
  }

  public int modifiers() {
    return field.getModifiers();
  }

  public boolean isFinal() {
    return Modifier.isFinal(field.getModifiers());
  }

  public <T> T instanceAs(Class<T> type) {
    return type.cast(instance);
  }

  @SuppressWarnings("unchecked")
  public <T> T typedInstance() {
    return (T) fieldType.cast(instance);
  }
}
