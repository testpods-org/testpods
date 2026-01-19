package org.testpods.junit;

import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.*;


@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(TestPodsExtension.class)
@Inherited
public @interface TestPods {
}
