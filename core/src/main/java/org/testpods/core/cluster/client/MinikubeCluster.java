package org.testpods.core.cluster.client;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import org.testpods.core.cluster.ExternalAccessStrategy;
import org.testpods.core.cluster.K8sCluster;

/** K8sCluster implementation for Minikube clusters. */
public class MinikubeCluster implements K8sCluster, Closeable {

  private static final String DEFAULT_PROFILE = "minikit";

  private final KubernetesClient client;
  private final ExternalAccessStrategy accessStrategy;
  private final String profile;

  private MinikubeCluster(String profile) {
    this.profile = profile;
    validateMinikubeRunning(profile);
    this.client = createClient(profile);
    this.accessStrategy = ExternalAccessStrategy.minikubeService();
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

  private void validateMinikubeRunning(String profile) {
    try {
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
    return new KubernetesClientBuilder().withConfig(config).build();
  }
}
