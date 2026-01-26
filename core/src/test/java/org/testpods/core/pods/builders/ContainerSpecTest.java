package org.testpods.core.pods.builders;

import static org.assertj.core.api.Assertions.*;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerPort;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.VolumeMount;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for ContainerSpec fluent builder.
 *
 * <p>These tests verify the fluent API works correctly and produces valid Fabric8 Container
 * objects. Tests are organized by feature area and serve as executable documentation.
 */
class ContainerSpecTest {

  @Nested
  class BasicContainerBuilding {

    @Test
    void shouldBuildContainerWithNameImageAndPort() {
      Container container =
          new ContainerSpec().withName("my-app").withImage("nginx:latest").withPort(8080).build();

      assertThat(container.getName()).isEqualTo("my-app");
      assertThat(container.getImage()).isEqualTo("nginx:latest");
      assertThat(container.getPorts()).hasSize(1);
      assertThat(container.getPorts().get(0).getContainerPort()).isEqualTo(8080);
    }

    @Test
    void shouldBuildContainerWithMultiplePorts() {
      Container container =
          new ContainerSpec()
              .withName("multi-port")
              .withImage("app:v1")
              .withPort(8080)
              .withPort(8443)
              .withPort(9090)
              .build();

      assertThat(container.getPorts()).hasSize(3);
      assertThat(container.getPorts())
          .extracting(ContainerPort::getContainerPort)
          .containsExactly(8080, 8443, 9090);
    }

    @Test
    void shouldBuildContainerWithNamedPort() {
      Container container =
          new ContainerSpec()
              .withName("web")
              .withImage("nginx:latest")
              .withPort(8080, "http")
              .withPort(8443, "https")
              .build();

      assertThat(container.getPorts()).hasSize(2);
      assertThat(container.getPorts().get(0).getName()).isEqualTo("http");
      assertThat(container.getPorts().get(0).getContainerPort()).isEqualTo(8080);
      assertThat(container.getPorts().get(1).getName()).isEqualTo("https");
      assertThat(container.getPorts().get(1).getContainerPort()).isEqualTo(8443);
    }

    @Test
    void shouldBuildMinimalContainerWithNameAndImageOnly() {
      Container container =
          new ContainerSpec().withName("minimal").withImage("busybox:latest").build();

      assertThat(container.getName()).isEqualTo("minimal");
      assertThat(container.getImage()).isEqualTo("busybox:latest");
      assertThat(container.getPorts()).isEmpty();
      assertThat(container.getEnv()).isEmpty();
    }

    @Test
    void shouldSupportFluentMethodChaining() {
      ContainerSpec spec = new ContainerSpec();

      // All methods should return the same instance for chaining
      assertThat(spec.withName("test")).isSameAs(spec);
      assertThat(spec.withImage("image")).isSameAs(spec);
      assertThat(spec.withPort(80)).isSameAs(spec);
      assertThat(spec.withEnv("KEY", "value")).isSameAs(spec);
    }
  }

  @Nested
  class EnvironmentVariables {

    @Test
    void shouldAddMultipleEnvVars() {
      Container container =
          new ContainerSpec()
              .withName("app")
              .withImage("app:v1")
              .withEnv("DB_HOST", "localhost")
              .withEnv("DB_PORT", "5432")
              .withEnv("DB_NAME", "mydb")
              .build();

      assertThat(container.getEnv()).hasSize(3);
      assertThat(container.getEnv())
          .extracting(EnvVar::getName)
          .containsExactly("DB_HOST", "DB_PORT", "DB_NAME");
      assertThat(container.getEnv())
          .extracting(EnvVar::getValue)
          .containsExactly("localhost", "5432", "mydb");
    }

    @Test
    void shouldPreserveEnvVarInsertionOrder() {
      Container container =
          new ContainerSpec()
              .withName("ordered")
              .withImage("app:v1")
              .withEnv("FIRST", "1")
              .withEnv("SECOND", "2")
              .withEnv("THIRD", "3")
              .withEnv("FOURTH", "4")
              .build();

      List<String> names = container.getEnv().stream().map(EnvVar::getName).toList();

      assertThat(names).containsExactly("FIRST", "SECOND", "THIRD", "FOURTH");
    }

    @Test
    void shouldOverwriteEnvVarWithSameName() {
      Container container =
          new ContainerSpec()
              .withName("overwrite")
              .withImage("app:v1")
              .withEnv("KEY", "original")
              .withEnv("KEY", "updated")
              .build();

      assertThat(container.getEnv()).hasSize(1);
      assertThat(container.getEnv().get(0).getValue()).isEqualTo("updated");
    }

    @Test
    void shouldAddEnvFromConfigMap() {
      Container container =
          new ContainerSpec()
              .withName("configmap-app")
              .withImage("app:v1")
              .withEnvFrom("app-config", "database-url")
              .build();

      assertThat(container.getEnv()).hasSize(1);
      EnvVar envVar = container.getEnv().get(0);
      assertThat(envVar.getName()).isEqualTo("database-url");
      assertThat(envVar.getValue()).isNull();
      assertThat(envVar.getValueFrom()).isNotNull();
      assertThat(envVar.getValueFrom().getConfigMapKeyRef()).isNotNull();
      assertThat(envVar.getValueFrom().getConfigMapKeyRef().getName()).isEqualTo("app-config");
      assertThat(envVar.getValueFrom().getConfigMapKeyRef().getKey()).isEqualTo("database-url");
    }

    @Test
    void shouldAddEnvFromSecret() {
      Container container =
          new ContainerSpec()
              .withName("secret-app")
              .withImage("app:v1")
              .withSecretEnv("DB_PASSWORD", "db-secret", "password")
              .build();

      assertThat(container.getEnv()).hasSize(1);
      EnvVar envVar = container.getEnv().get(0);
      assertThat(envVar.getName()).isEqualTo("DB_PASSWORD");
      assertThat(envVar.getValue()).isNull();
      assertThat(envVar.getValueFrom()).isNotNull();
      assertThat(envVar.getValueFrom().getSecretKeyRef()).isNotNull();
      assertThat(envVar.getValueFrom().getSecretKeyRef().getName()).isEqualTo("db-secret");
      assertThat(envVar.getValueFrom().getSecretKeyRef().getKey()).isEqualTo("password");
    }

    @Test
    void shouldMixDifferentEnvVarTypes() {
      Container container =
          new ContainerSpec()
              .withName("mixed-env")
              .withImage("app:v1")
              .withEnv("PLAIN_VALUE", "hello")
              .withEnvFrom("config-map", "config-key")
              .withSecretEnv("SECRET_VALUE", "my-secret", "secret-key")
              .build();

      assertThat(container.getEnv()).hasSize(3);

      // Plain value
      EnvVar plain = container.getEnv().get(0);
      assertThat(plain.getName()).isEqualTo("PLAIN_VALUE");
      assertThat(plain.getValue()).isEqualTo("hello");

      // ConfigMap reference
      EnvVar configMapRef = container.getEnv().get(1);
      assertThat(configMapRef.getName()).isEqualTo("config-key");
      assertThat(configMapRef.getValueFrom().getConfigMapKeyRef()).isNotNull();

      // Secret reference
      EnvVar secretRef = container.getEnv().get(2);
      assertThat(secretRef.getName()).isEqualTo("SECRET_VALUE");
      assertThat(secretRef.getValueFrom().getSecretKeyRef()).isNotNull();
    }
  }

  @Nested
  class ProbeConfiguration {

    @Test
    void shouldConfigureTcpSocketReadinessProbe() {
      Container container =
          new ContainerSpec()
              .withName("postgres")
              .withImage("postgres:15")
              .withPort(5432)
              .withReadinessProbe(probe -> probe.tcpSocket(5432).initialDelay(5).period(2))
              .build();

      assertThat(container.getReadinessProbe()).isNotNull();
      assertThat(container.getReadinessProbe().getTcpSocket()).isNotNull();
      assertThat(container.getReadinessProbe().getTcpSocket().getPort().getIntVal())
          .isEqualTo(5432);
      assertThat(container.getReadinessProbe().getInitialDelaySeconds()).isEqualTo(5);
      assertThat(container.getReadinessProbe().getPeriodSeconds()).isEqualTo(2);
    }

    @Test
    void shouldConfigureHttpGetLivenessProbe() {
      Container container =
          new ContainerSpec()
              .withName("web")
              .withImage("nginx:latest")
              .withLivenessProbe(
                  probe -> probe.httpGet(8080, "/health").initialDelay(30).period(10).timeout(5))
              .build();

      assertThat(container.getLivenessProbe()).isNotNull();
      assertThat(container.getLivenessProbe().getHttpGet()).isNotNull();
      assertThat(container.getLivenessProbe().getHttpGet().getPort().getIntVal()).isEqualTo(8080);
      assertThat(container.getLivenessProbe().getHttpGet().getPath()).isEqualTo("/health");
      assertThat(container.getLivenessProbe().getHttpGet().getScheme()).isEqualTo("HTTP");
      assertThat(container.getLivenessProbe().getInitialDelaySeconds()).isEqualTo(30);
      assertThat(container.getLivenessProbe().getPeriodSeconds()).isEqualTo(10);
      assertThat(container.getLivenessProbe().getTimeoutSeconds()).isEqualTo(5);
    }

    @Test
    void shouldConfigureStartupProbe() {
      Container container =
          new ContainerSpec()
              .withName("slow-starter")
              .withImage("app:v1")
              .withStartupProbe(
                  probe ->
                      probe
                          .exec("pg_isready", "-U", "postgres")
                          .initialDelay(0)
                          .period(5)
                          .failureThreshold(30))
              .build();

      assertThat(container.getStartupProbe()).isNotNull();
      assertThat(container.getStartupProbe().getExec()).isNotNull();
      assertThat(container.getStartupProbe().getExec().getCommand())
          .containsExactly("pg_isready", "-U", "postgres");
      assertThat(container.getStartupProbe().getInitialDelaySeconds()).isEqualTo(0);
      assertThat(container.getStartupProbe().getPeriodSeconds()).isEqualTo(5);
      assertThat(container.getStartupProbe().getFailureThreshold()).isEqualTo(30);
    }

    @Test
    void shouldConfigureAllThreeProbes() {
      Container container =
          new ContainerSpec()
              .withName("fully-probed")
              .withImage("app:v1")
              .withReadinessProbe(probe -> probe.tcpSocket(8080))
              .withLivenessProbe(probe -> probe.httpGet(8080, "/live"))
              .withStartupProbe(probe -> probe.exec("check-ready"))
              .build();

      assertThat(container.getReadinessProbe()).isNotNull();
      assertThat(container.getLivenessProbe()).isNotNull();
      assertThat(container.getStartupProbe()).isNotNull();
    }

    @Test
    void shouldVerifyProbeTimingParameters() {
      Container container =
          new ContainerSpec()
              .withName("timed")
              .withImage("app:v1")
              .withReadinessProbe(
                  probe ->
                      probe
                          .tcpSocket(8080)
                          .initialDelay(10)
                          .period(5)
                          .timeout(3)
                          .failureThreshold(5)
                          .successThreshold(2))
              .build();

      var probe = container.getReadinessProbe();
      assertThat(probe.getInitialDelaySeconds()).isEqualTo(10);
      assertThat(probe.getPeriodSeconds()).isEqualTo(5);
      assertThat(probe.getTimeoutSeconds()).isEqualTo(3);
      assertThat(probe.getFailureThreshold()).isEqualTo(5);
      assertThat(probe.getSuccessThreshold()).isEqualTo(2);
    }
  }

  @Nested
  class ResourceConfiguration {

    @Test
    void shouldSetResourceRequests() {
      Container container =
          new ContainerSpec()
              .withName("with-resources")
              .withImage("app:v1")
              .withResources("100m", "256Mi")
              .build();

      ResourceRequirements resources = container.getResources();
      assertThat(resources).isNotNull();
      assertThat(resources.getRequests()).containsKey("cpu");
      assertThat(resources.getRequests()).containsKey("memory");
      assertThat(resources.getRequests().get("cpu")).isEqualTo(new Quantity("100m"));
      assertThat(resources.getRequests().get("memory")).isEqualTo(new Quantity("256Mi"));
    }

    @Test
    void shouldSetResourceLimits() {
      Container container =
          new ContainerSpec()
              .withName("with-limits")
              .withImage("app:v1")
              .withResourceLimits("500m", "512Mi")
              .build();

      ResourceRequirements resources = container.getResources();
      assertThat(resources).isNotNull();
      assertThat(resources.getLimits()).containsKey("cpu");
      assertThat(resources.getLimits()).containsKey("memory");
      assertThat(resources.getLimits().get("cpu")).isEqualTo(new Quantity("500m"));
      assertThat(resources.getLimits().get("memory")).isEqualTo(new Quantity("512Mi"));
    }

    @Test
    void shouldSetBothRequestsAndLimits() {
      Container container =
          new ContainerSpec()
              .withName("full-resources")
              .withImage("app:v1")
              .withResources("100m", "256Mi")
              .withResourceLimits("500m", "512Mi")
              .build();

      ResourceRequirements resources = container.getResources();
      assertThat(resources.getRequests().get("cpu")).isEqualTo(new Quantity("100m"));
      assertThat(resources.getRequests().get("memory")).isEqualTo(new Quantity("256Mi"));
      assertThat(resources.getLimits().get("cpu")).isEqualTo(new Quantity("500m"));
      assertThat(resources.getLimits().get("memory")).isEqualTo(new Quantity("512Mi"));
    }
  }

  @Nested
  class VolumeMountConfiguration {

    @Test
    void shouldAddVolumeMount() {
      Container container =
          new ContainerSpec()
              .withName("with-volume")
              .withImage("app:v1")
              .withVolumeMount("data", "/var/data")
              .build();

      assertThat(container.getVolumeMounts()).hasSize(1);
      VolumeMount mount = container.getVolumeMounts().get(0);
      assertThat(mount.getName()).isEqualTo("data");
      assertThat(mount.getMountPath()).isEqualTo("/var/data");
      assertThat(mount.getReadOnly()).isFalse();
    }

    @Test
    void shouldAddReadOnlyVolumeMount() {
      Container container =
          new ContainerSpec()
              .withName("with-readonly")
              .withImage("app:v1")
              .withVolumeMount("config", "/etc/config", true)
              .build();

      assertThat(container.getVolumeMounts()).hasSize(1);
      VolumeMount mount = container.getVolumeMounts().get(0);
      assertThat(mount.getName()).isEqualTo("config");
      assertThat(mount.getMountPath()).isEqualTo("/etc/config");
      assertThat(mount.getReadOnly()).isTrue();
    }

    @Test
    void shouldAddMultipleVolumeMounts() {
      Container container =
          new ContainerSpec()
              .withName("multi-volume")
              .withImage("app:v1")
              .withVolumeMount("data", "/var/data")
              .withVolumeMount("logs", "/var/log")
              .withVolumeMount("config", "/etc/config", true)
              .build();

      assertThat(container.getVolumeMounts()).hasSize(3);
      assertThat(container.getVolumeMounts())
          .extracting(VolumeMount::getName)
          .containsExactly("data", "logs", "config");
    }
  }

  @Nested
  class CommandAndArgs {

    @Test
    void shouldSetCommand() {
      Container container =
          new ContainerSpec()
              .withName("custom-cmd")
              .withImage("busybox")
              .withCommand("/bin/sh", "-c", "echo hello")
              .build();

      assertThat(container.getCommand()).containsExactly("/bin/sh", "-c", "echo hello");
    }

    @Test
    void shouldSetArgs() {
      Container container =
          new ContainerSpec()
              .withName("with-args")
              .withImage("app:v1")
              .withArgs("--config", "/etc/config.yaml", "--verbose")
              .build();

      assertThat(container.getArgs()).containsExactly("--config", "/etc/config.yaml", "--verbose");
    }

    @Test
    void shouldSetBothCommandAndArgs() {
      Container container =
          new ContainerSpec()
              .withName("full-cmd")
              .withImage("app:v1")
              .withCommand("python", "app.py")
              .withArgs("--port", "8080")
              .build();

      assertThat(container.getCommand()).containsExactly("python", "app.py");
      assertThat(container.getArgs()).containsExactly("--port", "8080");
    }
  }

  @Nested
  class EscapeHatch {

    @Test
    void shouldApplyCustomizerForImagePullPolicy() {
      Container container =
          new ContainerSpec()
              .withName("custom-pull")
              .withImage("app:v1")
              .customize(builder -> builder.withImagePullPolicy("Always"))
              .build();

      assertThat(container.getImagePullPolicy()).isEqualTo("Always");
    }

    @Test
    void shouldApplyCustomizerForSecurityContext() {
      Container container =
          new ContainerSpec()
              .withName("secure")
              .withImage("app:v1")
              .customize(
                  builder ->
                      builder
                          .withNewSecurityContext()
                          .withRunAsNonRoot(true)
                          .withReadOnlyRootFilesystem(true)
                          .endSecurityContext())
              .build();

      assertThat(container.getSecurityContext()).isNotNull();
      assertThat(container.getSecurityContext().getRunAsNonRoot()).isTrue();
      assertThat(container.getSecurityContext().getReadOnlyRootFilesystem()).isTrue();
    }

    @Test
    void shouldApplyMultipleCustomizersInOrder() {
      Container container =
          new ContainerSpec()
              .withName("multi-custom")
              .withImage("app:v1")
              .customize(builder -> builder.withImagePullPolicy("Always"))
              .customize(builder -> builder.withTerminationMessagePath("/dev/termination-log"))
              .customize(builder -> builder.withWorkingDir("/app"))
              .build();

      assertThat(container.getImagePullPolicy()).isEqualTo("Always");
      assertThat(container.getTerminationMessagePath()).isEqualTo("/dev/termination-log");
      assertThat(container.getWorkingDir()).isEqualTo("/app");
    }

    @Test
    void shouldAllowCustomizerToAccessBuilderState() {
      Container container =
          new ContainerSpec()
              .withName("stateful")
              .withImage("app:v1")
              .withPort(8080)
              .customize(
                  builder -> {
                    // Customizer can see and modify previously set state
                    assertThat(builder.getName()).isEqualTo("stateful");
                    return builder.withImagePullPolicy("IfNotPresent");
                  })
              .build();

      assertThat(container.getImagePullPolicy()).isEqualTo("IfNotPresent");
    }
  }

  @Nested
  class Validation {

    @Test
    void shouldThrowWhenNameNotSet() {
      ContainerSpec spec = new ContainerSpec().withImage("app:v1");

      assertThatThrownBy(spec::build)
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("name");
    }

    @Test
    void shouldThrowWhenImageNotSet() {
      ContainerSpec spec = new ContainerSpec().withName("my-container");

      assertThatThrownBy(spec::build)
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("image");
    }

    @Test
    void shouldThrowWhenBothNameAndImageNotSet() {
      ContainerSpec spec = new ContainerSpec();

      // Should throw for name first (checked first)
      assertThatThrownBy(spec::build)
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("name");
    }
  }

  @Nested
  class GetName {

    @Test
    void shouldReturnNullBeforeNameSet() {
      ContainerSpec spec = new ContainerSpec();
      assertThat(spec.getName()).isNull();
    }

    @Test
    void shouldReturnNameAfterSet() {
      ContainerSpec spec = new ContainerSpec().withName("my-container");
      assertThat(spec.getName()).isEqualTo("my-container");
    }
  }
}
