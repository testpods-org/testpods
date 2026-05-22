package org.testpods.core.cluster;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;

/**
 * Reusable JUnit 5 fixture for tests that need a {@link MinikubeCluster}.
 *
 * <p>Two scopes are supported:
 *
 * <ul>
 *   <li>{@link PerClass} — registered via {@code @ExtendWith(MinikitTestFixture.PerClass.class)};
 *       brings up a {@link MinikubeCluster} in {@code @BeforeAll} and tears it down in
 *       {@code @AfterAll}.
 *   <li>{@link PerSuite} — registered via the JUnit Platform's {@link java.util.ServiceLoader}
 *       (see {@code META-INF/services/org.junit.platform.launcher.LauncherSessionListener});
 *       brings up a single {@link MinikubeCluster} for the entire test session.
 * </ul>
 */
public class MinikitTestFixture {

  private MinikitTestFixture() {}

  /** Per-class scope: brings up a MinikubeCluster in {@code @BeforeAll}, destroys it in {@code @AfterAll}. */
  @Slf4j
  public static class PerClass implements BeforeAllCallback, AfterAllCallback {

    private static final ExtensionContext.Namespace NS =
        ExtensionContext.Namespace.create(MinikitTestFixture.class, "PerClass");

    public static final String CLUSTER_KEY = "cluster";

    @Override
    public void beforeAll(ExtensionContext context) {
      MinikubeCluster cluster = MinikubeCluster.create();
      context.getStore(NS).put(CLUSTER_KEY, cluster);
    }

    @Override
    public void afterAll(ExtensionContext context) {
      MinikubeCluster cluster = context.getStore(NS).remove(CLUSTER_KEY, MinikubeCluster.class);
      if (cluster != null) {
        try {
          cluster.close();
        } catch (Exception e) {
          log.warn("Failed to close MinikubeCluster in PerClass afterAll: {}", e.getMessage(), e);
        }
      }
    }

    /** Helper for tests to retrieve the shared cluster from the extension store. */
    public static MinikubeCluster cluster(ExtensionContext context) {
      return context.getStore(NS).get(CLUSTER_KEY, MinikubeCluster.class);
    }
  }

  /**
   * Per-suite scope: brings up a single MinikubeCluster for the entire JUnit Platform session.
   *
   * <p>Registered via {@code META-INF/services/org.junit.platform.launcher.LauncherSessionListener},
   * so JUnit instantiates it for every test session. To avoid spinning up a cluster on every
   * {@code mvn test} invocation, the listener is opt-in: it only creates a cluster when the system
   * property {@code testpods.fixture.persuite} is set to {@code true}. Enable it on the command
   * line with {@code -Dtestpods.fixture.persuite=true}.
   */
  @Slf4j
  public static class PerSuite implements LauncherSessionListener {

    /** System property toggling per-suite cluster startup. */
    public static final String ENABLE_PROPERTY = "testpods.fixture.persuite";

    private MinikubeCluster cluster;

    @Override
    public void launcherSessionOpened(LauncherSession session) {
      if (!Boolean.parseBoolean(System.getProperty(ENABLE_PROPERTY, "false"))) {
        return;
      }
      this.cluster = MinikubeCluster.create();
    }

    @Override
    public void launcherSessionClosed(LauncherSession session) {
      if (cluster != null) {
        try {
          cluster.close();
        } catch (Exception e) {
          log.warn("Failed to close MinikubeCluster in PerSuite launcherSessionClosed: {}", e.getMessage(), e);
        }
        cluster = null;
      }
    }

    /** Exposes the cluster for tests that want to read it; {@code null} outside a session. */
    public MinikubeCluster cluster() {
      return cluster;
    }
  }
}
