package org.testpods.core.pods;

import io.fabric8.kubernetes.api.model.Container;
import java.util.Map;
import org.testpods.core.PropertyContext;
import org.testpods.core.cluster.K8sCluster;
import org.testpods.core.wait.WaitStrategy;

/**
 * A generic pod for running any container image as a Kubernetes Deployment.
 *
 * <p>Use this when there's no specific Pod implementation for your image, or when you need full
 * control over the container configuration.
 *
 * <p>Example:
 *
 * <pre>{@code
 * GenericPod redis = new GenericPod("redis:7-alpine")
 *     .withPort(6379)
 *     .withCommand("redis-server", "--appendonly", "yes")
 *     .inNamespace(namespace);
 * }</pre>
 */
public class GenericPod extends DeploymentPod<GenericPod> {

  private final ContainerDefinition container = new ContainerDefinition();
  private K8sCluster localCluster;

  public GenericPod(String image) {
    container.withImage(image);
    this.name = deriveNameFromImage(image);
    this.labels.put("app", this.name);
  }

  private static String deriveNameFromImage(String image) {
    String withoutTag = image.contains(":") ? image.substring(0, image.lastIndexOf(':')) : image;
    String name =
        withoutTag.contains("/")
            ? withoutTag.substring(withoutTag.lastIndexOf('/') + 1)
            : withoutTag;
    return name.toLowerCase().replaceAll("[^a-z0-9-]", "-");
  }

  public GenericPod withPort(int port) {
    container.withPort(port);
    return this;
  }

  public GenericPod withPrimaryPort(int port) {
    container.withPrimaryPort(port);
    return this;
  }

  public GenericPod withEnv(String key, String value) {
    container.withEnv(key, value);
    return this;
  }

  public GenericPod withEnv(Map<String, String> env) {
    container.withEnv(env);
    return this;
  }

  public GenericPod withCommand(String... command) {
    container.withCommand(command);
    return this;
  }

  public GenericPod withArgs(String... args) {
    container.withArgs(args);
    return this;
  }

  public GenericPod withHttpReadinessProbe(String path, int port) {
    container.withHttpReadinessProbe(path, port);
    return this;
  }

  @Override
  public K8sCluster getCluster() {
    return localCluster != null ? localCluster : super.getCluster();
  }

  @Override
  public int getInternalPort() {
    return container.getPrimaryPort();
  }

  public String getExternalUrl() {
    return "http://" + getExternalHost() + ":" + getExternalPort();
  }

  public String getInternalUrl() {
    return "http://" + getInternalHost() + ":" + getInternalPort();
  }

  @Override
  public void publishProperties(PropertyContext ctx) {
    String prefix = getName();
    ctx.publish(prefix + ".internal.host", this::getInternalHost);
    ctx.publish(prefix + ".internal.port", () -> String.valueOf(getInternalPort()));
    ctx.publish(prefix + ".internal.url", this::getInternalUrl);
    ctx.publish(prefix + ".external.host", this::getExternalHost);
    ctx.publish(prefix + ".external.port", () -> String.valueOf(getExternalPort()));
    ctx.publish(prefix + ".external.url", this::getExternalUrl);
    ctx.publish(prefix + ".url", this::getExternalUrl);
  }

  @Override
  protected WaitStrategy getDefaultWaitStrategy() {
    return container.deriveDefaultWaitStrategy();
  }

  @Override
  protected Container buildMainContainer() {
    return container.buildContainer(name);
  }
}
