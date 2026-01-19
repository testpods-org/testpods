package org.testpods.core.pods.external.kafka;

import io.fabric8.kubernetes.api.model.Container;
import org.testpods.core.pods.PropertyContext;
import org.testpods.core.pods.StatefulSetPod;
import org.testpods.core.pods.TestNamespace;
import org.testpods.core.pods.TestPod;
import org.testpods.core.wait.WaitStrategy;

import java.util.Map;

public class KafkaPod extends StatefulSetPod<KafkaPod> {
    @Override
    public KafkaPod withName(String name) {
        return null;
    }

    @Override
    public KafkaPod inNamespace(TestNamespace namespace) {
        return null;
    }

    @Override
    public KafkaPod withLabels(Map<String, String> labels) {
        return null;
    }

    @Override
    public KafkaPod withAnnotations(Map<String, String> annotations) {
        return null;
    }

    @Override
    public KafkaPod waitingFor(WaitStrategy strategy) {
        return null;
    }

    @Override
    public void start() {

    }

    @Override
    public void stop() {

    }

    @Override
    public boolean isRunning() {
        return false;
    }

    @Override
    public boolean isReady() {
        return false;
    }

    @Override
    public String getInternalHost() {
        return "";
    }

    @Override
    public int getInternalPort() {
        return 0;
    }

    @Override
    public String getExternalHost() {
        return "";
    }

    @Override
    public int getExternalPort() {
        return 0;
    }

    @Override
    protected Container buildMainContainer() {
        return null;
    }

    @Override
    public void publishProperties(PropertyContext ctx) {

    }

//    public KafkaPod withKraftMode(boolean kraft);  // No ZK dependency
//    public KafkaPod withTopics(String... topics);
//    public KafkaPod withPartitions(int partitions);
//
//    public String getBootstrapServers();           // External
//    public String getInternalBootstrapServers();   // Internal
}