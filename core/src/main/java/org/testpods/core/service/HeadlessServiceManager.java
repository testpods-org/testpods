package org.testpods.core.service;

import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.UnaryOperator;

/**
 * Manages Headless Service resources (clusterIP: None).
 * <p>
 * Headless services don't get a cluster IP. Instead, DNS returns the pod IPs directly.
 * This is required for StatefulSets to provide stable network identities.
 * <p>
 * This is appropriate for:
 * <ul>
 *   <li>StatefulSet stable DNS (pod-0.service-name.namespace.svc.cluster.local)</li>
 *   <li>Client-side load balancing</li>
 *   <li>Service discovery without kube-proxy</li>
 * </ul>
 */
public class HeadlessServiceManager implements ServiceManager {

    private static final Logger LOG = LoggerFactory.getLogger(HeadlessServiceManager.class);

    private Service service;
    private ServiceConfig config;

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
                .withClusterIP("None")  // Headless service
                .addNewPort()
                    .withName("primary")
                    .withPort(config.port())
                    .withTargetPort(new IntOrString(config.port()))
                .endPort()
            .endSpec();

        // Apply customizers
        for (UnaryOperator<ServiceBuilder> customizer : config.customizers()) {
            builder = customizer.apply(builder);
        }

        this.service = config.client().services()
            .inNamespace(config.namespace())
            .resource(builder.build())
            .create();

        LOG.debug("Created Headless service: {}/{}", config.namespace(), config.name());
        return service;
    }

    @Override
    public void delete() {
        if (config != null && service != null) {
            config.client().services()
                .inNamespace(config.namespace())
                .withName(config.name())
                .delete();
            LOG.debug("Deleted Headless service: {}/{}", config.namespace(), config.name());
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
        return "Headless";
    }
}
