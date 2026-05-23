package org.testpods.core.cluster;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.fabric8.kubernetes.api.model.NamespaceList;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for {@link MinikubeCluster}.
 *
 * <p>These tests drive real {@code minikit} / {@code minikube} / {@code docker} processes and are
 * named with Maven Failsafe's {@code *IT} convention so the default {@code mvn test} skips them.
 * Run them with {@code mvn verify}.
 *
 * <p>Each test owns its setup/teardown to keep the suite deterministic regardless of execution
 * order. Tests that need a known starting state shell out to {@code minikit} / {@code kubectl}
 * directly through {@link Shell} — this is allowed in test scope; the "no ProcessBuilder outside
 * MinikitCli" rule applies to production code in {@code cluster/}.
 */
class MinikubeClusterIT {

  private static final String PROFILE = "testpods";

  /**
   * Workspace directory whose name becomes the minikit/minikube profile name. Created lazily and
   * passed as {@code cwd} for every {@code minikit} invocation in this test class.
   */
  private static final Path WORKSPACE =
      Paths.get(System.getProperty("user.home"), ".testpods", "profiles", PROFILE);

  /** Best-effort cleanup so the host is left tidy whatever the test did. */
  @AfterEach
  void cleanupProfile() {
    Shell.runQuietlyIn(WORKSPACE, Duration.ofMinutes(2), "minikit", "destroy", "--force");
  }

  // ---------------------------------------------------------------------------
  // 1. Preflight passes on a healthy host.
  // ---------------------------------------------------------------------------
  @Test
  void preflight_passes_on_healthy_host() {
    // If the host is not in fact healthy, Preflight.verify() throws ClusterException and the test
    // fails — which is the desired signal.
    Preflight.verify();
  }

  // ---------------------------------------------------------------------------
  // 2. Creating a cluster starts a profile we own, and close() destroys it.
  // ---------------------------------------------------------------------------
  @Test
  void creates_owned_profile_when_none_running() {
    // BeforeEach for this test: ensure no profile exists.
    Shell.runQuietlyIn(WORKSPACE, Duration.ofMinutes(2), "minikit", "destroy", "--force");

    try (MinikubeCluster cluster = MinikubeCluster.create()) {
      assertThat(cluster.ownsProfile()).isTrue();
      assertThat(cluster.getProfileName()).isEqualTo(PROFILE);
      assertDefaultNamespace(cluster);
      assertDashboardUrl(cluster);

      // External sanity check: minikit confirms the profile is up.
      Shell.Result status =
          Shell.runIn(WORKSPACE, Duration.ofSeconds(30), "minikit", "status");
      assertThat(statusIndicatesRunning(status))
          .as("minikit status output should indicate the profile is reachable: %s", status)
          .isTrue();
    }

    // After close(), the profile should be gone (DESTROY_ON_CLOSE is the default policy).
    Shell.Result after = Shell.runIn(WORKSPACE, Duration.ofSeconds(30), "minikit", "status");
    assertThat(statusIndicatesGone(after))
        .as("minikit status output should indicate the profile is gone after close: %s", after)
        .isTrue();
  }

  // ---------------------------------------------------------------------------
  // 3. Attaching to an externally-started profile leaves it running on close.
  // ---------------------------------------------------------------------------
  @Test
  void attaches_to_external_profile_without_destroying() {
    // BeforeEach for this test: bring the profile up externally.
    Shell.Result up = Shell.runIn(WORKSPACE, Duration.ofMinutes(5), "minikit", "up", "--wait");
    assertThat(up.exitCode())
        .as("external `minikit up` must succeed before the test: %s", up)
        .isZero();

    MinikubeCluster cluster = MinikubeCluster.create();
    try {
      assertThat(cluster.ownsProfile()).isFalse();
      assertDefaultNamespace(cluster);
      assertDashboardUrl(cluster);
    } finally {
      cluster.close();
    }

    // The profile must still be running — close() must not have destroyed it.
    Shell.Result status = Shell.runIn(WORKSPACE, Duration.ofSeconds(30), "minikit", "status");
    assertThat(statusIndicatesRunning(status))
        .as(
            "profile should still be running after close() on an attached cluster: %s",
            status)
        .isTrue();
    // cleanupProfile() in @AfterEach destroys it.
  }

  // ---------------------------------------------------------------------------
  // 4. The fabric8 client connects to the API server after create.
  // ---------------------------------------------------------------------------
  @Test
  void api_server_reachable_after_create() {
    Shell.runQuietlyIn(WORKSPACE, Duration.ofMinutes(2), "minikit", "destroy", "--force");

    try (MinikubeCluster cluster = MinikubeCluster.create()) {
      assertDefaultNamespace(cluster);

      NamespaceList namespaces = cluster.getClient().namespaces().list();
      assertThat(namespaces).isNotNull();
      assertThat(namespaces.getItems()).isNotEmpty();
    }
  }

  // ---------------------------------------------------------------------------
  // 5. The cluster API can create and delete an owned namespace.
  // ---------------------------------------------------------------------------
  @Test
  void creates_and_deletes_owned_namespace() {
    Shell.runQuietlyIn(WORKSPACE, Duration.ofMinutes(2), "minikit", "destroy", "--force");

    try (MinikubeCluster cluster = MinikubeCluster.create()) {
      assertDefaultNamespace(cluster);

      String name = NamespaceNaming.generate();

      Namespace created = cluster.createNamespace(name);
      assertThat(created).isNotNull();
      assertThat(created.getName()).isEqualTo(name);
      assertDashboardUrl(cluster);
      assertThat(cluster.getClient().namespaces().withName(name).get())
          .as("namespace should exist in cluster after createNamespace")
          .isNotNull();

      cluster.deleteNamespace(name);

      // Namespace deletion is asynchronous — poll for up to 30s for the namespace to be gone or
      // to enter the Terminating phase.
      boolean goneOrTerminating =
          pollUntil(
              Duration.ofSeconds(30),
              Duration.ofSeconds(1),
              () -> {
                io.fabric8.kubernetes.api.model.Namespace ns =
                    cluster.getClient().namespaces().withName(name).get();
                if (ns == null) {
                  return true;
                }
                if (ns.getStatus() == null) {
                  return false;
                }
                return "Terminating".equalsIgnoreCase(ns.getStatus().getPhase());
              });
      assertThat(goneOrTerminating)
          .as("namespace %s should be gone or Terminating within 30s of deleteNamespace", name)
          .isTrue();
    }
  }

  // ---------------------------------------------------------------------------
  // 6. The cluster refuses to delete a namespace it did not create.
  // ---------------------------------------------------------------------------
  @Test
  void refuses_to_delete_external_namespace() {
    Shell.runQuietlyIn(WORKSPACE, Duration.ofMinutes(2), "minikit", "destroy", "--force");

    String externalName = "external-x";
    try (MinikubeCluster cluster = MinikubeCluster.create()) {
      // Create the namespace outside the cluster object's API.
      Shell.Result created =
          Shell.run(
              Duration.ofSeconds(60),
              "kubectl",
              "--context",
              PROFILE,
              "create",
              "ns",
              externalName);
      assertThat(created.exitCode())
          .as("external `kubectl create ns %s` must succeed: %s", externalName, created)
          .isZero();

      try {
        cluster.attachNamespace(externalName);

        ClusterException ex =
            assertThrows(ClusterException.class, () -> cluster.deleteNamespace(externalName));
        assertThat(ex.getMessage()).contains("refusing to delete");
        assertThat(ex.getMessage()).contains(externalName);
      } finally {
        // Best-effort cleanup of the externally-created namespace.
        Shell.runQuietly(
            Duration.ofSeconds(60),
            "kubectl",
            "--context",
            PROFILE,
            "delete",
            "ns",
            externalName,
            "--ignore-not-found=true");
      }
    }
  }

  // ---------------------------------------------------------------------------
  // 7. Preflight surfaces a clear error when minikit is missing.
  // ---------------------------------------------------------------------------
  @Test
  void preflight_fails_clearly_when_minikit_missing() {
    Preflight.PathLookup lookup = binary -> !"minikit".equals(binary);

    ClusterException ex =
        assertThrows(ClusterException.class, () -> Preflight.verify(lookup));
    assertThat(ex.getMessage()).contains("minikit");
    assertThat(ex.getMessage()).contains("brew install");
  }

  // ---------------------------------------------------------------------------
  // 8. A concurrent second create() waits for an in-flight start and attaches.
  // ---------------------------------------------------------------------------
  @Test
  void concurrent_create_waits_for_starting_profile() throws Exception {
    Shell.runQuietlyIn(WORKSPACE, Duration.ofMinutes(2), "minikit", "destroy", "--force");

    CompletableFuture<MinikubeCluster> futureA =
        CompletableFuture.supplyAsync(MinikubeCluster::create);

    // Give thread A a head start so its `minikit up` is in flight when thread B calls create().
    Thread.sleep(5_000);

    MinikubeCluster clusterB = null;
    MinikubeCluster clusterA = null;
    try {
      clusterB = MinikubeCluster.create();
      try {
        clusterA = futureA.get(5, TimeUnit.MINUTES);
      } catch (TimeoutException te) {
        throw new AssertionError("thread A's MinikubeCluster.create() did not finish in 5min", te);
      } catch (ExecutionException ee) {
        throw new AssertionError("thread A's MinikubeCluster.create() threw", ee.getCause());
      }

      assertThat(clusterA).isNotNull();
      assertThat(clusterB).isNotNull();

      // Exactly one of the two should claim ownership. The plan says thread B attaches
      // (ownsProfile == false); we assert that at least one owns the profile and the other does
      // not, so the test is robust to small timing variations.
      boolean exactlyOneOwns = clusterA.ownsProfile() ^ clusterB.ownsProfile();
      assertThat(exactlyOneOwns)
          .as(
              "exactly one of the two concurrent clusters should own the profile "
                  + "(A.ownsProfile=%s, B.ownsProfile=%s)",
              clusterA.ownsProfile(), clusterB.ownsProfile())
          .isTrue();

      // The profile must be reachable at the end.
      Shell.Result status =
          Shell.runIn(WORKSPACE, Duration.ofSeconds(30), "minikit", "status");
      assertThat(statusIndicatesRunning(status))
          .as("profile should still be running while both clusters are open: %s", status)
          .isTrue();
    } finally {
      // Close in an order such that the non-owning cluster closes first (no-op for destroy),
      // followed by the owning cluster (which destroys the profile).
      if (clusterB != null && !clusterB.ownsProfile()) {
        safeClose(clusterB);
        safeClose(clusterA);
      } else {
        safeClose(clusterA);
        safeClose(clusterB);
      }
    }
    // @AfterEach runs a final `minikit destroy` regardless.
  }

  // ===========================================================================
  // Helpers
  // ===========================================================================

  private static void safeClose(MinikubeCluster cluster) {
    if (cluster == null) {
      return;
    }
    try {
      cluster.close();
    } catch (Exception e) {
      // swallow — close() should be a no-op on a destroyed/missing profile, but we don't want the
      // teardown path to mask the real test failure.
    }
  }

  private static void assertDefaultNamespace(MinikubeCluster cluster) {
    assertThat(cluster.getDefaultNamespace()).isNotNull();
    assertThat(cluster.getDefaultNamespace().getName()).startsWith("testpods-");
    assertThat(
            cluster
                .getClient()
                .namespaces()
                .withName(cluster.getDefaultNamespace().getName())
                .get())
        .as("default namespace should exist in the cluster")
        .isNotNull();
  }

  private static void assertDashboardUrl(MinikubeCluster cluster) {
    String namespace = cluster.getDefaultNamespace().getName();
    assertThat(cluster.getDashboardUrl())
        .as("cluster should expose a clickable minikube dashboard URL")
        .hasValueSatisfying(
            url -> {
              assertThat(url).startsWith("http://127.0.0.1:");
              assertThat(url)
                  .contains(
                      "/api/v1/namespaces/kubernetes-dashboard/"
                          + "services/kubernetes-dashboard/proxy/");
              assertThat(url).contains("#/workloads?namespace=" + namespace);
            });
  }

  /** Poll {@code condition} every {@code interval} for up to {@code timeout}. */
  private static boolean pollUntil(
      Duration timeout, Duration interval, java.util.function.BooleanSupplier condition) {
    long deadline = System.nanoTime() + timeout.toNanos();
    while (System.nanoTime() < deadline) {
      if (condition.getAsBoolean()) {
        return true;
      }
      try {
        Thread.sleep(interval.toMillis());
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
        return false;
      }
    }
    return condition.getAsBoolean();
  }

  /** Heuristic: did {@code minikit status} indicate the profile is up and reachable? */
  private static boolean statusIndicatesRunning(Shell.Result result) {
    if (result.exitCode() != 0) {
      return false;
    }
    String haystack = (result.stdout() + "\n" + result.stderr()).toLowerCase(java.util.Locale.ROOT);
    // Match common ways minikit / minikube report a healthy profile.
    return haystack.contains("running")
        || haystack.contains("\"status\":\"running\"")
        || haystack.contains("host: running");
  }

  /** Heuristic: did {@code minikit status} indicate the profile is gone / not running? */
  private static boolean statusIndicatesGone(Shell.Result result) {
    String haystack = (result.stdout() + "\n" + result.stderr()).toLowerCase(java.util.Locale.ROOT);
    if (haystack.contains("not found")
        || haystack.contains("does not exist")
        || haystack.contains("no such")
        || haystack.contains("nonexistent")) {
      return true;
    }
    if (haystack.contains("stopped") || haystack.contains("not running")) {
      return true;
    }
    // Many CLI variants exit non-zero when the profile doesn't exist; treat that as "gone" unless
    // the output explicitly says "running".
    return result.exitCode() != 0 && !haystack.contains("running");
  }

  // ---------------------------------------------------------------------------
  // Shell helper — test-only direct ProcessBuilder usage.
  // ---------------------------------------------------------------------------

  /**
   * Tiny ProcessBuilder wrapper used by integration tests to drive {@code minikit} / {@code
   * kubectl} from outside the production CLI wrappers.
   */
  static final class Shell {

    /** Outcome of a {@link Shell#run} invocation. */
    record Result(int exitCode, String stdout, String stderr) {
      @Override
      public String toString() {
        return "exit=" + exitCode + ", stdout=[" + stdout.strip() + "], stderr=[" + stderr.strip()
            + "]";
      }
    }

    private Shell() {}

    /** Run a process and capture stdout/stderr. Times out by destroying the process. */
    static Result run(Duration timeout, String... argv) {
      return runIn(null, timeout, argv);
    }

    /** Run a process with an explicit working directory; {@code null} means inherit. */
    static Result runIn(Path workingDir, Duration timeout, String... argv) {
      List<String> command = Arrays.asList(argv);
      Process process;
      try {
        if (workingDir != null) {
          Files.createDirectories(workingDir);
        }
        ProcessBuilder pb = new ProcessBuilder(command);
        if (workingDir != null) {
          pb.directory(workingDir.toFile());
        }
        process = pb.start();
      } catch (IOException e) {
        return new Result(-1, "", "failed to launch " + command + ": " + e.getMessage());
      }
      String stdout;
      String stderr;
      try {
        boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (!finished) {
          process.destroyForcibly();
          return new Result(
              -1, drain(process, true), "timeout after " + timeout + " running " + command);
        }
        stdout = drain(process, true);
        stderr = drain(process, false);
        return new Result(process.exitValue(), stdout, stderr);
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
        process.destroyForcibly();
        return new Result(-1, "", "interrupted while running " + command);
      }
    }

    /** Run a process and ignore the result. Used for best-effort setup / cleanup. */
    static void runQuietly(Duration timeout, String... argv) {
      runQuietlyIn(null, timeout, argv);
    }

    /** Run a process in a specific working directory and ignore the result. */
    static void runQuietlyIn(Path workingDir, Duration timeout, String... argv) {
      try {
        runIn(workingDir, timeout, argv);
      } catch (RuntimeException ignored) {
        // Best-effort.
      }
    }

    private static String drain(Process process, boolean stdout) {
      try (BufferedReader reader =
          new BufferedReader(
              new InputStreamReader(
                  stdout ? process.getInputStream() : process.getErrorStream(),
                  StandardCharsets.UTF_8))) {
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
          sb.append(line).append('\n');
        }
        return sb.toString();
      } catch (IOException e) {
        return "";
      }
    }
  }
}
