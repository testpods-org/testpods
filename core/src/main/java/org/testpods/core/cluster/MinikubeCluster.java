package org.testpods.core.cluster;

import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.KubernetesClientException;
import java.io.Closeable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.testpods.core.cluster.minikube.MinikubeImageLoadTarget;

/**
 * K8sCluster implementation backed by a local {@code minikit} / {@code minikube} profile.
 *
 * <p>Ownership of the underlying profile is decided at construction time: if the profile was
 * already {@link ProfileStatus#RUNNING} we attach to it and never stop/destroy it on
 * {@link #close()}; if we had to start it, {@link #close()} applies the configured
 * {@link ProfileLifecyclePolicy}.
 */
@Slf4j
public class MinikubeCluster implements K8sCluster, MinikubeImageLoadTarget, Closeable {

  /** Default minikube profile name used by {@link #create()} and {@link #builder()}. */
  static final String DEFAULT_PROFILE_NAME = "testpods";

  /** Maximum time we wait for an in-progress {@link ProfileStatus#STARTING} profile to settle. */
  private static final Duration STARTING_WAIT_TIMEOUT = Duration.ofMinutes(3);

  /** Poll interval while waiting for a starting profile to settle. */
  private static final Duration STARTING_WAIT_INTERVAL = Duration.ofSeconds(2);

  /**
   * Timeout passed to {@link MinikitCli#up(String, Duration)} when this cluster starts the profile.
   */
  private static final Duration UP_TIMEOUT = Duration.ofMinutes(3);

  private static final String DASHBOARD_NAMESPACE = "kubernetes-dashboard";
  private static final String DASHBOARD_SERVICE = "kubernetes-dashboard";
  private static final Duration DASHBOARD_WAIT_TIMEOUT = Duration.ofMinutes(1);
  private static final Duration DASHBOARD_WAIT_INTERVAL = Duration.ofSeconds(1);

  /**
   * Per-profile mutex serialising concurrent {@code MinikubeCluster} construction within this JVM.
   * The real {@code minikit up} command rejects concurrent invocations on the same workspace, so
   * two threads racing on {@link #create()} would otherwise both try to start the profile and one
   * would fail. Holding this lock around the up/status decision lets the second thread observe the
   * now-running profile and attach without starting it.
   */
  private static final Map<String, Object> PROFILE_STARTUP_LOCKS = new ConcurrentHashMap<>();

  private final String profileName;
  private final MinikitCli cli;
  private final boolean ownsProfile;
  private final ProfileLifecyclePolicy policy;
  private final KubernetesClient client;
  private final Map<String, Namespace> namespaces;
  private final ExternalAccessStrategy accessStrategy;
  private Optional<MinikitCli.DashboardProxy> dashboardProxy = Optional.empty();
  private Namespace defaultNamespace;

  private MinikubeCluster(
      String profileName,
      ProfileLifecyclePolicy policy,
      MinikitCli cli,
      ExternalAccessStrategy accessStrategy) {
    this.profileName = profileName;
    this.cli = cli;
    Preflight.verify();

    Object lock = PROFILE_STARTUP_LOCKS.computeIfAbsent(profileName, k -> new Object());
    boolean owns;
    synchronized (lock) {
      ProfileStatus initial = cli.status(profileName);
      if (initial == ProfileStatus.STARTING) {
        // Another process is mid-start; wait it out and let that process own the profile.
        ProfileStatus settled = waitUntilSettled(profileName);
        owns = (settled != ProfileStatus.RUNNING);
        if (owns) {
          cli.up(profileName, UP_TIMEOUT);
        }
      } else if (initial == ProfileStatus.RUNNING) {
        owns = false;
      } else {
        owns = true;
        cli.up(profileName, UP_TIMEOUT);
      }
    }
    this.ownsProfile = owns;

    this.client = createAndPingClient(profileName);
    this.policy = ownsProfile ? policy : ProfileLifecyclePolicy.LEAVE_RUNNING;
    this.namespaces = new HashMap<>();
    this.accessStrategy = accessStrategy;
    Namespace namespace = createNamespace();
    log.info(
        "Created TestPods namespace {} in minikube profile {}", namespace.getName(), profileName);
    this.dashboardProxy = startDashboardProxy(profileName, namespace.getName());
  }

  /**
   * Create a MinikubeCluster on the default {@code testpods} profile with {@code DESTROY_ON_CLOSE}.
   */
  public static MinikubeCluster create() {
    return builder().build();
  }

  /** Create a MinikubeCluster on a specific profile name with default policy and CLI. */
  public static MinikubeCluster withProfileName(String profileName) {
    return builder().profileName(profileName).build();
  }

  /**
   * @deprecated Use {@link #withProfileName(String)}. This value is a minikube profile name.
   */
  @Deprecated
  public static MinikubeCluster withNodeName(String nodeName) {
    return withProfileName(nodeName);
  }

  /**
   * @deprecated Use {@link #withProfileName(String)}.
   */
  @Deprecated
  public static MinikubeCluster withProfile(String profile) {
    return withProfileName(profile);
  }

  /** Builder for non-default profile names, policies, or a custom {@link MinikitCli}. */
  public static Builder builder() {
    return new Builder();
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
      // TODO handle namespace not found
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

  @Override
  public Namespace attachNamespace(String name) {
    io.fabric8.kubernetes.api.model.Namespace cn;
    try {
      cn = client.namespaces().withName(name).get();
    } catch (KubernetesClientException kce) {
      throw new ClusterException(kce);
    }
    if (cn == null) {
      throw new ClusterException(
          "namespace " + name + " does not exist in cluster — cannot attach");
    }
    Namespace ns = Namespace.external(name, cn);
    namespaces.put(name, ns);
    return ns;
  }

  @Override
  public void deleteNamespace(String name) {
    Namespace ns = namespaces.get(name);
    if (ns == null || !ns.isOwned()) {
      throw new ClusterException(
          "refusing to delete namespace " + name + " — not created by this MinikubeCluster");
    }
    try {
      client.namespaces().withName(name).delete();
    } catch (KubernetesClientException kce) {
      throw new ClusterException(kce);
    }
    namespaces.remove(name);
    if (defaultNamespace != null && name.equals(defaultNamespace.getName())) {
      defaultNamespace = namespaces.values().stream().findFirst().orElse(null);
      if (defaultNamespace != null) {
        logDashboardUrlForNamespace(defaultNamespace.getName());
      }
    }
  }

  private Namespace createNamespaceInCluster(Optional<String> optionalName) {
    String name = optionalName.orElseGet(NamespaceNaming::generate);
    try {
      var existing = client.namespaces().withName(name).get();
      if (existing != null) {
        throw new ClusterException(
            "namespace "
                + name
                + " already exists in cluster — use attachNamespace to use an external one");
      }
      var clusterNamespace =
          new NamespaceBuilder().withNewMetadata().withName(name).endMetadata().build();
      clusterNamespace = client.namespaces().resource(clusterNamespace).create();

      Namespace namespace = Namespace.owned(name, clusterNamespace);
      namespaces.put(name, namespace);
      defaultNamespace = namespace;
      logDashboardUrlForNamespace(name);
      return namespace;
    } catch (KubernetesClientException kce) {
      throw new ClusterException(kce);
    }
  }

  /** Returns the minikube profile name backing this cluster. */
  public String getProfileName() {
    return profileName;
  }

  /**
   * @deprecated Use {@link #getProfileName()}. This value is a minikube profile name.
   */
  @Deprecated
  public String getNodeName() {
    return getProfileName();
  }

  /**
   * @deprecated Use {@link #getProfileName()}.
   */
  @Deprecated
  public String getProfile() {
    return getProfileName();
  }

  /** True iff this cluster started the profile and is therefore responsible for stopping it. */
  public boolean ownsProfile() {
    return ownsProfile;
  }

  /**
   * @deprecated Use {@link #ownsProfile()}.
   */
  @Deprecated
  public boolean ownsNode() {
    return ownsProfile();
  }

  /** Returns the local dashboard URL when TestPods was able to start a dashboard proxy. */
  public Optional<String> getDashboardUrl() {
    if (defaultNamespace == null) {
      return Optional.empty();
    }
    return dashboardProxy.map(proxy -> dashboardUrlForNamespace(proxy, defaultNamespace.getName()));
  }

  @Override
  public void close() {
    // 1. Best-effort delete every namespace this cluster owns.
    for (Namespace ns : new ArrayList<>(namespaces.values())) {
      if (ns.isOwned()) {
        try {
          deleteNamespace(ns.getName());
        } catch (Exception e) {
          log.warn("Failed to delete owned namespace {} during close: {}", ns.getName(),
              e.getMessage());
        }
      }
    }

    // 2. Close the Fabric8 client.
    if (client != null) {
      try {
        client.close();
      } catch (Exception e) {
        log.warn(
            "Failed to close Kubernetes client for profile {}: {}", profileName, e.getMessage());
      }
    }

    // 3. Stop the local dashboard proxy, if dashboard setup succeeded.
    dashboardProxy.ifPresent(
        proxy -> {
          try {
            proxy.close();
          } catch (Exception e) {
            log.warn(
                "Failed to close dashboard proxy for profile {}: {}",
                profileName,
                e.getMessage());
          }
        });

    // 4. Apply the lifecycle policy. Failures here are logged, never rethrown — close() must not
    //    throw.
    try {
      switch (policy) {
        case DESTROY_ON_CLOSE -> cli.destroy(profileName);
        case STOP_ON_CLOSE -> cli.down(profileName);
        case LEAVE_RUNNING -> { /* no-op */ }
      }
    } catch (ClusterException e) {
      log.warn(
          "Lifecycle policy {} failed for profile {}: {}", policy, profileName, e.getMessage());
    }
  }

  private Optional<MinikitCli.DashboardProxy> startDashboardProxy(
      String profileName, String namespaceName) {
    try {
      cli.enableDashboardAddon(profileName);
      waitForDashboardEndpoint();
      MinikitCli.DashboardProxy proxy = cli.startDashboardProxy(profileName);
      String url = dashboardUrlForNamespace(proxy, namespaceName);
      log.info(
          "Minikube dashboard for profile {} namespace {}: {}",
          profileName,
          namespaceName,
          url);
      return Optional.of(proxy);
    } catch (ClusterException e) {
      log.warn(
          "Could not start minikube dashboard proxy for profile {}: {}",
          profileName,
          e.getMessage());
      return Optional.empty();
    }
  }

  private void logDashboardUrlForNamespace(String namespaceName) {
    dashboardProxy.ifPresent(
        proxy ->
            log.info(
                "Minikube dashboard for profile {} namespace {}: {}",
                profileName,
                namespaceName,
                dashboardUrlForNamespace(proxy, namespaceName)));
  }

  private static String dashboardUrlForNamespace(
      MinikitCli.DashboardProxy proxy, String namespaceName) {
    return proxy.baseUrl() + "#/workloads?namespace=" + namespaceName;
  }

  private void waitForDashboardEndpoint() {
    long deadline = System.nanoTime() + DASHBOARD_WAIT_TIMEOUT.toNanos();
    while (System.nanoTime() < deadline) {
      try {
        var endpoints =
            client
                .endpoints()
                .inNamespace(DASHBOARD_NAMESPACE)
                .withName(DASHBOARD_SERVICE)
                .get();
        if (hasReadyEndpoint(endpoints)) {
          return;
        }
      } catch (KubernetesClientException e) {
        log.debug("Dashboard endpoint not ready yet: {}", e.getMessage());
      }

      try {
        Thread.sleep(DASHBOARD_WAIT_INTERVAL.toMillis());
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new ClusterException("Interrupted while waiting for minikube dashboard endpoint", e);
      }
    }
    throw new ClusterException(
        "Dashboard service "
            + DASHBOARD_NAMESPACE
            + "/"
            + DASHBOARD_SERVICE
            + " has no ready endpoints after "
            + DASHBOARD_WAIT_TIMEOUT);
  }

  private static boolean hasReadyEndpoint(io.fabric8.kubernetes.api.model.Endpoints endpoints) {
    if (endpoints == null || endpoints.getSubsets() == null) {
      return false;
    }
    return endpoints.getSubsets().stream()
        .anyMatch(
            subset ->
                subset.getAddresses() != null
                    && !subset.getAddresses().isEmpty()
                    && subset.getPorts() != null
                    && !subset.getPorts().isEmpty());
  }

  /**
   * Poll {@link MinikitCli#status(String)} every {@link #STARTING_WAIT_INTERVAL} until the status
   * leaves {@link ProfileStatus#STARTING} or {@link #STARTING_WAIT_TIMEOUT} elapses.
   */
  private ProfileStatus waitUntilSettled(String profileName) {
    long start = System.nanoTime();
    long timeoutNanos = STARTING_WAIT_TIMEOUT.toNanos();
    ProfileStatus last = ProfileStatus.STARTING;
    while (System.nanoTime() - start < timeoutNanos) {
      last = cli.status(profileName);
      if (last != ProfileStatus.STARTING) {
        return last;
      }
      try {
        Thread.sleep(STARTING_WAIT_INTERVAL.toMillis());
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
        throw new ClusterException(
            "Interrupted while waiting for profile " + profileName + " to leave STARTING", ie);
      }
    }
    Duration elapsed = Duration.ofNanos(System.nanoTime() - start);
    throw new ClusterException(
        "Profile "
            + profileName
            + " still STARTING after "
            + elapsed
            + " (last status: "
            + last
            + ")");
  }

  /**
   * Build a Fabric8 client for {@code profileName} and confirm reachability via a cheap
   * {@code namespaces().list()}. Retries once with a 2-second pause before giving up.
   */
  private KubernetesClient createAndPingClient(String profileName) {
    Throwable lastFailure = null;
    for (int attempt = 1; attempt <= 2; attempt++) {
      Config config = Config.autoConfigure(profileName);
      KubernetesClient candidate = new KubernetesClientBuilder().withConfig(config).build();
      try {
        candidate.namespaces().list();
        return candidate;
      } catch (Exception e) {
        lastFailure = e;
        try {
          candidate.close();
        } catch (Exception closeEx) {
          // Ignore — we're already in the error path.
        }
        if (attempt == 1) {
          try {
            Thread.sleep(Duration.ofSeconds(2).toMillis());
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new ClusterException(
                "Interrupted while waiting to retry Kubernetes API ping for profile " + profileName,
                ie);
          }
        }
      }
    }
    throw new ClusterException(
        "Kubernetes API server unreachable for profile "
            + profileName
            + "; run `kubectl cluster-info` to diagnose",
        lastFailure);
  }

  /** Builder for {@link MinikubeCluster}. */
  public static final class Builder {
    private String profileName = DEFAULT_PROFILE_NAME;
    private ProfileLifecyclePolicy policy = ProfileLifecyclePolicy.DESTROY_ON_CLOSE;
    private MinikitCli cli = new MinikitCli();
    private ExternalAccessStrategy accessStrategy;

    private Builder() {}

    public Builder profileName(String profileName) {
      this.profileName = profileName;
      return this;
    }

    /**
     * @deprecated Use {@link #profileName(String)}. This value is a minikube profile name.
     */
    @Deprecated
    public Builder nodeName(String nodeName) {
      return profileName(nodeName);
    }

    public Builder policy(ProfileLifecyclePolicy policy) {
      this.policy = policy;
      return this;
    }

    /**
     * @deprecated Use {@link #policy(ProfileLifecyclePolicy)}.
     */
    @Deprecated
    public Builder policy(NodeLifecyclePolicy policy) {
      this.policy = ProfileLifecyclePolicy.valueOf(policy.name());
      return this;
    }

    Builder cli(MinikitCli cli) {
      this.cli = cli;
      return this;
    }

    /** Use a specific strategy for pod endpoints reachable from test code. */
    public Builder accessStrategy(ExternalAccessStrategy accessStrategy) {
      this.accessStrategy = accessStrategy;
      return this;
    }

    /**
     * Expose pod endpoints on localhost with external {@code kubectl port-forward} processes.
     *
     * <p>The forwarding processes are not Fabric8 {@code LocalPortForward}s and do not run inside
     * the test JVM, so they keep serving traffic while the JVM is suspended in a debugger.
     */
    public Builder localhostPortForwardAccess() {
      this.accessStrategy = null;
      return this;
    }

    public MinikubeCluster build() {
      ExternalAccessStrategy resolvedAccessStrategy =
          accessStrategy != null
              ? accessStrategy
              : ExternalAccessStrategy.localhostPortForward(profileName);
      return new MinikubeCluster(profileName, policy, cli, resolvedAccessStrategy);
    }
  }
}
