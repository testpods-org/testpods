package org.testpods.core.provisioning;

import lombok.Getter;
import lombok.Setter;
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

    /**
     * Tear down all managed test pods and release resources.
     *
     * <p>Stub: full implementation tracked separately. Present so the JUnit extension's
     * afterAll() callback compiles.
     */
    public void tearDown() {
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
