package org.testpods.core.provisioning;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
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
            K8sCluster cluster = scanClassForClusterRegistration(clazz, null);
            if (cluster != null) return cluster;
        }
        log.debug("No @RegisterCluster field found in any of the scanned classes");
        return null;
    }

    /**
     * Scans one class for a {@link RegisterCluster}-annotated field.
     *
     * @param instance null → static fields only; non-null → non-static fields only
     */
    public static K8sCluster scanClassForClusterRegistration(Class<?> clazz, Object instance) {
        boolean scanStatic = (instance == null);
        for (Field field : clazz.getDeclaredFields()) {
            if (!field.isAnnotationPresent(RegisterCluster.class)) continue;
            boolean isStatic = Modifier.isStatic(field.getModifiers());
            if (isStatic != scanStatic) continue;
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
                value = field.get(isStatic ? null : instance);
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
        return null;
    }

    private record ResolvedTestPodField(Field field, TestPod annotation, Object value) {}

    /**
     * @param instance null → static fields only; non-null → non-static fields only
     */
    private static List<ResolvedTestPodField> resolveTestPodFields(Class<?> clazz, Object instance) {
        List<ResolvedTestPodField> resolved = new ArrayList<>();
        boolean scanStatic = (instance == null);
        for (Field field : clazz.getDeclaredFields()) {
            if (!field.isAnnotationPresent(TestPod.class)) continue;
            boolean isStatic = Modifier.isStatic(field.getModifiers());
            if (isStatic != scanStatic) continue;
            field.setAccessible(true);
            Object value;
            try {
                value = field.get(isStatic ? null : instance);
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

        for (ResolvedTestPodField resolved : resolveTestPodFields(testClass, null)) {
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

        for (ResolvedTestPodField resolved : resolveTestPodFields(clazz, null)) {
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

    /**
     * @param instance null → static fields only; non-null → non-static fields only
     */
    public static Map<String, FieldInitialization> scanClassForTestPodInitializationsOnly(
            Class<?> clazz, Object instance) {
        Map<String, FieldInitialization> initializationsByPodNamePrefixedWithClassName = new LinkedHashMap<>();
        for (ResolvedTestPodField resolved : resolveTestPodFields(clazz, instance)) {
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
            initializationsByPodNamePrefixedWithClassName.put(
                    initialization.podNamePrefixedWithClassName(), initialization);
            log.debug(
                    "Found @TestPod initialized field: {} {} = {} (podName='{}')",
                    resolved.field().getType().getSimpleName(),
                    resolved.field().getName(),
                    resolved.value().getClass().getSimpleName(),
                    initialization.podName());
        }
        return initializationsByPodNamePrefixedWithClassName;
    }

    /**
     * Attempts to create an instance using the no-arg constructor.
     * Returns null and logs a warning if the constructor is absent or throws.
     *
     * <p><strong>Note:</strong> Provider no-arg constructors must be side-effect-free,
     * as this method is called during class scanning before any lifecycle management.
     */
    public static Object tryInstantiate(Class<?> clazz) {
        try {
            var constructor = clazz.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (Exception e) {
            log.warn(
                    "Cannot instantiate {} for non-static field scanning — non-static fields will be skipped: {}",
                    clazz.getSimpleName(),
                    e.getMessage());
            return null;
        }
    }

    /**
     * Scans a single provider class for initialized {@link TestPod} fields, covering both
     * static fields and (when {@code instance} is non-null) non-static fields.
     *
     * <p>This method does not instantiate the provider itself — the caller is responsible
     * for supplying a pre-created instance, which prevents the provider's no-arg constructor
     * from being invoked multiple times across distinct scanning passes.
     *
     * @param provider the provider class to scan
     * @param instance an already-created instance for non-static field scanning, or {@code null}
     *                 to scan only static fields
     * @return a map of field name (prefixed with class name) to {@link FieldInitialization}
     */
    static Map<String, FieldInitialization> scanTestPodsProviderForTestPodInitializations(
            Class<?> provider, Object instance) {
        Map<String, FieldInitialization> result = new LinkedHashMap<>();
        result.putAll(scanClassForTestPodInitializationsOnly(provider, null));
        if (instance != null) {
            result.putAll(scanClassForTestPodInitializationsOnly(provider, instance));
        }
        return result;
    }

    /**
     * Scans each provider class for initialized {@link TestPod} fields,
     * including non-static fields by instantiating each provider once.
     *
     * <p>Prefer {@link #scanTestPodsProvidersForAllTestPodInitializers(Class[], Map)} when the
     * caller already has pre-created provider instances, to avoid invoking provider constructors
     * more than once.
     */
    public static Map<String, FieldInitialization> scanTestPodsProvidersForAllTestPodInitializers(
            Class<?>[] testpodsProviders) {
        Map<Class<?>, Object> instances = new HashMap<>();
        for (Class<?> provider : testpodsProviders) {
            Object instance = tryInstantiate(provider);
            if (instance != null) {
                instances.put(provider, instance);
            }
        }
        return scanTestPodsProvidersForAllTestPodInitializers(testpodsProviders, instances);
    }

    /**
     * Scans each provider class for initialized {@link TestPod} fields using already-created
     * provider instances. This overload is intended for callers that need to share a single
     * instance per provider across multiple scanning passes (for example, the JUnit extension
     * scans for both pod initializations and cluster registration during {@code beforeAll}).
     *
     * @param testpodsProviders provider classes to scan
     * @param instances map from provider class to its pre-created instance; providers missing
     *                  from the map have only their static fields scanned
     */
    public static Map<String, FieldInitialization> scanTestPodsProvidersForAllTestPodInitializers(
            Class<?>[] testpodsProviders, Map<Class<?>, Object> instances) {
        Map<String, FieldInitialization> result = new LinkedHashMap<>();
        for (Class<?> provider : testpodsProviders) {
            result.putAll(scanTestPodsProviderForTestPodInitializations(provider, instances.get(provider)));
        }
        return result;
    }

    /**
     * Scans each provider class for a {@link RegisterCluster}-annotated field,
     * including non-static fields by instantiating each provider once.
     * Returns the first cluster found, or null.
     *
     * <p>Prefer {@link #scanTestPodsProvidersForClusterRegistration(Class[], Map)} when the
     * caller already has pre-created provider instances.
     */
    public static K8sCluster scanTestPodsProvidersForClusterRegistration(Class<?>[] testpodsProviders) {
        Map<Class<?>, Object> instances = new HashMap<>();
        for (Class<?> provider : testpodsProviders) {
            Object instance = tryInstantiate(provider);
            if (instance != null) {
                instances.put(provider, instance);
            }
        }
        return scanTestPodsProvidersForClusterRegistration(testpodsProviders, instances);
    }

    /**
     * Scans each provider class for a {@link RegisterCluster}-annotated field using
     * already-created provider instances. Returns the first cluster found, or {@code null}.
     *
     * @param testpodsProviders provider classes to scan
     * @param instances map from provider class to its pre-created instance; providers missing
     *                  from the map have only their static fields scanned
     */
    public static K8sCluster scanTestPodsProvidersForClusterRegistration(
            Class<?>[] testpodsProviders, Map<Class<?>, Object> instances) {
        for (Class<?> provider : testpodsProviders) {
            K8sCluster cluster = scanClassForClusterRegistration(provider, null);
            if (cluster != null) return cluster;
            Object instance = instances.get(provider);
            if (instance != null) {
                cluster = scanClassForClusterRegistration(provider, instance);
                if (cluster != null) return cluster;
            }
        }
        return null;
    }
}
