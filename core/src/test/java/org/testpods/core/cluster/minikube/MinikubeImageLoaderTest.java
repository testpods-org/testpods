package org.testpods.core.cluster.minikube;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testpods.core.cluster.ClusterException;

class MinikubeImageLoaderTest {

  @TempDir Path tempDir;

  @Test
  void load_delegatesToMinikubeImageLoad() throws Exception {
    Path argvFile = tempDir.resolve("argv.txt");
    Path minikube = executableScript("printf '%s\\n' \"$@\" > '" + argvFile + "'\nexit 0\n");

    new MinikubeImageLoader(minikube.toString())
        .load("testpods", "examples/order-service:test-current");

    assertThat(Files.readString(argvFile, StandardCharsets.UTF_8))
        .isEqualTo("-p\ntestpods\nimage\nload\nexamples/order-service:test-current\n");
  }

  @Test
  void load_surfacesStderrOnFailure() throws Exception {
    Path minikube = executableScript("echo 'image load failed hard' >&2\nexit 7\n");

    assertThatThrownBy(
            () ->
                new MinikubeImageLoader(minikube.toString())
                    .load("testpods", "examples/product-service:test-current"))
        .isInstanceOf(ClusterException.class)
        .hasMessageContaining("minikube image load failed (exit 7)")
        .hasMessageContaining("image load failed hard");
  }

  @Test
  void loadCachedOrPull_usesDockerCacheBeforeMinikubeLoad() throws Exception {
    Path dockerArgvFile = tempDir.resolve("docker-argv.txt");
    Path minikubeArgvFile = tempDir.resolve("minikube-argv.txt");
    Path docker =
        executableScript(
            "docker",
            "printf '%s\\n' \"$@\" >> '"
                + dockerArgvFile
                + "'\n"
                + "if [ \"$1\" = 'image' ] && [ \"$2\" = 'inspect' ]; then exit 0; fi\n"
                + "exit 9\n");
    Path minikube =
        executableScript(
            "minikube", "printf '%s\\n' \"$@\" > '" + minikubeArgvFile + "'\nexit 0\n");

    new MinikubeImageLoader(minikube.toString(), docker.toString())
        .loadCachedOrPull("testpods", "apache/kafka:3.9.1");

    assertThat(Files.readString(dockerArgvFile, StandardCharsets.UTF_8))
        .isEqualTo("image\ninspect\napache/kafka:3.9.1\n");
    assertThat(Files.readString(minikubeArgvFile, StandardCharsets.UTF_8))
        .isEqualTo("-p\ntestpods\nimage\nload\napache/kafka:3.9.1\n");
  }

  @Test
  void loadCachedOrPull_pullsWhenDockerCacheMisses() throws Exception {
    Path dockerArgvFile = tempDir.resolve("docker-argv.txt");
    Path docker =
        executableScript(
            "docker",
            "printf '%s\\n' \"$@\" >> '"
                + dockerArgvFile
                + "'\n"
                + "if [ \"$1\" = 'image' ] && [ \"$2\" = 'inspect' ]; then exit 1; fi\n"
                + "if [ \"$1\" = 'pull' ]; then exit 0; fi\n"
                + "exit 9\n");
    Path minikube = executableScript("minikube", "exit 0\n");

    new MinikubeImageLoader(minikube.toString(), docker.toString())
        .loadCachedOrPull("testpods", "postgres:16-alpine");

    assertThat(Files.readString(dockerArgvFile, StandardCharsets.UTF_8))
        .isEqualTo(
            "image\ninspect\npostgres:16-alpine\n"
                + "pull\npostgres:16-alpine\n");
  }

  private Path executableScript(String body) throws Exception {
    return executableScript("minikube", body);
  }

  private Path executableScript(String name, String body) throws Exception {
    Path script = tempDir.resolve(name);
    Files.writeString(script, "#!/bin/sh\n" + body, StandardCharsets.UTF_8);
    assertThat(script.toFile().setExecutable(true)).isTrue();
    return script;
  }
}
