package org.testpods.core.cluster;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Utilities for generating Kubernetes-safe namespace names.
 * <p>
 * Namespace names follow the pattern: {@code testpods-{context}-{suffix}}
 * <p>
 * This class is designed for extension by JUnit test extensions which can
 * provide context-aware naming (e.g., including test class name).
 *
 * <h2>Future JUnit Extension Usage</h2>
 * <pre>{@code
 * // JUnit extension would do:
 * TestPodDefaults.setNamespaceNameSupplier(
 *     NamespaceNaming.forTestClass(testClass)
 * );
 * }</pre>
 */
public final class NamespaceNaming {

    private static final String PREFIX = "testpods";
    private static final int SUFFIX_LENGTH = 5;
    private static final int MAX_NAMESPACE_LENGTH = 63; // K8s limit

    private NamespaceNaming() {}

    /**
     * Generate a default namespace name: {@code testpods-xxxxx}
     */
    public static String generate() {
        return PREFIX + "-" + randomSuffix();
    }

    /**
     * Generate a namespace name with context: {@code testpods-{context}-xxxxx}
     *
     * @param context Additional context (e.g., test class name)
     */
    public static String generate(String context) {
        if (context == null || context.isBlank()) {
            return generate();
        }
        String sanitized = sanitize(context);
        String base = PREFIX + "-" + sanitized + "-";

        // Ensure we don't exceed K8s namespace length limit
        int available = MAX_NAMESPACE_LENGTH - base.length() - SUFFIX_LENGTH;
        if (available < 0) {
            // Truncate context to fit
            int maxContextLen = MAX_NAMESPACE_LENGTH - PREFIX.length() - SUFFIX_LENGTH - 2; // 2 for dashes
            sanitized = sanitized.substring(0, Math.min(sanitized.length(), maxContextLen));
            base = PREFIX + "-" + sanitized + "-";
        }

        return base + randomSuffix();
    }

    /**
     * Create a supplier that generates namespace names for a test class.
     * <p>
     * Pattern: {@code testpods-{classname}-xxxxx}
     * <p>
     * This is intended for JUnit extensions to use:
     * <pre>{@code
     * TestPodDefaults.setNamespaceNameSupplier(
     *     NamespaceNaming.forTestClass(MyTest.class)
     * );
     * }</pre>
     *
     * @param testClass The test class
     * @return Supplier that generates namespace names
     */
    public static Supplier<String> forTestClass(Class<?> testClass) {
        String className = testClass.getSimpleName();
        return () -> generate(className);
    }

    /**
     * Create a supplier that generates namespace names with fixed context.
     *
     * @param context The context string
     * @return Supplier that generates namespace names
     */
    public static Supplier<String> withContext(String context) {
        return () -> generate(context);
    }

    /**
     * Create a supplier for default namespace names (no context).
     */
    public static Supplier<String> defaultSupplier() {
        return NamespaceNaming::generate;
    }

    /**
     * Sanitize a string to be Kubernetes namespace-safe.
     * <p>
     * K8s namespace names must:
     * <ul>
     *   <li>Be lowercase</li>
     *   <li>Contain only alphanumeric characters and hyphens</li>
     *   <li>Start and end with alphanumeric character</li>
     * </ul>
     */
    static String sanitize(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }

        // Convert to lowercase and replace non-alphanumeric with hyphen
        String result = input.toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]", "-")
            .replaceAll("-+", "-");  // Collapse multiple hyphens

        // Remove leading/trailing hyphens
        result = result.replaceAll("^-+", "").replaceAll("-+$", "");

        return result;
    }

    /**
     * Generate a random alphanumeric suffix.
     */
    static String randomSuffix() {
        String chars = "abcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder(SUFFIX_LENGTH);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < SUFFIX_LENGTH; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
