package org.testpods.core.pods.external.kafka;

import java.time.Duration;
import org.testpods.core.ExecResult;
import org.testpods.core.pods.Pod;
import org.testpods.core.wait.WaitStrategy;

/** Wait strategy for single-node Kafka pods. */
public class KafkaWaitStrategy implements WaitStrategy {

  private Duration timeout = Duration.ofMinutes(2);
  private Duration pollInterval = Duration.ofMillis(500);

  @Override
  public KafkaWaitStrategy withTimeout(Duration timeout) {
    KafkaWaitStrategy copy = new KafkaWaitStrategy();
    copy.timeout = timeout;
    copy.pollInterval = this.pollInterval;
    return copy;
  }

  @Override
  public KafkaWaitStrategy withPollInterval(Duration interval) {
    KafkaWaitStrategy copy = new KafkaWaitStrategy();
    copy.timeout = this.timeout;
    copy.pollInterval = interval;
    return copy;
  }

  @Override
  public void waitUntilReady(Pod<?> testPod) {
    if (!(testPod instanceof KafkaPod)) {
      throw new IllegalArgumentException("KafkaWaitStrategy requires KafkaPod");
    }

    KafkaPod kafka = (KafkaPod) testPod;
    long deadline = System.currentTimeMillis() + timeout.toMillis();

    waitForPodReady(kafka, deadline);
    waitForBrokerApi(kafka, deadline);
  }

  private void waitForPodReady(KafkaPod kafka, long deadline) {
    while (System.currentTimeMillis() < deadline) {
      if (kafka.isReady()) {
        return;
      }
      sleep(pollInterval);
    }
    throw new IllegalStateException("Timed out waiting for Kafka pod to be ready");
  }

  private void waitForBrokerApi(KafkaPod kafka, long deadline) {
    while (System.currentTimeMillis() < deadline) {
      ExecResult result =
          kafka.execInMainContainer(
              new String[] {
                "sh",
                "-c",
                KafkaPod.kafkaCliCommand(),
                "kafka-topics",
                "--bootstrap-server",
                "localhost:" + KafkaPod.INTERNAL_LISTENER_PORT,
                "--list"
              });
      if (result.exitCode() == 0) {
        return;
      }
      sleep(pollInterval);
    }
    throw new IllegalStateException("Timed out waiting for Kafka broker API");
  }

  private void sleep(Duration duration) {
    try {
      Thread.sleep(duration.toMillis());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while waiting", e);
    }
  }
}
