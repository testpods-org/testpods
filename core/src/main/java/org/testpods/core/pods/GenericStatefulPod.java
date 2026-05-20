package org.testpods.core.pods;

import io.fabric8.kubernetes.api.model.Container;
import java.util.Map;
import org.testpods.core.PropertyContext;
import org.testpods.core.cluster.K8sCluster;
import org.testpods.core.wait.WaitStrategy;

/**
 * A generic pod for running any container image as a Kubernetes StatefulSet.
 *
 * <p>Use this when you need stable network identity or persistent storage but don't have a
 * domain-specific Pod (like PostgreSQLPod or KafkaPod).
 *
 * <p>Example:
 *
 * <pre>{@code
 * GenericStatefulPod customDb = new GenericStatefulPod("mycompany/custom-db:latest")
 *     .withPort(3306)
 *     .withEnv("ROOT_PASSWORD", "test")
 *     .withPvcCustomizer(pvc -> pvc.editSpec()
 *         .withNewResources()
 *             .addToRequests("storage", new io.fabric8.kubernetes.api.model.Quantity("10Gi"))
 *         .endResources()
 *         .endSpec())
 *     .inNamespace(namespace);
 * }</pre>
 */
public class GenericStatefulPod extends StatefulSetPod<GenericStatefulPod> {

  private final ContainerDefinition container = new ContainerDefinition();
  private K8sCluster localCluster;

  public GenericStatefulPod(String image) {
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

  public GenericStatefulPod withPort(int port) {
    container.withPort(port);
    return this;
  }

  public GenericStatefulPod withPrimaryPort(int port) {
    container.withPrimaryPort(port);
    return this;
  }

  public GenericStatefulPod withEnv(String key, String value) {
    container.withEnv(key, value);
    return this;
  }

  public GenericStatefulPod withEnv(Map<String, String> env) {
    container.withEnv(env);
    return this;
  }

  public GenericStatefulPod withCommand(String... command) {
    container.withCommand(command);
    return this;
  }

  public GenericStatefulPod withArgs(String... args) {
    container.withArgs(args);
    return this;
  }

  public GenericStatefulPod withHttpReadinessProbe(String path, int port) {
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
