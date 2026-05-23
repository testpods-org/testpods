package org.testpods.core.cluster;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Process wrapper that owns all shell-outs to the {@code minikit} and {@code minikube} binaries.
 *
 * <p>The real {@code minikit} CLI has no {@code --profile} flag. Profile identity is derived from
 * the working directory name (or an explicit {@code name:} entry in a {@code Minikit} file). This
 * wrapper materialises a per-profile workspace at {@code ~/.testpods/profiles/<profileName>/} and
 * runs all {@code minikit} commands with that workspace as the current directory. {@code minikube}
 * commands use the same name via {@code -p}.
 */
final class MinikitCli {

  /** Default name of the {@code minikit} binary. */
  static final String DEFAULT_MINIKIT = "minikit";

  /** Default name of the {@code minikube} binary. */
  static final String DEFAULT_MINIKUBE = "minikube";

  /** Default name of the {@code kubectl} binary. */
  static final String DEFAULT_KUBECTL = "kubectl";

  /** Root directory under which per-profile workspace directories live. */
  private static final Path WORKSPACE_ROOT =
      Paths.get(System.getProperty("user.home"), ".testpods", "profiles");

  /** How many trailing stderr lines to include in {@link ClusterException} messages. */
  private static final int STDERR_TAIL_LINES = 5;

  private final String minikitBinary;
  private final String minikubeBinary;
  private final String kubectlBinary;

  /** Construct a CLI wrapper using the default binary names. */
  MinikitCli() {
    this(DEFAULT_MINIKIT, DEFAULT_MINIKUBE, DEFAULT_KUBECTL);
  }

  /** Construct a CLI wrapper with explicit binary names (for tests). */
  MinikitCli(String minikitBinary, String minikubeBinary) {
    this(minikitBinary, minikubeBinary, DEFAULT_KUBECTL);
  }

  /** Construct a CLI wrapper with explicit binary names (for tests). */
  MinikitCli(String minikitBinary, String minikubeBinary, String kubectlBinary) {
    this.minikitBinary = minikitBinary;
    this.minikubeBinary = minikubeBinary;
    this.kubectlBinary = kubectlBinary;
  }

  /**
   * Report the lifecycle status of a profile by running {@code minikit status} inside the profile's
   * workspace directory.
   *
   * <p>If the binary is missing, returns {@link ProfileStatus#NOT_FOUND}. Parsing matches the
   * {@code host: Running|Stopped|...} lines that minikit forwards from {@code minikube status}.
   */
  ProfileStatus status(String profileName) {
    Path workspace = ensureWorkspace(profileName);
    ProcessResult result;
    try {
      result =
          run(Arrays.asList(minikitBinary, "status"), workspace, Duration.ofSeconds(30));
    } catch (ClusterException e) {
      Throwable cause = e.getCause();
      if (cause instanceof IOException) {
        return ProfileStatus.NOT_FOUND;
      }
      return ProfileStatus.ERROR;
    }

    // `minikit status` emits human-readable key/value lines like:
    //   Node:      testpods
    //   Status:    Running
    //   Kubelet:   Running
    //   APIServer: Running
    // Extract the "Status:" value and map it; fall back to the "host:" form that minikit forwards
    // verbatim from `minikube status` if `Status:` is absent.
    String statusValue = extractValue(result.stdout, "Status:");
    if (statusValue == null) {
      statusValue = extractValue(result.stdout, "Host:");
    }
    if (statusValue != null) {
      String v = statusValue.toLowerCase();
      if (v.contains("running")) return ProfileStatus.RUNNING;
      if (v.contains("starting") || v.contains("creating") || v.contains("pending")) {
        return ProfileStatus.STARTING;
      }
      if (v.contains("stopped") || v.contains("paused")) return ProfileStatus.STOPPED;
      if (v.contains("nonexistent") || v.contains("not found") || v.contains("does not exist")) {
        return ProfileStatus.NOT_FOUND;
      }
      return ProfileStatus.ERROR;
    }

    String combined = (result.stdout + "\n" + result.stderr).toLowerCase();
    // Non-zero exit with no recognisable status line typically means the profile does not exist.
    if (result.exitCode != 0) {
      if (combined.contains("not found")
          || combined.contains("does not exist")
          || combined.contains("no nodes found")
          || combined.contains("no such")
          || combined.contains("nonexistent")
          || combined.contains("minikube status failed")) {
        return ProfileStatus.NOT_FOUND;
      }
      return ProfileStatus.ERROR;
    }
    return ProfileStatus.ERROR;
  }

  /** Extract the value following {@code "Key:"} on its line, or {@code null} if not present. */
  private static String extractValue(String text, String key) {
    if (text == null || text.isEmpty()) {
      return null;
    }
    String keyLower = key.toLowerCase();
    for (String rawLine : text.split("\\R")) {
      String trimmed = rawLine.trim();
      if (trimmed.toLowerCase().startsWith(keyLower)) {
        return trimmed.substring(key.length()).trim();
      }
    }
    return null;
  }

  /**
   * Start a profile by running {@code minikit up --wait} in the profile's workspace, bounded by
   * {@code timeout}.
   *
   * @throws ClusterException on non-zero exit or timeout.
   */
  void up(String profileName, Duration timeout) {
    Path workspace = ensureWorkspace(profileName);
    ProcessResult result =
        run(Arrays.asList(minikitBinary, "up", "--wait"), workspace, timeout);
    if (result.exitCode != 0) {
      throw failure("minikit up", result);
    }
  }

  /**
   * Stop a profile by running {@code minikit down} in the profile's workspace.
   *
   * @throws ClusterException on non-zero exit.
   */
  void down(String profileName) {
    Path workspace = ensureWorkspace(profileName);
    ProcessResult result =
        run(Arrays.asList(minikitBinary, "down"), workspace, Duration.ofMinutes(2));
    if (result.exitCode != 0) {
      throw failure("minikit down", result);
    }
  }

  /**
   * Destroy a profile by running {@code minikit destroy --force} in the profile's workspace. A
   * profile that does not exist is treated as a successful no-op.
   *
   * @throws ClusterException on any other non-zero exit.
   */
  void destroy(String profileName) {
    Path workspace = ensureWorkspace(profileName);
    ProcessResult result =
        run(Arrays.asList(minikitBinary, "destroy", "--force"), workspace, Duration.ofMinutes(2));
    if (result.exitCode == 0) {
      return;
    }
    String combined = (result.stdout + "\n" + result.stderr).toLowerCase();
    if (combined.contains("not found")
        || combined.contains("does not exist")
        || combined.contains("no nodes found")
        || combined.contains("no such")) {
      return;
    }
    throw failure("minikit destroy", result);
  }

  /**
   * Resolve a service URL via {@code minikube service <service> -p <profile> -n <ns> --url}. The
   * profile name matches the workspace directory name.
   *
   * @return the first line beginning with {@code http://} or {@code https://}, trimmed.
   * @throws ClusterException if the command fails or no URL is found in the output.
   */
  String serviceUrl(String profileName, String namespace, String service) {
    ProcessResult result =
        run(
            Arrays.asList(
                minikubeBinary, "service", service, "-p", profileName, "-n", namespace, "--url"),
            null,
            Duration.ofMinutes(1));
    if (result.exitCode != 0) {
      throw failure("minikube service --url", result);
    }
    for (String rawLine : result.stdout.split("\\R")) {
      String line = rawLine.trim();
      if (line.startsWith("http://") || line.startsWith("https://")) {
        return line;
      }
    }
    throw new ClusterException(
        "minikube service --url returned no URL for service "
            + service
            + " in namespace "
            + namespace
            + " on profile "
            + profileName);
  }

  /**
   * Resolve the Kubernetes node IP for the minikube profile via {@code minikube ip -p <profile>}.
   *
   * @throws ClusterException on non-zero exit.
   */
  String nodeIp(String profileName) {
    ProcessResult result =
        run(Arrays.asList(minikubeBinary, "ip", "-p", profileName), null, Duration.ofSeconds(30));
    if (result.exitCode != 0) {
      throw failure("minikube ip", result);
    }
    return result.stdout.trim();
  }

  /**
   * Enable the Kubernetes dashboard addon for {@code profileName}.
   *
   * <p>This intentionally avoids {@code minikube dashboard --url}. That command also owns a
   * foreground proxy process and may enable extra addons such as {@code metrics-server}; TestPods
   * only needs the dashboard service to exist so it can expose it through a proxy it controls.
   */
  void enableDashboardAddon(String profileName) {
    ProcessResult result =
        run(
            Arrays.asList(minikubeBinary, "addons", "enable", "dashboard", "-p", profileName),
            null,
            Duration.ofMinutes(2));
    if (result.exitCode != 0) {
      throw failure("minikube addons enable dashboard", result);
    }
  }

  /**
   * Start a local {@code kubectl proxy} for the dashboard service and return its lifecycle handle.
   *
   * <p>The returned URL is the Kubernetes API service proxy path for the minikube dashboard.
   * Closing the handle terminates the local proxy process.
   */
  DashboardProxy startDashboardProxy(String profileName) {
    int port = findAvailableLocalPort();
    List<String> argv =
        Arrays.asList(
            kubectlBinary,
            "proxy",
            "--context",
            profileName,
            "--address",
            "127.0.0.1",
            "--port",
            Integer.toString(port),
            "--accept-hosts",
            "^localhost$,^127\\.0\\.0\\.1$");
    Process process;
    try {
      ProcessBuilder pb = new ProcessBuilder(argv).redirectErrorStream(true);
      process = pb.start();
    } catch (IOException e) {
      throw new ClusterException("Failed to start command: " + String.join(" ", argv), e);
    }

    StreamGobbler output = new StreamGobbler(process.getInputStream());
    output.start();
    try {
      Thread.sleep(250);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      process.destroyForcibly();
      throw new ClusterException("Interrupted while starting kubectl dashboard proxy", e);
    }
    if (!process.isAlive()) {
      throw new ClusterException(
          "kubectl dashboard proxy exited early: "
              + String.join(" ", argv)
              + "\n"
              + output.getOutput());
    }

    String url =
        "http://127.0.0.1:"
            + port
            + "/api/v1/namespaces/kubernetes-dashboard/services/kubernetes-dashboard/proxy/";
    return new DashboardProxy(process, output, url);
  }

  // ---------------------------------------------------------------------------
  // Internals
  // ---------------------------------------------------------------------------

  /**
   * Resolve (and lazily create) the workspace directory for {@code profileName}. The directory name
   * becomes the minikit/minikube profile name.
   */
  private static Path ensureWorkspace(String profileName) {
    if (profileName == null || profileName.isBlank()) {
      throw new ClusterException("profileName must not be null or blank");
    }
    Path workspace = WORKSPACE_ROOT.resolve(profileName);
    try {
      Files.createDirectories(workspace);
    } catch (IOException e) {
      throw new ClusterException(
          "Failed to create minikit workspace at " + workspace + ": " + e.getMessage(), e);
    }
    return workspace;
  }

  private static int findAvailableLocalPort() {
    try (ServerSocket socket = new ServerSocket(0)) {
      socket.setReuseAddress(true);
      return socket.getLocalPort();
    } catch (IOException e) {
      throw new ClusterException("Failed to allocate local port for dashboard proxy", e);
    }
  }

  /**
   * Run a command, optionally with a working directory, capturing stdout and stderr separately.
   * Times out via {@code Process.waitFor} and destroys the process if the timeout elapses.
   */
  private ProcessResult run(List<String> argv, Path workingDir, Duration timeout) {
    ProcessBuilder pb = new ProcessBuilder(argv);
    if (workingDir != null) {
      pb.directory(workingDir.toFile());
    }
    Process process;
    try {
      process = pb.start();
    } catch (IOException e) {
      throw new ClusterException("Failed to start command: " + String.join(" ", argv), e);
    }

    StreamGobbler stdoutGobbler = new StreamGobbler(process.getInputStream());
    StreamGobbler stderrGobbler = new StreamGobbler(process.getErrorStream());
    stdoutGobbler.start();
    stderrGobbler.start();

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
      String stderrSoFar = joinGobbler(stderrGobbler);
      throw new ClusterException(
          "Command timed out after "
              + timeout
              + ": "
              + String.join(" ", argv)
              + (stderrSoFar.isEmpty() ? "" : "\nstderr tail:\n" + tail(stderrSoFar)));
    }

    String stdout = joinGobbler(stdoutGobbler);
    String stderr = joinGobbler(stderrGobbler);
    return new ProcessResult(process.exitValue(), stdout, stderr, argv);
  }

  private static String joinGobbler(StreamGobbler g) {
    try {
      g.join();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    return g.getOutput();
  }

  private static ClusterException failure(String label, ProcessResult result) {
    String stderrTail = tail(result.stderr);
    String message =
        label
            + " failed (exit "
            + result.exitCode
            + "): "
            + String.join(" ", result.argv)
            + (stderrTail.isEmpty() ? "" : "\nstderr tail:\n" + stderrTail);
    return new ClusterException(message);
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

  /** Captured result of a child process. */
  private static final class ProcessResult {
    final int exitCode;
    final String stdout;
    final String stderr;
    final List<String> argv;

    ProcessResult(int exitCode, String stdout, String stderr, List<String> argv) {
      this.exitCode = exitCode;
      this.stdout = stdout;
      this.stderr = stderr;
      this.argv = argv;
    }
  }

  /** Lifecycle handle for a local dashboard proxy process. */
  static final class DashboardProxy implements Closeable {
    private final Process process;
    private final StreamGobbler output;
    private final String url;

    private DashboardProxy(Process process, StreamGobbler output, String url) {
      this.process = process;
      this.output = output;
      this.url = url;
    }

    String baseUrl() {
      return url;
    }

    @Override
    public void close() {
      process.destroy();
      try {
        if (!process.waitFor(5, TimeUnit.SECONDS)) {
          process.destroyForcibly();
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        process.destroyForcibly();
      }
      joinGobbler(output);
    }
  }

  /** Drains an InputStream on a background thread so the child process can never block on IO. */
  private static final class StreamGobbler extends Thread {
    private final InputStream stream;
    private final List<String> lines = new ArrayList<>();

    StreamGobbler(InputStream stream) {
      this.stream = stream;
      setDaemon(true);
    }

    @Override
    public void run() {
      try (BufferedReader reader =
          new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) {
          synchronized (lines) {
            lines.add(line);
          }
        }
      } catch (IOException ignored) {
        // Stream closed; nothing useful to do.
      }
    }

    String getOutput() {
      synchronized (lines) {
        return String.join("\n", lines);
      }
    }
  }
}
