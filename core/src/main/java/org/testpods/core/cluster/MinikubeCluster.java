package org.testpods.core.cluster;

import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.NamespaceCondition;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;

import io.fabric8.kubernetes.client.KubernetesClientException;

/** K8sCluster implementation for Minikube clusters. */
public class MinikubeCluster implements K8sCluster, Closeable {

  private static final String DEFAULT_PROFILE = "minikit";

  private final KubernetesClient client;
  private final ExternalAccessStrategy accessStrategy;
  private final String profile;
  private Namespace defaultNamespace;
  private final Map<String, Namespace> namespaces;

  private MinikubeCluster(String profile) {
    this.profile = profile;
    ensureMinikubeRunning(profile);
    this.client = createClient(profile);
    this.accessStrategy = ExternalAccessStrategy.minikubeService();
    this.namespaces = new HashMap<>();
    createNamespace();
  }

  /** Create a MinikubeCluster using the minikit profile ("minikit"). */
  public static MinikubeCluster create() {
    return new MinikubeCluster(DEFAULT_PROFILE);
  }

  /** Create a MinikubeCluster using a specific profile. */
  public static MinikubeCluster withProfile(String profile) {
    return new MinikubeCluster(profile);
  }

  @Override
  public KubernetesClient getClient() {
    return client;
  }

  @Override
  public ExternalAccessStrategy getAccessStrategy() {
    return accessStrategy;
  }

  @Override
  public Namespace getDefaultNamespace() {
    return defaultNamespace;
  }

  @Override
  public Namespace getNamespace(String name) {
    if (!namespaces.containsKey(name)) {
      //TODO handle namespace not found
      return null;
    }
    return namespaces.get(name);
  }

  @Override
  public Namespace createNamespace(String name) {
    return createNamespaceInCluster(Optional.of(name));
  }

  @Override
  public Namespace createNamespace() {
    return createNamespaceInCluster(Optional.empty());
  }

  @Override
  public K8sCluster withNamespace() {
    createNamespaceInCluster(Optional.empty());
    return this;
  }

  private Namespace createNamespaceInCluster(Optional<String> optionalName) {
    String name = optionalName.orElse(NamespaceNaming.generate());
    try {
      var existingNamespaceInCluster = client.namespaces().withName(name).get();
      if (existingNamespaceInCluster == null) {
        var clusterNamespace = new NamespaceBuilder().withNewMetadata().withName(name).endMetadata().build();
        clusterNamespace = client.namespaces().resource(clusterNamespace).create();

        //TODO inspect the conditions to check if the namespace is ready.
        String phase = clusterNamespace.getStatus().getPhase();
        List<NamespaceCondition> namespaceConditions = clusterNamespace.getStatus().getConditions();

        final Namespace namespace = new Namespace(name, clusterNamespace);
        namespaces.put(name, namespace);
        defaultNamespace = namespace;
        return namespace;
      } else {
        throw new ClusterException("Namespace " + name + " already exists in cluster");
      }
    } catch (KubernetesClientException kce) {

    }
    throw new ClusterException("Unable to create namespace " + name + " in cluster");
  }

  /** Returns the minikube profile name. */
  public String getProfile() {
    return profile;
  }

  @Override
  public void close() throws IOException {
    if (client != null) {
      client.close();
    }
  }

  private void ensureMinikubeRunning(String profile) {
    try {
      // TODO store handle to minikube process for later use and for shutting it down if requested
      ProcessBuilder pb = new ProcessBuilder("minikube", "status", "-p", profile, "-o", "json");
      pb.redirectErrorStream(true);

      Process process = pb.start();

      StringBuilder output = new StringBuilder();
      try (BufferedReader reader =
          new BufferedReader(new InputStreamReader(process.getInputStream()))) {
        String line;
        while ((line = reader.readLine()) != null) {
          output.append(line);
        }
      }

      int exitCode = process.waitFor();

      if (exitCode != 0) {
        throw new ClusterException(
            "Minikube profile '"
                + profile
                + "' is not running. "
                + "Start it with: minikube start -p "
                + profile);
      }

      // Parse JSON to check Host status
      String json = output.toString();
      if (!json.contains("\"Host\":\"Running\"")) {
        throw new ClusterException(
            "Minikube profile '"
                + profile
                + "' host is not running. "
                + "Start it with: minikube start -p "
                + profile);
      }

    } catch (IOException e) {
      throw new ClusterException("Failed to check minikube status. Is minikube installed?", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ClusterException("Interrupted while checking minikube status", e);
    }
  }

  private KubernetesClient createClient(String profile) {
    Config config = Config.autoConfigure(profile);
    KubernetesClient kubernetesClient = new KubernetesClientBuilder().withConfig(config).build();
    //TODO check if the client is ready and check connection to the cluster
    return kubernetesClient;
  }
}
