**Architecture**

The package is built as a template-method stack:

- `Pod` defines the user-facing contract for configuration, lifecycle, ports, logs, and properties: [Pod.java](/Users/henrik/git/henrik/testpods-project/testpods/core/src/main/java/org/testpods/core/pods/Pod.java#L27)
- `BaseManagedPod` owns the shared start/stop workflow, namespace/cluster resolution, pod-spec customization, logs, exec, and external port mapping: [BaseManagedPod.java](/Users/henrik/git/henrik/testpods-project/testpods/core/src/main/java/org/testpods/core/pods/BaseManagedPod.java#L69)
- `DeploymentPod` and `StatefulSetPod` choose the workload kind and service shape: [DeploymentPod.java](/Users/henrik/git/henrik/testpods-project/testpods/core/src/main/java/org/testpods/core/pods/DeploymentPod.java#L21), [StatefulSetPod.java](/Users/henrik/git/henrik/testpods-project/testpods/core/src/main/java/org/testpods/core/pods/StatefulSetPod.java#L25)
- Concrete pods like `GenericPod`, `PostgreSQLPod`, `KafkaPod`, and `MongoDBPod` fill in the main container and any extra hooks: [GenericPod.java](/Users/henrik/git/henrik/testpods-project/testpods/core/src/main/java/org/testpods/core/pods/GenericPod.java#L31), [PostgreSQLPod.java](/Users/henrik/git/henrik/testpods-project/testpods/core/src/main/java/org/testpods/core/pods/external/postgresql/PostgreSQLPod.java#L82), [KafkaPod.java](/Users/henrik/git/henrik/testpods-project/testpods/core/src/main/java/org/testpods/core/pods/external/kafka/KafkaPod.java#L48), [MongoDBPod.java](/Users/henrik/git/henrik/testpods-project/testpods/core/src/main/java/org/testpods/core/pods/external/mongodb/MongoDBPod.java#L46)

The actual Kubernetes API objects are created by the workload and service manager layer, not directly by the pod classes: [WorkloadConfig.java](/Users/henrik/git/henrik/testpods-project/testpods/core/src/main/java/org/testpods/core/workload/WorkloadConfig.java#L22), [ServiceConfig.java](/Users/henrik/git/henrik/testpods-project/testpods/core/src/main/java/org/testpods/core/service/ServiceConfig.java#L25)

**Start/Stop Flow**

`BaseManagedPod.start()` does this in order:

1. Resolve cluster and namespace defaults
2. Run `preStart()` if the concrete pod implements `PodLifecycleHooks`
3. Build the pod spec
4. Create the workload resource
5. Create the service resource
6. Wait for readiness
7. Run `postStart()` if present

See: [BaseManagedPod.java](/Users/henrik/git/henrik/testpods-project/testpods/core/src/main/java/org/testpods/core/pods/BaseManagedPod.java#L739) and [PodLifecycleHooks.java](/Users/henrik/git/henrik/testpods-project/testpods/core/src/main/java/org/testpods/core/pods/PodLifecycleHooks.java#L13)

`stop()` runs `preStop()` first, then deletes service and workload, and also cleans up any cluster access strategy state: [BaseManagedPod.java](/Users/henrik/git/henrik/testpods-project/testpods/core/src/main/java/org/testpods/core/pods/BaseManagedPod.java#L752)

**Namespace / Cluster Resolution**

The runtime defaults come from `TestPodDefaults` and `BaseManagedPod.resolveRuntimeDefaults()`:

- cluster: explicit cluster, then `TestPodDefaults.resolveCluster()`
- namespace: explicit namespace object, then explicit namespace name, then shared namespace, then cluster default namespace, otherwise create a namespace

See: [TestPodDefaults.java](/Users/henrik/git/henrik/testpods-project/testpods/core/src/main/java/org/testpods/core/pods/TestPodDefaults.java#L236), [BaseManagedPod.java](/Users/henrik/git/henrik/testpods-project/testpods/core/src/main/java/org/testpods/core/pods/BaseManagedPod.java#L839)

One subtlety: `TestPodDefaults.resolveNamespaceName()` exists, but the current `BaseManagedPod` flow does not call it directly. The actual namespace path is the explicit/shared/default/create logic above.

**What Each Class Creates**

- `DeploymentPod` creates:
  - one `Deployment`
  - one `ClusterIP Service`
  - service customizers are applied
  - `deploymentCustomizers` are stored but not currently wired into the manager path

  See: [DeploymentPod.java](/Users/henrik/git/henrik/testpods-project/testpods/core/src/main/java/org/testpods/core/pods/DeploymentPod.java#L21), [DeploymentManager.java](/Users/henrik/git/henrik/testpods-project/testpods/core/src/main/java/org/testpods/core/workload/DeploymentManager.java#L19), [ClusterIPServiceManager.java](/Users/henrik/git/henrik/testpods-project/testpods/core/src/main/java/org/testpods/core/service/ClusterIPServiceManager.java#L24)

- `StatefulSetPod` creates:
  - one `StatefulSet`
  - one headless `Service`
  - optional `PersistentVolumeClaim` templates on the StatefulSet when `withPvcCustomizer(...)` is used
  - `statefulSetCustomizers` are stored but not currently wired into the manager path

  See: [StatefulSetPod.java](/Users/henrik/git/henrik/testpods-project/testpods/core/src/main/java/org/testpods/core/pods/StatefulSetPod.java#L25), [StatefulSetManager.java](/Users/henrik/git/henrik/testpods-project/testpods/core/src/main/java/org/testpods/core/workload/StatefulSetManager.java#L22), [HeadlessServiceManager.java](/Users/henrik/git/henrik/testpods-project/testpods/core/src/main/java/org/testpods/core/service/HeadlessServiceManager.java#L23)

- `GenericPod` creates:
  - same resources as `DeploymentPod`
  - one `Deployment`
  - one `ClusterIP Service`
  - no extra Kubernetes object by itself
  - optionally preloads a local image into Minikube before start if created via `fromLocalImage(...)`

  See: [GenericPod.java](/Users/henrik/git/henrik/testpods-project/testpods/core/src/main/java/org/testpods/core/pods/GenericPod.java#L31)

- `GenericStatefulPod` creates:
  - same resources as `StatefulSetPod`
  - one `StatefulSet`
  - one headless `Service`
  - optional PVC templates if configured

  See: [GenericStatefulPod.java](/Users/henrik/git/henrik/testpods-project/testpods/core/src/main/java/org/testpods/core/pods/GenericStatefulPod.java#L30)

- `LocalServicePod` is the outlier:
  - it does **not** create a workload at all
  - it creates a `Service` with **no selector**
  - it creates a manually managed `Endpoints` object pointing to the developer machine’s IP
  - that makes in-cluster clients resolve the service name to the host machine, not to a pod

  See: [LocalServicePod.java](/Users/henrik/git/henrik/testpods-project/testpods/core/src/main/java/org/testpods/core/pods/local/LocalServicePod.java#L51)

- `MongoDBPod` creates:
  - same resources as `StatefulSetPod`
  - one `StatefulSet`
  - one headless `Service`
  - no extra API objects beyond that
  - `preStart()` only preloads the image for Minikube

  See: [MongoDBPod.java](/Users/henrik/git/henrik/testpods-project/testpods/core/src/main/java/org/testpods/core/pods/external/mongodb/MongoDBPod.java#L46)

- `PostgreSQLPod` creates:
  - one `StatefulSet`
  - one `ClusterIP Service`
  - one headless `Service`
  - optional `ConfigMap` named `<pod>-init` when init SQL or extra database creation is needed
  - optional `PersistentVolumeClaim` template named `data` when persistent storage is enabled
  - the StatefulSet’s pod template mounts the ConfigMap and/or PVC volume as needed

  See: [PostgreSQLPod.java](/Users/henrik/git/henrik/testpods-project/testpods/core/src/main/java/org/testpods/core/pods/external/postgresql/PostgreSQLPod.java#L548)

- `KafkaPod` creates:
  - one `StatefulSet`
  - one `ClusterIP Service`
  - one headless `Service`
  - optional UI sidecar container inside the pod spec when `withUi()` is enabled
  - no separate Service/ConfigMap/PVC for the UI
  - topics are created after startup by `exec` into the broker, not by a Kubernetes resource

  See: [KafkaPod.java](/Users/henrik/git/henrik/testpods-project/testpods/core/src/main/java/org/testpods/core/pods/external/kafka/KafkaPod.java#L453)

**Pod Spec Construction**

`BaseManagedPod.buildPodSpec()` always starts with the main container and then applies customization in this order:

1. init containers
2. sidecars
3. resource requests on the first container
4. low-level `PodSpecBuilder` customizers

See: [BaseManagedPod.java](/Users/henrik/git/henrik/testpods-project/testpods/core/src/main/java/org/testpods/core/pods/BaseManagedPod.java#L891)

That means `withInitContainer(...)`, `withSidecar(...)`, and `withPodCustomizer(...)` all affect the pod template, not separate Kubernetes resources.

`ContainerDefinition` is the generic-pod container adapter: it holds image, env, command, args, ports, optional HTTP readiness probe, and derives a default wait strategy from those settings. It builds a Fabric8 `Container` using `ContainerSpec`: [ContainerDefinition.java](/Users/henrik/git/henrik/testpods-project/testpods/core/src/main/java/org/testpods/core/pods/ContainerDefinition.java#L23)

`ContainerSpec` is the lower-level container builder. It can create a Fabric8 `Container` with:

- ports
- env vars
- command/args
- volume mounts
- readiness/liveness/startup probes
- resource requests and limits
- arbitrary Fabric8 customizations

See: [ContainerSpec.java](/Users/henrik/git/henrik/testpods-project/testpods/core/src/main/java/org/testpods/core/pods/builders/ContainerSpec.java#L80)

`ProbeSpec` is the corresponding probe builder, supporting TCP, HTTP/HTTPS, and exec probes: [ProbeSpec.java](/Users/henrik/git/henrik/testpods-project/testpods/core/src/main/java/org/testpods/core/pods/builders/ProbeSpec.java#L65)

`InitContainerBuilder` and `SidecarBuilder` are simple facades that each emit a Fabric8 `Container`: [InitContainerBuilder.java](/Users/henrik/git/henrik/testpods-project/testpods/core/src/main/java/org/testpods/core/pods/builders/InitContainerBuilder.java#L9), [SidecarBuilder.java](/Users/henrik/git/henrik/testpods-project/testpods/core/src/main/java/org/testpods/core/pods/builders/SidecarBuilder.java#L8)

**Wait Behavior**

- `DeploymentPod` defaults to readiness-probe waiting with a 1 minute timeout: [DeploymentPod.java](/Users/henrik/git/henrik/testpods-project/testpods/core/src/main/java/org/testpods/core/pods/DeploymentPod.java#L63)
- `StatefulSetPod` defaults to readiness-probe waiting with a 2 minute timeout: [StatefulSetPod.java](/Users/henrik/git/henrik/testpods-project/testpods/core/src/main/java/org/testpods/core/pods/StatefulSetPod.java#L92)
- `ContainerDefinition` derives an HTTP wait, then port wait, then readiness-probe wait: [ContainerDefinition.java](/Users/henrik/git/henrik/testpods-project/testpods/core/src/main/java/org/testpods/core/pods/ContainerDefinition.java#L147)
- PostgreSQL waits in three layers: pod readiness, a log message, then a real JDBC `SELECT 1`: [PostgreSQLWaitStrategy.java](/Users/henrik/git/henrik/testpods-project/testpods/core/src/main/java/org/testpods/core/pods/external/postgresql/PostgreSQLWaitStrategy.java#L22)
- Kafka waits for pod readiness and then a real broker API call via `kafka-topics --list`: [KafkaWaitStrategy.java](/Users/henrik/git/henrik/testpods-project/testpods/core/src/main/java/org/testpods/core/pods/external/kafka/KafkaWaitStrategy.java#L9)

**Important Details by Concrete Pod**

- `PostgreSQLPod`:
  - main container listens on `5432`
  - readiness/liveness probes use `pg_isready`
  - persistent storage is implemented through a StatefulSet PVC template
  - init SQL is loaded into a ConfigMap and mounted at `/docker-entrypoint-initdb.d`
  - `createWorkloadManager()` sets the StatefulSet’s `serviceName` to `<name>-headless`
  - `createServiceManager()` uses `CompositeServiceManager` to create both `<name>` and `<name>-headless`
  - `preStop()` deletes the init ConfigMap

  See: [PostgreSQLPod.java](/Users/henrik/git/henrik/testpods-project/testpods/core/src/main/java/org/testpods/core/pods/external/postgresql/PostgreSQLPod.java#L473)

- `KafkaPod`:
  - runs Kafka in KRaft mode, so no ZooKeeper resource is created
  - main container exposes internal, external, and controller ports
  - `createServiceManager()` creates a `ClusterIP` service plus a headless service
  - `buildServiceCustomizers()` enables `publishNotReadyAddresses(true)`
  - the optional Redpanda Console is just a sidecar container in the same pod
  - `getInternalPort()` is the external-listener port (`9093`), while `getInternalBootstrapServers()` points to the true in-cluster listener (`9092`)

  See: [KafkaPod.java](/Users/henrik/git/henrik/testpods-project/testpods/core/src/main/java/org/testpods/core/pods/external/kafka/KafkaPod.java#L403)

- `MongoDBPod`:
  - main container uses an exec readiness/liveness probe that runs `mongosh` or `mongo`
  - authentication, database, and replica-set settings are all container env/args, not extra Kubernetes objects
  - it inherits the standard StatefulSet + headless service path

  See: [MongoDBPod.java](/Users/henrik/git/henrik/testpods-project/testpods/core/src/main/java/org/testpods/core/pods/external/mongodb/MongoDBPod.java#L202)

**Bottom Line**

If you only want the resource summary:

- `DeploymentPod` and `GenericPod` create `Deployment + ClusterIP Service`
- `StatefulSetPod`, `GenericStatefulPod`, `MongoDBPod`, and most of `KafkaPod` create `StatefulSet + headless Service`
- `PostgreSQLPod` creates `StatefulSet + ClusterIP Service + headless Service + optional ConfigMap + optional PVCs`
- `LocalServicePod` creates `Service + Endpoints`
- init containers, sidecars, probes, and resource requests live inside the pod template, not as separate K8s API objects

**Class Table**

| Class | Purpose | Workload / Service | Extra resources or notes |
|---|---|---|---|
| `Pod` | Public contract for all test pods | None | Configuration, lifecycle, logs, exec, ports, property publishing |
| `PodLifecycleHooks` | Optional lifecycle callbacks | None | `preStart`, `postStart`, `preStop` only |
| `TestPodDefaults` | Resolve cluster and namespace defaults | None | Thread-local plus global defaults |
| `BaseManagedPod` | Shared orchestration and pod-spec assembly | None directly | Creates workload/service via managers, applies init containers, sidecars, resource requests, customizers |
| `DeploymentPod` | Base for stateless pods | `Deployment` + `ClusterIP Service` | Default readiness-based wait strategy |
| `StatefulSetPod` | Base for stateful pods | `StatefulSet` + headless `Service` | Can add PVC templates; caches external endpoint |
| `ContainerDefinition` | Generic container description helper | None directly | Builds main container and derives default wait strategy |
| `ContainerSpec` | Low-level Fabric8 container builder | None directly | Ports, env, commands, probes, mounts, resources, customizers |
| `ProbeSpec` | Probe builder helper | None directly | TCP, HTTP/HTTPS, and exec probes |
| `InitContainerBuilder` | Simplified init-container builder | None directly | Emits a Fabric8 `Container` for init usage |
| `SidecarBuilder` | Simplified sidecar builder | None directly | Emits a Fabric8 `Container` for sidecar usage |
| `GenericPod` | Arbitrary image on a Deployment | `Deployment` + `ClusterIP Service` | Optional Minikube local-image preload |
| `GenericStatefulPod` | Arbitrary image on a StatefulSet | `StatefulSet` + headless `Service` | Optional PVC templates |
| `LocalServicePod` | Expose a local machine service to the cluster | `Service` + `Endpoints` | No workload; service has no selector |
| `MongoDBPod` | MongoDB integration pod | `StatefulSet` + headless `Service` | Auth env vars, DB env, optional replica set args |
| `PostgreSQLPod` | PostgreSQL integration pod | `StatefulSet` + `ClusterIP Service` + headless `Service` | Optional init `ConfigMap`, optional PVC template, init-script volume mount |
| `KafkaPod` | Single-node Kafka broker | `StatefulSet` + `ClusterIP Service` + headless `Service` | Optional UI sidecar, fixed exposed ports, topic creation after startup |
| `WorkloadConfig` | Data passed to workload managers | None directly | Name, namespace, labels, annotations, pod spec, client |
| `WorkloadManager` | Internal workload abstraction | None directly | `DeploymentManager` or `StatefulSetManager` implement it |
| `DeploymentManager` | Create and delete a Deployment | `Deployment` | 1 replica, label selector on `app` |
| `StatefulSetManager` | Create and delete a StatefulSet | `StatefulSet` | 1 replica, stable `serviceName`, optional PVC templates |
| `ServiceConfig` | Data passed to service managers | None directly | Port list, selector, labels, customizers, fixed node ports |
| `ServiceManager` | Internal service abstraction | None directly | `ClusterIP`, `Headless`, `NodePort`, or composite managers implement it |
| `ClusterIPServiceManager` | Create a normal service | `Service` (`ClusterIP`) | Multi-port support |
| `HeadlessServiceManager` | Create a headless service | `Service` (`clusterIP: None`) | Used for stable DNS with StatefulSets |
| `NodePortServiceManager` | Create an externally reachable service | `Service` (`NodePort`) | Can set fixed node ports |
| `CompositeServiceManager` | Create multiple services together | Multiple `Service` objects | Used by PostgreSQL and Kafka to combine service types |
