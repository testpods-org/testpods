package org.testpods.core.provisioning;

import org.testpods.core.pods.Pod;
import org.testpods.junit.TestPodGroup;

public class GroupBuilder {


    public static GroupBuilder newGroup() {
        return new GroupBuilder();
    }

    public GroupBuilder add(Pod<?> testPod) {
        return null;
    }

    public TestPodGroup build() {
        return null;
    }

    public GroupBuilder dependsOn(TestPodGroup infrastructureGroup) {
        return null;
    }
}
