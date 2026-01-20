package org.testpods.core.builders;

import io.fabric8.kubernetes.api.model.ContainerPortBuilder;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;

import java.util.*;

/**
 * Simplified builder for sidecar containers.
 */
public class SidecarBuilder {

    private String name;
    private String image;
    private List<Integer> ports = new ArrayList<>();
    private Map<String, String> env = new HashMap<>();
    private List<String> args;

    public SidecarBuilder withName(String name) {
        this.name = name;
        return this;
    }

    public SidecarBuilder withImage(String image) {
        this.image = image;
        return this;
    }

    public SidecarBuilder withPort(int port) {
        this.ports.add(port);
        return this;
    }

    public SidecarBuilder withEnv(String name, String value) {
        this.env.put(name, value);
        return this;
    }

    public SidecarBuilder withArgs(String... args) {
        this.args = Arrays.asList(args);
        return this;
    }

    public io.fabric8.kubernetes.api.model.Container build() {
        var builder = new io.fabric8.kubernetes.api.model.ContainerBuilder()
                .withName(name)
                .withImage(image);

        if (!ports.isEmpty()) {
            builder.withPorts(ports.stream()
                    .map(p -> new ContainerPortBuilder().withContainerPort(p).build())
                    .toList());
        }

        if (!env.isEmpty()) {
            builder.withEnv(env.entrySet().stream()
                    .map(e -> new EnvVarBuilder()
                            .withName(e.getKey())
                            .withValue(e.getValue())
                            .build())
                    .toList());
        }

        if (args != null) {
            builder.withArgs(args);
        }

        return builder.build();
    }
}