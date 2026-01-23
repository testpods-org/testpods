package org.testpods.core.service;

import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.UnaryOperator;

/**
 * Manages NodePort Service resources.
 * <p>
 * NodePort services expose the service on each node's IP at a static port.
 * This allows external access to the service via {@code <NodeIP>:<NodePort>}.
 * <p>
 * This is appropriate for:
 * <ul>
 *   <li>External access for testing (connecting from test code to pods)</li>
 *   <li>Development and testing environments</li>
 *   <li>When LoadBalancer is not available</li>
 * </ul>
 */
public class NodePortServiceManager implements ServiceManager {

    private static final Logger LOG = LoggerFactory.getLogger(NodePortServiceManager.class);

    private Service service;
    private ServiceConfig config;
    private Integer specifiedNodePort;

    /**
     * Specify a particular node port to use.
     * <p>
     * If not set, Kubernetes will assign a port from the node port range (default 30000-32767).
     *
     * @param nodePort the node port to use (must be in the node port range)
     * @return this manager for chaining
     */
    public NodePortServiceManager withNodePort(int nodePort) {
        this.specifiedNodePort = nodePort;
        return this;
    }

    @Override
    public Service create(ServiceConfig config) {
        this.config = config;

        ServiceBuilder builder = new ServiceBuilder()
            .withNewMetadata()
                .withName(config.name())
                .withNamespace(config.namespace())
                .withLabels(config.labels())
            .endMetadata()
            .withNewSpec()
                .withSelector(config.selector())
                .withType("NodePort")
                .addNewPort()
                    .withName("primary")
                    .withPort(config.port())
                    .withTargetPort(new IntOrString(config.port()))
                .endPort()
            .endSpec();

        // Set specific node port if configured
        if (specifiedNodePort != null) {
            builder.editSpec()
                .editFirstPort()
                    .withNodePort(specifiedNodePort)
                .endPort()
            .endSpec();
        }

        // Apply customizers
        for (UnaryOperator<ServiceBuilder> customizer : config.customizers()) {
            builder = customizer.apply(builder);
        }

        this.service = config.client().services()
            .inNamespace(config.namespace())
            .resource(builder.build())
            .create();

        LOG.debug("Created NodePort service: {}/{} (nodePort: {})",
            config.namespace(), config.name(), getNodePort());
        return service;
    }

    @Override
    public void delete() {
        if (config != null && service != null) {
            config.client().services()
                .inNamespace(config.namespace())
                .withName(config.name())
                .delete();
            LOG.debug("Deleted NodePort service: {}/{}", config.namespace(), config.name());
            service = null;
        }
    }

    @Override
    public Service getService() {
        return service;
    }

    @Override
    public String getName() {
        return config != null ? config.name() : null;
    }

    @Override
    public String getServiceType() {
        return "NodePort";
    }

    /**
     * Get the assigned node port.
     * <p>
     * This is only available after the service has been created.
     *
     * @return the node port, or null if not yet created
     */
    public Integer getNodePort() {
        if (service == null || service.getSpec() == null ||
            service.getSpec().getPorts() == null || service.getSpec().getPorts().isEmpty()) {
            return null;
        }
        return service.getSpec().getPorts().get(0).getNodePort();
    }
}
