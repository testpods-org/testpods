# PostgreSQL TestPod

`PostgreSQLPod` runs the official PostgreSQL Docker image in a Kubernetes StatefulSet for local
integration tests.

## Basic JUnit Usage

```java
import org.testpods.core.cluster.K8sCluster;
import org.testpods.core.pods.external.postgresql.PostgreSQLPod;
import org.testpods.junit.RegisterCluster;
import org.testpods.junit.TestPod;
import org.testpods.junit.TestPods;

@TestPods
class RepositoryTest {

  @RegisterCluster
  static K8sCluster cluster = K8sCluster.newMinikube().withNamespace();

  @TestPod
  static PostgreSQLPod postgres =
      new PostgreSQLPod()
          .withDatabase("orders")
          .withUsername("orders_user")
          .withPassword("orders_pass");

  // Use postgres.getJdbcUrl(), postgres.getUsername(), and postgres.getPassword() in tests.
}
```

When the pod is ready, TestPods logs the external JDBC URL at info level. Use that URL with a local
database browser, for example:

```text
jdbc:postgresql://127.0.0.1:30432/orders
postgresql://orders_user:orders_pass@127.0.0.1:30432/orders
```

## Port Mapping

PostgreSQL listens on container port `5432`. TestPods exposes that port by default and follows the
same naming conventions as Testcontainers:

```java
static PostgreSQLPod postgres =
    new PostgreSQLPod()
        .withDatabase("orders")
        .withExposedPorts(5432);

String host = postgres.getHost();
int port = postgres.getMappedPort(5432);
```

To request a fixed externally reachable port:

```java
static PostgreSQLPod postgres =
    new PostgreSQLPod()
        .withDatabase("orders")
        .withFixedExposedPort(54320, 5432);
```

The meaning of the fixed port depends on the cluster access strategy:

```text
Port-forward access:
  your JVM -> 127.0.0.1:54320 -> Service:5432 -> PostgreSQL Pod:5432

NodePort / Minikube service access:
  your JVM -> <node-ip>:54320 -> Service:5432 -> PostgreSQL Pod:5432
```

NodePort services can only use ports from the cluster's NodePort range. Many local clusters default
to `30000-32767`, so `54320` is usually valid for port-forward access but not for default NodePort
access.

Other pods inside Kubernetes do not need the mapped external port. They should use service DNS:

```text
orders-service -> postgres.<namespace>.svc.cluster.local:5432
```

## Image Version

The default image is `postgres:16-alpine`. Override it either with a complete image name or a tag:

```java
new PostgreSQLPod("postgres:17-alpine");
new PostgreSQLPod().withVersion("15-alpine");
```

## Persistent Data

By default PostgreSQL data is ephemeral and disappears when the pod is deleted. Enable persistence
with a StatefulSet PVC:

```java
static PostgreSQLPod postgres =
    new PostgreSQLPod()
        .withDatabase("orders")
        .withUsername("orders_user")
        .withPassword("orders_pass")
        .withPersistentData("2Gi");
```

Inside the PostgreSQL container, the PVC is mounted at `/var/lib/postgresql/data`, and `PGDATA` is
set to `/var/lib/postgresql/data/pgdata`.

The extra `pgdata` subdirectory keeps PostgreSQL's actual data directory below the volume mount
root. This avoids common `initdb` problems when a mounted filesystem is not completely empty at its
root, for example when it contains `lost+found` or provisioner metadata.

The physical location of the PVC data on the Minikube node depends on the Kubernetes StorageClass
and PersistentVolume provisioner. It is not normally exposed as a convenient folder on your laptop's
host filesystem. To inspect where Minikube backed a PVC, check the bound PersistentVolume:

```bash
kubectl get pvc
kubectl get pv <pv-name> -o yaml
```

For Minikube's default hostPath-style storage, look for `spec.hostPath.path` in the PV output. That
path is inside the Minikube node filesystem.

## Init SQL

PostgreSQL Docker entrypoint scripts can be mounted from direct SQL or a classpath resource:

```java
new PostgreSQLPod()
    .withInitSql("CREATE TABLE orders (id bigint primary key);");

new PostgreSQLPod()
    .withInitScript("db/init.sql");
```

## Multiple Databases

Use `withDatabases` when one PostgreSQL server should contain several databases, for example one
database per service under test:

```java
static PostgreSQLPod postgres =
    new PostgreSQLPod()
        .withDatabases("orders", "inventory")
        .withUsername("app_user")
        .withPassword("app_pass");

String ordersJdbcUrl = postgres.getJdbcUrl("orders");
String inventoryJdbcUrl = postgres.getJdbcUrl("inventory");
```

The first database is the primary database and is created by the official PostgreSQL image through
`POSTGRES_DB`. Additional databases are created by a generated init SQL script mounted into
`/docker-entrypoint-initdb.d`.

You can combine generated databases with your own init SQL. The additional databases are created
first, then your SQL runs:

```java
new PostgreSQLPod()
    .withDatabases("orders", "inventory")
    .withInitSql("""
        CREATE TABLE order_events (id bigint primary key);
        """);
```

If your application creates its own database, for example a Spring Boot service running migrations
or administrative startup code, you do not need `withDatabases`. Configure only the PostgreSQL user
and password, then let the service create its database as part of its normal startup behavior.

## Full Integration Test

There is a Failsafe integration test that starts minikube through `@TestPods`, provisions
PostgreSQL, and verifies both Kubernetes readiness and a JDBC `SELECT 1`:

```bash
mvn verify -Dit.test=TestPodsExtensionPostgreSQLIT
```

## Debug Deployment Details

After a pod is ready, TestPods can log a detailed Kubernetes deployment report at debug level. The
generic report includes workload type, namespace, wait strategy, internal and external endpoints,
runtime pods, containers, images, ports, masked environment variables, services, ConfigMaps, and
mounted PVCs.

`PostgreSQLPod` adds PostgreSQL-specific details such as databases, username, JDBC URL, PostgreSQL
URI, internal JDBC URL, and persistent storage settings.

Enable debug logging for TestPods to see the report:

```xml
<logger name="org.testpods" level="DEBUG"/>
```
