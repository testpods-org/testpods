package org.testpods.junit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RegisterCluster {

    String value() default "";
    String profile() default "";

    /**
     * Whether TestPods should delete the Minikube profile when the test class finishes.
     */
    boolean deleteProfileAfterTests() default true;

    /**
     * @deprecated Use {@link #deleteProfileAfterTests()}.
     */
    @Deprecated
    boolean stopAfterTest() default false;

    boolean deleteNamespaceAfterTest() default true;

}
