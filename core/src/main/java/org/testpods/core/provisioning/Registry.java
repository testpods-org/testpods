package org.testpods.core.provisioning;

import lombok.Getter;
import lombok.Setter;
import org.testpods.core.cluster.K8sCluster;
import org.testpods.core.pods.Pod;
import org.testpods.junit.TestPodGroup;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Repository for managing test pods and their lifecycle and state.
 *
 */
public class Registry {

    @Setter
    @Getter
    K8sCluster cluster;

    Map<String, Pod<?>> testPodsByName = new HashMap<>();
    Set<TestPodGroup> groups = new HashSet<>();

    //TODO asser that all declarations have matching initializations from other classes.
    Map<String, FieldDeclaration> testPodDeclarationsByName = new HashMap<>();
    Map<String, FieldInitialization> testPodInitializationsByName = new HashMap<>();

    public void addTestPodDeclarations(Map<String, FieldDeclaration> testPodDeclarations) {
        this.testPodDeclarationsByName.putAll(testPodDeclarations);
    }

    public void addTestPodInitializations(Map<String, FieldInitialization> testPodInitializations) {
        this.testPodInitializationsByName.putAll(testPodInitializations);
    }

    // validate that TestPodsRepository is properly configured
    // For each TestPod field declared in the test class, check if it is initialized in the TestPodsRepository
    public void validateConfiguration() {

    }

    /**
     * Tear down all managed test pods and release resources.
     *
     * <p>Stub: full implementation tracked separately. Present so the JUnit extension's
     * afterAll() callback compiles.
     */
    public void tearDown() {
    }
}
