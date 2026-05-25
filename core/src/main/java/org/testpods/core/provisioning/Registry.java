package org.testpods.core.provisioning;

import lombok.Getter;
import lombok.Setter;
import org.testpods.core.PropertyContext;
import org.testpods.core.pods.TestPodDefaults;
import org.testpods.core.cluster.K8sCluster;
import org.testpods.core.cluster.ProfileLifecyclePolicy;
import org.testpods.core.pods.Pod;
import org.testpods.junit.TestPodGroup;

import java.util.*;

/**
 * Repository for managing test pods and their lifecycle and state.
 *
 */
@lombok.extern.slf4j.Slf4j
public class Registry {

    @Setter
    @Getter
    K8sCluster cluster;

    @Getter
    final PropertyContext propertyContext = new PropertyContext();

    ProfileLifecyclePolicy profileLifecyclePolicy = ProfileLifecyclePolicy.DESTROY_ON_CLOSE;

    Map<String, Pod<?>> podsByName = new LinkedHashMap<>();
    Set<TestPodGroup> groups = new HashSet<>();

    Set<String> registeredNamespaceNames = new LinkedHashSet<>();
    List<Pod<?>> startedPods = new ArrayList<>();

    //TODO assert that all declarations have matching initializations from other classes.
    Map<String, FieldDeclaration> testPodDeclarationsByName = new LinkedHashMap<>();
    Map<String, FieldInitialization> testPodInitializationsByName = new LinkedHashMap<>();

    public void registerNamespace(String name) {
        registeredNamespaceNames.add(name);
    }

    public void setProfileLifecyclePolicy(ProfileLifecyclePolicy profileLifecyclePolicy) {
        this.profileLifecyclePolicy = profileLifecyclePolicy;
    }

    public void addTestPodDeclarations(Map<String, FieldDeclaration> testPodDeclarations) {
        this.testPodDeclarationsByName.putAll(testPodDeclarations);
    }

    public void addTestPodInitializations(Map<String, FieldInitialization> testPodInitializations) {
        this.testPodInitializationsByName.putAll(testPodInitializations);
    }

    // Validate that TestPodsRepository is properly configured
    // For each TestPod field declared in the test class, check if it is initialized in the TestPodsRepository
    public void validateConfiguration() {
        //TODO
    }

    public void ensureClusterIsReady() {
        ensureClusterIsReady(profileLifecyclePolicy);
    }

    public void ensureClusterIsReady(ProfileLifecyclePolicy profileLifecyclePolicy) {
        if (cluster == null) {
            cluster = K8sCluster.discover(profileLifecyclePolicy);
        }
        TestPodDefaults.setClusterSupplier(() -> cluster);
    }

    public void ensureNamespaceIsActive() {
        ensureClusterIsReady();
        if (cluster.getDefaultNamespace() == null) {
            cluster.createNamespace();
        }
        if (cluster.getDefaultNamespace() != null) {
            TestPodDefaults.setSharedNamespace(cluster.getDefaultNamespace());
            registeredNamespaceNames.add(cluster.getDefaultNamespace().getName());
        }
    }

    public void addPod(String name, Pod<?> pod) {
        podsByName.put(name, pod);
    }

    public Map<String, Pod<?>> getPodsByName() {
        return Collections.unmodifiableMap(podsByName);
    }

    public void provisionTestPods() {
      ensureNamespaceIsActive();

      try {
        for (FieldInitialization initialization : orderInitializationsForStart()) {
          Object instance = initialization.instance();
          if (!(instance instanceof Pod<?> pod)) {
            log.warn(
                "@TestPod field {} in {} is not a Pod instance; skipping",
                initialization.fieldName(),
                initialization.declaringClass().getSimpleName());
            continue;
          }

          String registryName = initialization.podName();
          podsByName.put(registryName, pod);
          if (pod.getName() != null) {
            podsByName.put(pod.getName(), pod);
          }
          if (pod.getCluster() == null) {
            pod.inCluster(cluster);
          }
          pod.withPropertyContext(propertyContext);

          log.info("Starting TestPod '{}' using {}", registryName, pod.getClass().getSimpleName());
          pod.start();
          pod.publishProperties(propertyContext);
          startedPods.add(pod);
          log.info(
              "Started TestPod '{}' in namespace '{}' at {}:{}",
              registryName,
              pod.getNamespace().getName(),
              pod.getExternalHost(),
              pod.getExternalPort());
        }
      } catch (RuntimeException e) {
        tearDown();
        throw e;
      }
    }

    List<FieldInitialization> orderInitializationsForStart() {
      List<FieldInitialization> ordered = new ArrayList<>();
      Map<FieldInitialization, VisitState> states = new IdentityHashMap<>();
      List<FieldInitialization> initializations = new ArrayList<>(testPodInitializationsByName.values());

      for (FieldInitialization initialization : initializations) {
        visit(initialization, initializations, states, ordered);
      }
      return ordered;
    }

    private void visit(
        FieldInitialization initialization,
        List<FieldInitialization> initializations,
        Map<FieldInitialization, VisitState> states,
        List<FieldInitialization> ordered) {
      VisitState state = states.get(initialization);
      if (state == VisitState.VISITED) {
        return;
      }
      if (state == VisitState.VISITING) {
        throw new IllegalStateException(
            "Cyclic TestPod property dependency involving '" + initialization.podName() + "'");
      }

      states.put(initialization, VisitState.VISITING);
      for (FieldInitialization dependency : dependenciesOf(initialization, initializations)) {
        visit(dependency, initializations, states, ordered);
      }
      states.put(initialization, VisitState.VISITED);
      ordered.add(initialization);
    }

    private List<FieldInitialization> dependenciesOf(
        FieldInitialization initialization, List<FieldInitialization> initializations) {
      if (!(initialization.instance() instanceof Pod<?> pod)) {
        return List.of();
      }

      List<FieldInitialization> dependencies = new ArrayList<>();
      for (String propertyKey : pod.getReferencedProperties()) {
        String podName = propertyOwner(propertyKey);
        if (podName == null) {
          continue;
        }
        FieldInitialization dependency = findInitializationForPodName(podName, initializations);
        if (dependency != null && dependency != initialization && !dependencies.contains(dependency)) {
          dependencies.add(dependency);
        }
      }
      return dependencies;
    }

    private String propertyOwner(String propertyKey) {
      int separator = propertyKey.indexOf('.');
      if (separator <= 0) {
        return null;
      }
      return propertyKey.substring(0, separator);
    }

    private FieldInitialization findInitializationForPodName(
        String podName, List<FieldInitialization> initializations) {
      for (FieldInitialization initialization : initializations) {
        if (initialization.instance() instanceof Pod<?> pod && podName.equals(pod.getName())) {
          return initialization;
        }
        if (podName.equals(initialization.podName())) {
          return initialization;
        }
      }
      return null;
    }

    private enum VisitState {
      VISITING,
      VISITED
    }

    /**
     * Tear down all managed test pods and release resources.
     *
     * <p>Stub: full implementation tracked separately. Present so the JUnit extension's
     * afterAll() callback compiles.
     */
    public void tearDown() {
      for (int i = startedPods.size() - 1; i >= 0; i--) {
        Pod<?> pod = startedPods.get(i);
        try {
          pod.stop();
        } catch (Exception e) {
          log.warn("Registry teardown: failed to stop pod {}: {}", pod.getName(), e.getMessage());
        }
      }
      startedPods.clear();

      if (cluster == null) return;
      for (String name : new java.util.ArrayList<>(registeredNamespaceNames)) {
        try {
          cluster.deleteNamespace(name);
          registeredNamespaceNames.remove(name);
        } catch (Exception e) {
          log.warn("Registry teardown: failed to delete namespace {}: {}", name, e.getMessage());
        }
      }
    }
}
