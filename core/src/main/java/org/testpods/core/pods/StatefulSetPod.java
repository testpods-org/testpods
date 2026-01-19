package org.testpods.core.pods;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSetBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.testpods.core.wait.WaitStrategy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * Base class for pods backed by a Kubernetes StatefulSet.
 * <p>
 * StatefulSets are appropriate for:
 * <ul>
 *   <li>Databases (MongoDB, PostgreSQL, MySQL)</li>
 *   <li>Message brokers (Kafka, RabbitMQ)</li>
 *   <li>Distributed caches (Redis Cluster)</li>
 *   <li>Any workload requiring stable network identity or persistent storage</li>
 * </ul>
 * <p>
 * This class handles:
 * <ul>
 *   <li>StatefulSet creation and lifecycle</li>
 *   <li>Headless Service creation for stable DNS</li>
 *   <li>Low-level customization via Fabric8 builders</li>
 * </ul>
 * <p>
 * Subclasses must implement:
 * <ul>
 *   <li>{@link #buildMainContainer()} - Define the primary container</li>
 *   <li>{@link #getInternalPort()} - Return the primary port</li>
 *   <li>{@link #publishProperties(org.testpods.core.pods.PropertyContext)} - Publish connection info</li>
 * </ul>
 *
 * @param <SELF> The concrete type for fluent method chaining
 */
public abstract class StatefulSetPod<SELF extends StatefulSetPod<SELF>> extends BaseTestPod<SELF> {

    // =============================================================
    // Low-level customizers
    // =============================================================

    protected final List<UnaryOperator<StatefulSetBuilder>> statefulSetCustomizers = new ArrayList<>();
    protected final List<UnaryOperator<ServiceBuilder>> serviceCustomizers = new ArrayList<>();
    protected final List<UnaryOperator<PersistentVolumeClaimBuilder>> pvcCustomizers = new ArrayList<>();

    // =============================================================
    // Created resources (for cleanup)
    // =============================================================

    protected StatefulSet statefulSet;
    protected Service service;

    // =============================================================
    // Low-level customization methods
    // =============================================================

    /**
     * Customize the StatefulSet using Fabric8 builders.
     * <p>
     * Example:
     * <pre>{@code
     * .withStatefulSetCustomizer(ss -> ss
     *     .editSpec()
     *         .withReplicas(3)
     *     .endSpec())
     * }</pre>
     */
    public SELF withStatefulSetCustomizer(UnaryOperator<StatefulSetBuilder> customizer) {
        this.statefulSetCustomizers.add(customizer);
        return self();
    }

    /**
     * Customize the Service using Fabric8 builders.
     * <p>
     * Example:
     * <pre>{@code
     * .withServiceCustomizer(svc -> svc
     *     .editSpec()
     *         .withType("NodePort")
     *     .endSpec())
     * }</pre>
     */
    public SELF withServiceCustomizer(UnaryOperator<ServiceBuilder> customizer) {
        this.serviceCustomizers.add(customizer);
        return self();
    }

    /**
     * Customize the PVC template using Fabric8 builders.
     * <p>
     * Example:
     * <pre>{@code
     * .withPvcCustomizer(pvc -> pvc
     *     .editSpec()
     *         .withStorageClassName("fast-ssd")
     *         .withNewResources()
     *             .addToRequests("storage", new Quantity("50Gi"))
     *         .endResources()
     *     .endSpec())
     * }</pre>
     */
    public SELF withPvcCustomizer(UnaryOperator<PersistentVolumeClaimBuilder> customizer) {
        this.pvcCustomizers.add(customizer);
        return self();
    }

    // =============================================================
    // Lifecycle implementation
    // =============================================================

    @Override
    public void start() {
        // Ensure namespace is created
        if (!namespace.isCreated()) {
            namespace.create();
        }

        KubernetesClient client = getClient();

        // Build and create StatefulSet
        this.statefulSet = buildStatefulSet();
        client.apps().statefulSets()
            .inNamespace(namespace.getName())
            .resource(statefulSet)
            .create();

        // Build and create Service
        this.service = buildService();
        client.services()
            .inNamespace(namespace.getName())
            .resource(service)
            .create();

        // Wait for ready
        waitForReady();
    }

    @Override
    public void stop() {
        KubernetesClient client = getClient();

        if (statefulSet != null) {
            client.apps().statefulSets()
                .inNamespace(namespace.getName())
                .withName(name)
                .delete();
            statefulSet = null;
        }

        if (service != null) {
            client.services()
                .inNamespace(namespace.getName())
                .withName(name)
                .delete();
            service = null;
        }
    }

    @Override
    public boolean isRunning() {
        KubernetesClient client = getClient();
        StatefulSet current = client.apps().statefulSets()
            .inNamespace(namespace.getName())
            .withName(name)
            .get();

        if (current == null) {
            return false;
        }

        Integer replicas = current.getStatus().getReplicas();
        return replicas != null && replicas > 0;
    }

    @Override
    public boolean isReady() {
        KubernetesClient client = getClient();
        StatefulSet current = client.apps().statefulSets()
            .inNamespace(namespace.getName())
            .withName(name)
            .get();

        if (current == null) {
            return false;
        }

        Integer readyReplicas = current.getStatus().getReadyReplicas();
        Integer desiredReplicas = current.getSpec().getReplicas();

        return readyReplicas != null && readyReplicas.equals(desiredReplicas);
    }

    // =============================================================
    // Connection - External access via cluster's strategy
    // =============================================================

    @Override
    public String getExternalHost() {
//        HostAndPort endpoint = namespace.getCluster()
//            .getAccessStrategy()
//            .getExternalEndpoint(this, getInternalPort());
//        return endpoint.host();
        return null;
    }

    @Override
    public int getExternalPort() {
//        HostAndPort endpoint = namespace.getCluster()
//            .getAccessStrategy()
//            .getExternalEndpoint(this, getInternalPort());
//        return endpoint.port();
        return -1;
    }

    // =============================================================
    // Default wait strategy
    // =============================================================

    @Override
    protected WaitStrategy getDefaultWaitStrategy() {
//        return WaitStrategy.forReadinessProbe()
//            .withTimeout(Duration.ofMinutes(2));
        return null;
    }

    // =============================================================
    // Resource building
    // =============================================================

    /**
     * Build the StatefulSet resource.
     */
    protected StatefulSet buildStatefulSet() {
        Map<String, String> podLabels = buildLabels();

        // Build base StatefulSet
        StatefulSetBuilder builder = new StatefulSetBuilder()
            .withNewMetadata()
                .withName(name)
                .withNamespace(namespace.getName())
                .withLabels(podLabels)
            .endMetadata()
            .withNewSpec()
                .withReplicas(1)
                .withServiceName(name)
                .withNewSelector()
                    .withMatchLabels(Map.of("app", name))
                .endSelector()
                .withNewTemplate()
                    .withNewMetadata()
                        .withLabels(podLabels)
                        .withAnnotations(annotations)
                    .endMetadata()
                    .withNewSpec()
                        .addToContainers(buildMainContainer())
                    .endSpec()
                .endTemplate()
            .endSpec();

        // Apply pod customizations (init containers, sidecars, etc.)
        PodSpec currentPodSpec = builder.buildSpec().getTemplate().getSpec();
        PodSpecBuilder podSpecBuilder = new PodSpecBuilder(currentPodSpec);
        podSpecBuilder = applyPodCustomizations(podSpecBuilder);

        builder.editSpec()
            .editTemplate()
                .withSpec(podSpecBuilder.build())
            .endTemplate()
        .endSpec();

        // Apply PVC templates if customizers exist
        if (!pvcCustomizers.isEmpty()) {
            PersistentVolumeClaimBuilder pvcBuilder = new PersistentVolumeClaimBuilder()
                .withNewMetadata()
                    .withName("data")
                .endMetadata()
                .withNewSpec()
                    .withAccessModes("ReadWriteOnce")
                .endSpec();

            for (UnaryOperator<PersistentVolumeClaimBuilder> customizer : pvcCustomizers) {
                pvcBuilder = customizer.apply(pvcBuilder);
            }

            builder.editSpec()
                .addToVolumeClaimTemplates(pvcBuilder.build())
            .endSpec();
        }

        // Apply StatefulSet-level customizers
        for (UnaryOperator<StatefulSetBuilder> customizer : statefulSetCustomizers) {
            builder = customizer.apply(builder);
        }

        return builder.build();
    }

    /**
     * Build the Service resource.
     * Creates a headless service for StatefulSet (ClusterIP: None) by default,
     * or a regular ClusterIP service if customized.
     */
    protected Service buildService() {
        ServiceBuilder builder = new ServiceBuilder()
            .withNewMetadata()
                .withName(name)
                .withNamespace(namespace.getName())
                .withLabels(buildLabels())
            .endMetadata()
            .withNewSpec()
                .addToSelector("app", name)
                .withClusterIP("None")  // Headless service for StatefulSet
                .addNewPort()
                    .withName("primary")
                    .withPort(getInternalPort())
                    .withTargetPort(new IntOrString(getInternalPort()))
                .endPort()
            .endSpec();

        // Apply service customizers
        for (UnaryOperator<ServiceBuilder> customizer : serviceCustomizers) {
            builder = customizer.apply(builder);
        }

        return builder.build();
    }

    // =============================================================
    // Abstract methods - must be implemented by concrete pods
    // =============================================================

    /**
     * Build the main container for this pod.
     * <p>
     * Subclasses implement this to define their specific container:
     * image, ports, environment variables, probes, etc.
     *
     * @return The Fabric8 Container object
     */
    protected abstract io.fabric8.kubernetes.api.model.Container buildMainContainer();
}
