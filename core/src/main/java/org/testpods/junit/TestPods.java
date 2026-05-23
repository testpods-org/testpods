package org.testpods.junit;

import java.lang.annotation.*;
import org.junit.jupiter.api.extension.ExtendWith;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(TestPodsExtension.class)
@Inherited
public @interface TestPods {

    /**
     * Whether TestPods should delete the test namespace and stop provisioned pods when the test
     * class finishes.
     *
     * <p>Set this to {@code false} when debugging an integration test and you want to inspect the
     * provisioned Kubernetes resources after the JVM finishes. In preserve mode the extension leaves
     * the registered cluster open as well, because {@code K8sCluster.close()} may delete owned
     * namespaces or destroy a TestPods-started minikube profile.
     */
    boolean deleteNamespaceAfterTests() default true;

    Class<?>[] testpodsProviders() default {};
}
