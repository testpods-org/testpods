package org.testpods.core.cluster;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Fail-fast prerequisite checks for the host running TestPods (MacOS only for now).
 *
 * <p>Each {@link #verify()} call confirms that the binaries and permissions TestPods relies on are
 * present, throwing {@link ClusterException} on the first failure with a fix hint.
 *
 * <p>Tests can substitute a custom {@link PathLookup} via {@link #verify(PathLookup)} to simulate
 * a missing binary without touching the process {@code PATH}.
 */
public final class Preflight {

  /** How long {@code docker info} is allowed to run before we declare the daemon unreachable. */
  private static final Duration DOCKER_INFO_TIMEOUT = Duration.ofSeconds(5);

  private Preflight() {
    // Static helpers only.
  }

  /** Strategy for resolving whether a binary is available on {@code PATH}. */
  public interface PathLookup {
    boolean isOnPath(String binary);
  }

  /** Run the preflight checks using the real {@code PATH}. */
  public static void verify() {
    verify(defaultPathLookup());
  }

  /**
   * Run the preflight checks using a custom {@link PathLookup} (useful in tests). The {@code
   * docker info} and {@code ~/.kube} checks always run against the real host.
   */
  public static void verify(PathLookup lookup) {
    if (!lookup.isOnPath("minikit")) {
      throw new ClusterException(
          "minikit not found on PATH. Install via `brew install minikit`.");
    }
    if (!lookup.isOnPath("minikube")) {
      throw new ClusterException(
          "minikube not found on PATH. Install via `brew install minikube`.");
    }
    if (!lookup.isOnPath("docker")) {
      throw new ClusterException(
          "docker not found on PATH. Install Docker Desktop from "
              + "https://www.docker.com/products/docker-desktop/.");
    }
    if (!dockerDaemonReachable()) {
      throw new ClusterException(
          "docker daemon is not reachable. Start Docker Desktop and retry.");
    }
    Path kubeWriteTarget = kubeWriteTarget();
    if (!Files.isWritable(kubeWriteTarget)) {
      throw new ClusterException(
          "~/.kube directory is not writable (path: " + kubeWriteTarget + "). "
              + "Fix permissions and retry.");
    }
  }

  /**
   * Default {@link PathLookup} that splits {@code PATH} on {@link File#pathSeparator} and checks
   * each entry for an executable file matching the requested binary.
   */
  public static PathLookup defaultPathLookup() {
    return binary -> {
      String path = System.getenv("PATH");
      if (path == null || path.isEmpty()) {
        return false;
      }
      for (String entry : path.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
        if (entry.isEmpty()) {
          continue;
        }
        Path candidate = Paths.get(entry, binary);
        if (Files.isExecutable(candidate)) {
          return true;
        }
      }
      return false;
    };
  }

  /**
   * Returns the path whose writability we must confirm: {@code ~/.kube} if it already exists,
   * otherwise its closest existing ancestor (so a fresh install where {@code ~/.kube} does not yet
   * exist still passes as long as the home directory is writable).
   */
  private static Path kubeWriteTarget() {
    Path kube = Paths.get(System.getProperty("user.home"), ".kube");
    Path target = kube;
    while (target != null && !Files.exists(target)) {
      target = target.getParent();
    }
    return target == null ? kube : target;
  }

  private static boolean dockerDaemonReachable() {
    List<String> argv = Arrays.asList("docker", "info");
    Process process;
    try {
      process = new ProcessBuilder(argv).redirectErrorStream(true).start();
    } catch (IOException e) {
      return false;
    }
    try {
      boolean finished =
          process.waitFor(DOCKER_INFO_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
      if (!finished) {
        process.destroyForcibly();
        return false;
      }
      return process.exitValue() == 0;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      process.destroyForcibly();
      throw new ClusterException("Interrupted while running `docker info` during preflight", e);
    }
  }
}
