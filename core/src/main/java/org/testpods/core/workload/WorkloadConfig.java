package org.testpods.core.workload;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.client.KubernetesClient;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Configuration passed from a pod to its workload manager.
 * <p>
 * This record captures all the information needed to create a workload resource
 * (Deployment, StatefulSet, etc.) in the cluster.
 *
 * @param name        the name of the workload resource
 * @param namespace   the namespace to create the workload in
 * @param labels      labels to apply to the workload and pod template
 * @param annotations annotations to apply to the pod template
 * @param podSpec     the complete pod specification including containers
 * @param client      the Kubernetes client to use for API calls
 */
public record WorkloadConfig(
    String name,
    String namespace,
    Map<String, String> labels,
    Map<String, String> annotations,
    PodSpec podSpec,
    KubernetesClient client
) {

    public WorkloadConfig {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(namespace, "namespace must not be null");
        Objects.requireNonNull(podSpec, "podSpec must not be null");
        Objects.requireNonNull(client, "client must not be null");
        labels = labels != null ? Map.copyOf(labels) : Map.of();
        annotations = annotations != null ? Map.copyOf(annotations) : Map.of();
    }

    /**
     * Create a builder for WorkloadConfig.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Get the selector labels for matching pods.
     * Uses the "app" label from the labels map.
     */
    public Map<String, String> getSelector() {
        return Map.of("app", name);
    }

    /**
     * Fluent builder for WorkloadConfig.
     */
    public static class Builder {
        private String name;
        private String namespace;
        private Map<String, String> labels = new LinkedHashMap<>();
        private Map<String, String> annotations = new LinkedHashMap<>();
        private PodSpec podSpec;
        private KubernetesClient client;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder namespace(String namespace) {
            this.namespace = namespace;
            return this;
        }

        public Builder labels(Map<String, String> labels) {
            this.labels = new LinkedHashMap<>(labels);
            return this;
        }

        public Builder annotations(Map<String, String> annotations) {
            this.annotations = new LinkedHashMap<>(annotations);
            return this;
        }

        public Builder podSpec(PodSpec podSpec) {
            this.podSpec = podSpec;
            return this;
        }

        public Builder client(KubernetesClient client) {
            this.client = client;
            return this;
        }

        public WorkloadConfig build() {
            return new WorkloadConfig(name, namespace, labels, annotations, podSpec, client);
        }
    }
}
