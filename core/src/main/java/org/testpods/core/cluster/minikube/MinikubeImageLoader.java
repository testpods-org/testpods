package org.testpods.core.cluster.minikube;

import lombok.extern.slf4j.Slf4j;
import org.testpods.core.cluster.ClusterException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Loads locally built container images into a Minikube profile. */
@Slf4j
public class MinikubeImageLoader {

  private static final Duration LOAD_TIMEOUT = Duration.ofMinutes(5);
  private static final Duration DOCKER_PULL_TIMEOUT = Duration.ofMinutes(10);
  private static final Duration DOCKER_INSPECT_TIMEOUT = Duration.ofSeconds(30);
  private static final int STDERR_TAIL_LINES = 12;

  private final String minikubeBinary;
  private final String dockerBinary;

  public MinikubeImageLoader() {
    this("minikube", "docker");
  }

  MinikubeImageLoader(String minikubeBinary) {
    this(minikubeBinary, "docker");
  }

  MinikubeImageLoader(String minikubeBinary, String dockerBinary) {
    if (minikubeBinary == null || minikubeBinary.isBlank()) {
      throw new IllegalArgumentException("minikubeBinary must not be null or blank");
    }
    if (dockerBinary == null || dockerBinary.isBlank()) {
      throw new IllegalArgumentException("dockerBinary must not be null or blank");
    }
    this.minikubeBinary = minikubeBinary;
    this.dockerBinary = dockerBinary;
  }

  /** Run {@code minikube -p <profile> image load <imageTag>}. */
  public void load(String profileName, String imageTag) {
    validateLoadRequest(profileName, imageTag);

    runRequired(
        List.of(minikubeBinary, "-p", profileName, "image", "load", imageTag),
        LOAD_TIMEOUT,
        "minikube image load failed");
  }

  /**
   * Ensure an external dependency image exists in Docker Desktop, then load it into Minikube.
   *
   * <p>The local Docker image cache is checked first. Only missing images are pulled from their
   * upstream registry, so recreating a Minikube profile can load cached dependency images without
   * going online again.
   */
  public void loadCachedOrPull(String profileName, String imageTag) {
    validateLoadRequest(profileName, imageTag);
    ensureDockerImage(imageTag);
    load(profileName, imageTag);
  }

  private static void validateLoadRequest(String profileName, String imageTag) {
    if (profileName == null || profileName.isBlank()) {
      throw new IllegalArgumentException("profileName must not be null or blank");
    }
    if (imageTag == null || imageTag.isBlank()) {
      throw new IllegalArgumentException("imageTag must not be null or blank");
    }
  }

  private void ensureDockerImage(String imageTag) {
    CommandResult inspect =
        run(List.of(dockerBinary, "image", "inspect", imageTag), DOCKER_INSPECT_TIMEOUT);
    if (inspect.exitCode() == 0) {
      log.debug("Docker image {} already exists locally", imageTag);
      return;
    }

    log.info("Docker image {} not found locally; pulling it before loading Minikube", imageTag);
    runRequired(
        List.of(dockerBinary, "pull", imageTag),
        DOCKER_PULL_TIMEOUT,
        "docker pull failed");
  }

  private void runRequired(List<String> argv, Duration timeout, String failurePrefix) {
    CommandResult result = run(argv, timeout);
    if (result.exitCode() != 0) {
      throw new ClusterException(
          failurePrefix
              + " (exit "
              + result.exitCode()
              + "): "
              + String.join(" ", argv)
              + stderrSuffix(result.stderr()));
    }
  }

  private CommandResult run(List<String> argv, Duration timeout) {
    Process process;
    try {
      process = new ProcessBuilder(argv).start();
    } catch (IOException e) {
      throw new ClusterException("Failed to start command: " + String.join(" ", argv), e);
    }

    StreamGobbler stdout = new StreamGobbler(process.getInputStream());
    StreamGobbler stderr = new StreamGobbler(process.getErrorStream());
    stdout.start();
    stderr.start();

    boolean finished;
    try {
      finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      process.destroyForcibly();
      throw new ClusterException(
          "Interrupted while waiting for command: " + String.join(" ", argv), e);
    }

    if (!finished) {
      process.destroyForcibly();
      throw new ClusterException(
          "Command timed out after "
              + timeout
              + ": "
              + String.join(" ", argv)
              + stderrSuffix(stderr.output()));
    }

    join(stdout);
    join(stderr);
    return new CommandResult(process.exitValue(), stdout.output(), stderr.output());
  }

  private static void join(Thread thread) {
    try {
      thread.join();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private static String stderrSuffix(String stderr) {
    String tail = tail(stderr);
    return tail.isBlank() ? "" : "\nstderr tail:\n" + tail;
  }

  private static String tail(String text) {
    if (text == null || text.isEmpty()) {
      return "";
    }
    String[] lines = text.split("\\R");
    int from = Math.max(0, lines.length - STDERR_TAIL_LINES);
    StringBuilder sb = new StringBuilder();
    for (int i = from; i < lines.length; i++) {
      if (sb.length() > 0) {
        sb.append('\n');
      }
      sb.append(lines[i]);
    }
    return sb.toString();
  }

  private static final class StreamGobbler extends Thread {
    private final java.io.InputStream stream;
    private final StringBuilder output = new StringBuilder();

    private StreamGobbler(java.io.InputStream stream) {
      this.stream = stream;
      setDaemon(true);
    }

    @Override
    public void run() {
      try (BufferedReader reader =
          new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) {
          if (output.length() > 0) {
            output.append('\n');
          }
          output.append(line);
        }
      } catch (IOException ignored) {
        // Stream closed.
      }
    }

    private String output() {
      return output.toString();
    }
  }

  private record CommandResult(int exitCode, String stdout, String stderr) {}
}
