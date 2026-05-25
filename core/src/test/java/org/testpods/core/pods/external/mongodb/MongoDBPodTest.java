package org.testpods.core.pods.external.mongodb;

import static org.assertj.core.api.Assertions.assertThat;

import io.fabric8.kubernetes.api.model.Container;
import org.junit.jupiter.api.Test;
import org.testpods.core.wait.WaitStrategy;

class MongoDBPodTest {

  static class TestableMongoDBPod extends MongoDBPod {
    Container buildContainerForTest() {
      return buildMainContainer();
    }

    WaitStrategy activeWaitStrategyForTest() {
      return getActiveWaitStrategy();
    }
  }

  @Test
  void buildMainContainerShouldUseShellCompatibleMongoPingProbe() {
    TestableMongoDBPod pod = new TestableMongoDBPod();

    Container container = pod.buildContainerForTest();

    assertThat(container.getReadinessProbe().getExec().getCommand())
        .containsExactly("sh", "-c", MongoDBPod.mongoShellPingCommand());
    assertThat(container.getLivenessProbe().getExec().getCommand())
        .containsExactly("sh", "-c", MongoDBPod.mongoShellPingCommand());
    assertThat(MongoDBPod.mongoShellPingCommand())
        .contains("command -v mongosh || command -v mongo")
        .contains("MONGO_INITDB_ROOT_USERNAME")
        .contains("db.adminCommand('ping')");
  }

  @Test
  void waitingForShouldUseBaseFluentConfiguration() {
    TestableMongoDBPod pod = new TestableMongoDBPod();
    WaitStrategy strategy = WaitStrategy.forCommand("true");

    MongoDBPod result = pod.waitingFor(strategy);

    assertThat(result).isSameAs(pod);
    assertThat(pod.activeWaitStrategyForTest()).isSameAs(strategy);
  }
}
