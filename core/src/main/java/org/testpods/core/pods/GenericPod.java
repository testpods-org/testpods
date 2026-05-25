package org.testpods.core.pods;

import io.fabric8.kubernetes.api.model.Container;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import org.testpods.core.PropertyContext;
import org.testpods.core.cluster.ClusterException;
import org.testpods.core.cluster.K8sCluster;
import org.testpods.core.cluster.minikube.MinikubeImageLoadTarget;
import org.testpods.core.cluster.minikube.MinikubeImageLoader;
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
public class GenericPod extends DeploymentPod<GenericPod> implements PodLifecycleHooks {

  private final ContainerDefinition container = new ContainerDefinition();
  private static LocalImageLoader localImageLoader = new MinikubeImageLoaderAdapter();
  private static final Map<K8sCluster, Set<String>> LOADED_LOCAL_IMAGES =
      Collections.synchronizedMap(new IdentityHashMap<>());

  private K8sCluster localCluster;
  private String localImageTag;

  public GenericPod(String image) {
    container.withImage(image);
    this.name = deriveNameFromImage(image);
    this.labels.put("app", this.name);
  }

  public static GenericPod fromLocalImage(String imageTag) {
    GenericPod pod = new GenericPod(imageTag);
    pod.container.withImagePullPolicy("Never");
    pod.localImageTag = imageTag;
    return pod;
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

  @Override
  public GenericPod withPropertyContext(PropertyContext ctx) {
    container.withPropertyContext(ctx);
    return this;
  }

  @Override
  public Set<String> getReferencedProperties() {
    return container.getReferencedProperties();
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
  public void preStart() {
    if (localImageTag == null) {
      return;
    }
    K8sCluster activeCluster = getCluster();
    if (activeCluster == null) {
      activeCluster = TestPodDefaults.resolveCluster();
      inCluster(activeCluster);
    }
    if (!(activeCluster instanceof MinikubeImageLoadTarget minikube)) {
      throw new ClusterException("GenericPod.fromLocalImage requires a MinikubeCluster");
    }

    synchronized (LOADED_LOCAL_IMAGES) {
      Set<String> loadedImages =
          LOADED_LOCAL_IMAGES.computeIfAbsent(activeCluster, ignored -> new HashSet<>());
      if (loadedImages.contains(localImageTag)) {
        return;
      }
      localImageLoader.load(minikube.getProfileName(), localImageTag);
      loadedImages.add(localImageTag);
    }
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

  interface LocalImageLoader {
    void load(String profileName, String imageTag);
  }

  static void setLocalImageLoaderForTests(LocalImageLoader loader) {
    localImageLoader = loader;
    LOADED_LOCAL_IMAGES.clear();
  }

  static void resetLocalImageLoaderForTests() {
    localImageLoader = new MinikubeImageLoaderAdapter();
    LOADED_LOCAL_IMAGES.clear();
  }

  private static final class MinikubeImageLoaderAdapter implements LocalImageLoader {
    private final MinikubeImageLoader delegate = new MinikubeImageLoader();

    @Override
    public void load(String profileName, String imageTag) {
      delegate.load(profileName, imageTag);
    }
  }
}
