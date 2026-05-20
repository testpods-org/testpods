package org.testpods.core.provisioning;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.testpods.core.cluster.K8sCluster;
import org.testpods.junit.RegisterCluster;
import org.testpods.junit.TestPod;

@Slf4j
public class ReflectionHelper {

    /**
     * Scans the given classes for a static field annotated with {@link RegisterCluster} that is
     * initialized with a {@link K8sCluster} instance. Classes are scanned in order; the first
     * matching field found wins.
     *
     * @param classes the classes to scan (test class, provider classes, etc.)
     * @return the {@link K8sCluster} instance from the first matching field, or {@code null} if none found
     */
    public static K8sCluster scanClassForClusterRegistration(Class<?>... classes) {
        for (Class<?> clazz : classes) {
            for (Field field : clazz.getDeclaredFields()) {
                if (!field.isAnnotationPresent(RegisterCluster.class)) {
                    continue;
                }

                if (!Modifier.isStatic(field.getModifiers())) {
                    log.warn(
                            "Field '{}' in {} is annotated with @RegisterCluster but is not static — skipping",
                            field.getName(),
                            clazz.getSimpleName());
                    continue;
                }

                if (!K8sCluster.class.isAssignableFrom(field.getType())) {
                    log.warn(
                            "Field '{}' in {} is annotated with @RegisterCluster but its type {} does not implement K8sCluster — skipping",
                            field.getName(),
                            clazz.getSimpleName(),
                            field.getType().getSimpleName());
                    continue;
                }

                field.setAccessible(true);
                Object value;
                try {
                    value = field.get(null);
                } catch (IllegalAccessException e) {
                    log.error(
                            "Cannot access @RegisterCluster field '{}' in {}: {}",
                            field.getName(),
                            clazz.getSimpleName(),
                            e.getMessage());
                    continue;
                }

                if (value == null) {
                    log.warn(
                            "@RegisterCluster field '{}' in {} is null — skipping",
                            field.getName(),
                            clazz.getSimpleName());
                    continue;
                }

                log.debug(
                        "Found @RegisterCluster field: {} {} in {}",
                        field.getType().getSimpleName(),
                        field.getName(),
                        clazz.getSimpleName());
                return (K8sCluster) value;
            }
        }

        log.debug("No @RegisterCluster field found in any of the scanned classes");
        return null;
    }

    private record ResolvedTestPodField(Field field, TestPod annotation, Object value) {}

    private static List<ResolvedTestPodField> resolveStaticTestPodFields(Class<?> clazz) {
        List<ResolvedTestPodField> resolved = new ArrayList<>();

        for (Field field : clazz.getDeclaredFields()) {
            if (!field.isAnnotationPresent(TestPod.class)) {
                continue;
            }

            if (!Modifier.isStatic(field.getModifiers())) {
                log.warn(
                        "Field '{}' in {} is annotated with @TestPod but is not static — skipping",
                        field.getName(),
                        clazz.getSimpleName());
                continue;
            }

            field.setAccessible(true);
            Object value;
            try {
                value = field.get(null);
            } catch (IllegalAccessException e) {
                log.error(
                        "Cannot access field '{}' in {}: {}",
                        field.getName(),
                        clazz.getSimpleName(),
                        e.getMessage());
                continue;
            }

            resolved.add(new ResolvedTestPodField(field, field.getAnnotation(TestPod.class), value));
        }

        return resolved;
    }

    /**
     * Scans the given test class for static fields annotated with {@link TestPod} that are
     * declaration-only (no initializer). A field is considered declaration-only if its runtime value
     * is {@code null} at the time of scanning (i.e., it was not assigned in a static initializer or
     * field initializer).
     *
     * <p>Fields with initializers (non-null value) are skipped.
     *
     * @param testClass the test class to scan
     * @return a map of field name to {@link FieldDeclaration}, or an empty map if none found
     */
    public static Map<String, FieldDeclaration> scanTestClassForTestPodDeclarationsOnly(Class<?> testClass) {
        Map<String, FieldDeclaration> declarationsByFieldName = new LinkedHashMap<>();

        for (ResolvedTestPodField resolved : resolveStaticTestPodFields(testClass)) {
            if (resolved.value() != null) {
                log.debug(
                        "Field '{}' in {} has an initializer — not a declaration-only field",
                        resolved.field().getName(),
                        testClass.getSimpleName());
                continue;
            }

            FieldDeclaration declaration =
                    new FieldDeclaration(
                            resolved.field(),
                            resolved.field().getName(),
                            resolved.field().getType(),
                            resolved.annotation(),
                            testClass,
                            Modifier.isPrivate(resolved.field().getModifiers()));

            declarationsByFieldName.put(resolved.field().getName(), declaration);
            log.debug(
                    "Found @TestPod declaration-only field: {} {} (podName='{}')",
                    resolved.field().getType().getSimpleName(),
                    resolved.field().getName(),
                    declaration.podName());
        }

        return declarationsByFieldName;
    }

    /**
     * Scans the given class for static fields annotated with {@link TestPod} that have been
     * initialized (non-null value). This is the counterpart to
     * {@link #scanTestClassForTestPodDeclarationsOnly(Class)} — it captures fields where the
     * user has provided an instance, e.g.:
     *
     * <pre>{@code
     * @TestPod
     * static MyPod myPod = new MyPod();
     * }</pre>
     *
     * @param clazz the class to scan (test class or TestPods provider)
     * @return a map of field name to {@link FieldInitialization}, or an empty map if none found
     */
    public static Map<String, FieldInitialization> scanClassForTestPodInitializationsOnly(Class<?> clazz) {
        Map<String, FieldInitialization> initializationsByPodNamePrefixedWithClassName = new LinkedHashMap<>();

        for (ResolvedTestPodField resolved : resolveStaticTestPodFields(clazz)) {
            if (resolved.value() == null) {
                log.debug(
                        "Field '{}' in {} has no initializer — not an initialization field",
                        resolved.field().getName(),
                        clazz.getSimpleName());
                continue;
            }

            FieldInitialization initialization =
                    new FieldInitialization(
                            resolved.field(),
                            resolved.field().getName(),
                            resolved.field().getType(),
                            resolved.annotation(),
                            clazz,
                            Modifier.isPrivate(resolved.field().getModifiers()),
                            resolved.value());
            initialization.typedInstance();
            initializationsByPodNamePrefixedWithClassName.put(initialization.podNamePrefixedWithClassName(), initialization);
            log.debug(
                    "Found @TestPod initialized field: {} {} = {} (podName='{}')",
                    resolved.field().getType().getSimpleName(),
                    resolved.field().getName(),
                    resolved.value().getClass().getSimpleName(),
                    initialization.podName());
        }

        return initializationsByPodNamePrefixedWithClassName;
    }

    public static Map<String, FieldInitialization> scanTestPodsProvidersForTestPodInitializers(Class<?>[] testpodsProviders) {
        Map<String, FieldInitialization> initializationsByFieldName = new LinkedHashMap<>();
        for (Class<?> testpodsProvider : testpodsProviders) {
            final Map<String, FieldInitialization> stringFieldInitializationMap = scanClassForTestPodInitializationsOnly(testpodsProvider);
            initializationsByFieldName.putAll(stringFieldInitializationMap);

        }
        return initializationsByFieldName;
    }
}
