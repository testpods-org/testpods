package org.testpods.core.provisioning;

import lombok.Getter;
import lombok.Setter;
import org.testpods.core.pods.TestPodDefaults;
import org.testpods.core.cluster.K8sCluster;
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

    Map<String, Pod<?>> podsByName = new HashMap<>();
    Set<TestPodGroup> groups = new HashSet<>();

    Set<String> registeredNamespaceNames = new LinkedHashSet<>();
    List<Pod<?>> startedPods = new ArrayList<>();

    //TODO assert that all declarations have matching initializations from other classes.
    Map<String, FieldDeclaration> testPodDeclarationsByName = new HashMap<>();
    Map<String, FieldInitialization> testPodInitializationsByName = new HashMap<>();

    public void registerNamespace(String name) {
        registeredNamespaceNames.add(name);
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

    public void addPod(String name, Pod<?> pod) {
        podsByName.put(name, pod);
    }

    public Map<String, Pod<?>> getPodsByName() {
        return Collections.unmodifiableMap(podsByName);
    }

    public void provisionTestPods() {
      if (cluster == null) {
        throw new IllegalStateException("A K8sCluster is required before provisioning TestPods");
      }

      TestPodDefaults.setClusterSupplier(() -> cluster);
      if (cluster.getDefaultNamespace() != null) {
        TestPodDefaults.setSharedNamespace(cluster.getDefaultNamespace());
        registeredNamespaceNames.add(cluster.getDefaultNamespace().getName());
      }

      try {
        for (FieldInitialization initialization : testPodInitializationsByName.values()) {
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
          if (pod.getCluster() == null) {
            pod.inCluster(cluster);
          }

          log.info("Starting TestPod '{}' using {}", registryName, pod.getClass().getSimpleName());
          pod.start();
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
