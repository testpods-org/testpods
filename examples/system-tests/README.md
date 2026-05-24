# TestPods System Tests Demo

This module demonstrates two end-to-end flows for the example order and product services.

## Prerequisites

- Minikube and `kubectl` available on the host.
- Docker daemon running for Spring Boot Buildpacks.
- Build and install the project from the repository root when core code changes:

```sh
mvn install
```

The service images are produced by Spring Boot Buildpacks during `package`:

- `examples/order-service:test-current`
- `examples/product-service:test-current`

Image refresh is manual. After changing either service, rerun `mvn -pl examples/order-service -am package`
or `mvn -pl examples/product-service -am package` before running the system tests.

## Image Cache Setup

TestPods treats service images and external dependency images differently:

- Service images use `GenericPod.fromLocalImage(...)`. They must already exist in Docker Desktop
  under the exact tag used by the test, for example `examples/order-service:test-current`.
  TestPods loads them into the Minikube profile with `minikube -p <profile> image load <image>`
  and sets Kubernetes `imagePullPolicy: Never`.
- External dependency images, such as `apache/kafka:3.9.1` and `postgres:16-alpine`, are checked
  in Docker Desktop first with `docker image inspect <image>`. If the image is missing, TestPods
  runs `docker pull <image>` once, then loads the cached image into Minikube with
  `minikube -p <profile> image load <image>`. These pods use `imagePullPolicy: IfNotPresent`.
- After a Minikube profile is destroyed and recreated, Docker Desktop still has the cached external
  images. TestPods reloads them into the new profile from Docker Desktop without pulling from the
  upstream registry again.

The effective order for dependency images is:

1. Docker Desktop local image cache.
2. Upstream image registry, only when Docker Desktop does not already have the image.
3. Minikube profile image store, loaded from Docker Desktop before the pod is created.
4. Kubernetes pod startup with `IfNotPresent`, so the Minikube-local image is used.

For service images, the order is stricter:

1. Docker Desktop local image cache, produced by the Spring Boot Buildpacks build.
2. Minikube profile image store, loaded from Docker Desktop before the pod is created.
3. Kubernetes pod startup with `Never`, so a missing local build fails fast instead of pulling an
   unrelated remote image.

## Test Log Levels

Each module has a `src/test/resources/logback-test.xml`. The default test log level is `INFO` for
the root logger and for TestPods packages. Override individual packages with Maven system
properties:

```sh
mvn test -Dtestpods.log.level=DEBUG -Dfabric8.log.level=WARN -Droot.log.level=INFO
```

Available switches are `testpods.log.level`, `fabric8.log.level`, `spring.log.level`,
`kafka.log.level`, `testcontainers.log.level`, `http.log.level`, and `root.log.level`.

## Scenario A: All In Cluster

Runs Kafka, two PostgreSQL databases, order-service, and product-service in Minikube.

```sh
mvn -pl examples/order-service,examples/product-service -am install
mvn -pl examples/system-tests -am verify -Dit.test=AllInClusterIT
```

The test creates a product with stock `10`, places an order for quantity `3`, and waits until
product-service consumes the Kafka event and reports stock `7`.

## Scenario B: Local Product Service Dev Flow

This flow runs infrastructure and order-service in Minikube, but product-service runs locally from
your IDE on `localhost:8082`.

1. Build the order-service image:

```sh
mvn -pl examples/order-service -am install
```

2. Open `LocalProductServiceDevIT` in IntelliJ.
3. Set a breakpoint on the log line at the start of `placeOrderConsumedByLocalProductService()`.
4. Run `LocalProductServiceDevIT` in IDE debug mode.
5. When the breakpoint hits, start `org.testpods.examples.product.ProductServiceApplication` in a
   second IDE run configuration with the default profile.
6. Confirm product-service connects to:
   - Kafka: `localhost:9092`
   - product database: `jdbc:postgresql://localhost:5433/productdb`
7. Optionally set a breakpoint in `OrderEventListener`.
8. Resume the test.

The in-cluster order-service calls `http://product-service:8082`, which is a Kubernetes `Service`
and manual `Endpoints` pair pointing back to the host machine. The local product-service uses the
fixed port-forwards exposed by the test.

## Fixed Local Ports

- Kafka: `localhost:9092`
- productdb: `localhost:5433`
- orderdb: `localhost:5432`
- local product-service: `localhost:8082`
- order-service: auto-assigned, logged by the test

## Cleanup Check

After a test finishes, inspect the logged namespace and run:

```sh
kubectl get pods,svc,endpoints -n <test-ns>
```

No demo resources should remain after the JUnit extension tears down the namespace.
