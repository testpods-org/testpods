package org.testpods.junit;

import java.lang.annotation.*;
import org.junit.jupiter.api.extension.ExtendWith;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(TestPodsExtension.class)
@Inherited
public @interface TestPods {}
