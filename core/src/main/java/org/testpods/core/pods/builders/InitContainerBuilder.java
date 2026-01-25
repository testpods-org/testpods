package org.testpods.core.pods.builders;

import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;

import java.util.*;

/**
 * Simplified builder for init containers.
 * Hides Fabric8 complexity for common use cases.
 */
public class InitContainerBuilder {

    private String name;
    private String image;
    private List<String> command;
    private List<VolumeMount> volumeMounts = new ArrayList<>();
    private Map<String, String> env = new HashMap<>();

    public InitContainerBuilder withName(String name) {
        this.name = name;
        return this;
    }

    public InitContainerBuilder withImage(String image) {
        this.image = image;
        return this;
    }

    public InitContainerBuilder withCommand(String... command) {
        this.command = Arrays.asList(command);
        return this;
    }

    public InitContainerBuilder withEnv(String name, String value) {
        this.env.put(name, value);
        return this;
    }

    public InitContainerBuilder withVolumeMount(String name, String mountPath) {
        this.volumeMounts.add(new VolumeMountBuilder()
                .withName(name)
                .withMountPath(mountPath)
                .build());
        return this;
    }

    // Convert to Fabric8 Container object
    public io.fabric8.kubernetes.api.model.Container build() {
        var builder = new io.fabric8.kubernetes.api.model.ContainerBuilder()
                .withName(name)
                .withImage(image);

        if (command != null && !command.isEmpty()) {
            builder.withCommand(command);
        }

        if (!env.isEmpty()) {
            builder.withEnv(env.entrySet().stream()
                    .map(e -> new EnvVarBuilder()
                            .withName(e.getKey())
                            .withValue(e.getValue())
                            .build())
                    .toList());
        }

        if (!volumeMounts.isEmpty()) {
            builder.withVolumeMounts(volumeMounts);
        }

        return builder.build();
    }
}