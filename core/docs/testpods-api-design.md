# TestPods API Design Document

## Executive Summary

TestPods is an open-source Java library designed to be "the Testcontainers equivalent for Kubernetes." It enables developers to test Spring Boot microservices (and other Java frameworks) in Kubernetes environments with the same ease that Testcontainers provides for Docker.

This document captures the API design decisions, mental models, and implementation patterns for the TestPods library.

---

## Core Mental Model

### Why Pod as the Primary Abstraction

The `Pod` is chosen as the primary abstraction for several reasons:

1. **Pod is the atomic schedulable unit** - You can't schedule half a pod; it's all-or-nothing on a node.
2. **Avoids "Container" overload** - In K8s, "container" lives inside pods, and the term is heavily loaded from Docker.
3. **Parallel to Testcontainers** - Mirrors the familiar Testcontainers API where `Container` is the primary type.
4. **Pod can contain multiple containers** - Including init containers and sidecars, which the abstraction can hide.

### Pod vs Workload Resource

In real K8s usage, developers rarely create bare `Pod` manifests. They create workload resources that manage pods:

| Workload Resource | Use Case | Pod Behavior |
|-------------------|----------|--------------|
| Deployment | Stateless apps | Ephemeral, replaceable |
| StatefulSet | Databases, Kafka | Stable identity, ordered startup |
| Job/CronJob | Batch work | Run-to-completion |
| DaemonSet | Per-node agents | One per node |

**Design Decision**: The `*Pod` abstraction hides the underlying workload resource. A `MongoDBPod` internally creates a StatefulSet + Service + PVC, but the developer just sees "a MongoDB they can connect to."

### What a TestPod Manages Internally

```
MongoDBPod (what developer sees)
    └── creates internally:
        ├── StatefulSet (1 replica)
        ├── Service (ClusterIP or headless)
        ├── PersistentVolumeClaim (optional)
        ├── ConfigMap (mongodb.conf)
        └── Secret (credentials)
```

### Kubernetes Lifecycle vs Docker

```
Docker:      pull → create → start → ready
Kubernetes:  schedule → init containers (sequential) → main containers → readiness probes → ready
```

Readiness probes are especially important - the abstraction needs to "wait for ready" in a K8s-native way (probe success, not just port open).

---

## Core Type Hierarchy

### Base TestPod Class

```java
//public interface Container<SELF extends Container<SELF>> extends LinkableContainer, ContainerState {
interface Pod<SELF extends Pod<SELF>> {
    
    // Fluent configuration
    public SELF withName(String name);
    public SELF inNamespace(TestNamespace namespace);
    public SELF withLabels(Map<String, String> labels);
    public SELF withAnnotations(Map<String, String> annotations);
    
    // Resource constraints
    public SELF withResources(ResourceRequirements resources);
    public SELF withResources(String cpuRequest, String memoryRequest);
    
    // Wait strategies (K8s-native)
    public SELF waitingFor(WaitStrategy strategy);
    
    // Lifecycle
    public void start();
    public void stop();
    public boolean isRunning();
    public boolean isReady();
    
    // Observability
    public String getLogs();
    public ExecResult exec(String... command);
    
    // Connection - dual model (critical abstraction)
    public String getInternalHost();     // DNS within cluster (for pod-to-pod)
    public int getInternalPort();        // Original port
    public String getExternalHost();     // For test code running outside cluster
    public int getExternalPort();        // Mapped port
    
    // Property publishing (for dependency injection)
    protected abstract void publishProperties(PropertyContext ctx);
}
```

### Infrastructure-Specific Pods

```java
public class MongoDBPod extends TestPod<MongoDBPod> {
    
    public MongoDBPod();                    // Sensible default image
    public MongoDBPod(String image);
    
    // MongoDB-specific fluent API
    public MongoDBPod withVersion(String version);
    public MongoDBPod withReplicaSet(String name);
    public MongoDBPod withCredentials(String username, String password);
    public MongoDBPod withDatabase(String database);
    public MongoDBPod withPersistence(boolean enabled);
    public MongoDBPod withPersistence(PersistentVolumeClaimSpec pvcSpec);
    
    // Connection helpers (type-safe)
    public String getConnectionString();           // External, for test code
    public String getInternalConnectionString();   // For services in cluster
    public MongoClient createClient();             // Convenience factory
}

public class KafkaPod extends TestPod<KafkaPod> {
    
    public KafkaPod withKraftMode(boolean kraft);  // No ZK dependency
    public KafkaPod withTopics(String... topics);
    public KafkaPod withPartitions(int partitions);
    
    public String getBootstrapServers();           // External
    public String getInternalBootstrapServers();   // Internal
}
```

### GenericPod for Arbitrary Images

```java
public class GenericPod extends TestPod<GenericPod> {
    
    public GenericPod(String image);
    
    public GenericPod withPort(int port);
    public GenericPod withEnv(String name, String value);
    public GenericPod withVolume(String mountPath, ConfigMap configMap);
    public GenericPod withCommand(String... command);
}
```

### ServicePod for Application Containers

For deploying application services (not infrastructure):

```java
public class ServicePod extends TestPod<ServicePod> {
    
    public ServicePod(String image);
    
    // Configuration injection via environment variables
    public ServicePod withEnv(String name, String value);
    public ServicePod withEnv(String name, Supplier<String> valueSupplier);
    public ServicePod withEnvFromProperty(String envName, String propertyKey);
    public ServicePod withEnvFromPod(TestPod<?> pod, String envName, 
                                      Function<TestPod<?>, String> valueExtractor);
    
    // Configuration via ConfigMap (file-based config)
    public ServicePod withConfigFile(String mountPath, String propertyKey);
    public ServicePod withConfigFile(String mountPath, Map<String, String> content);
    
    // Spring Boot specific helpers
    public ServicePod withSpringProfile(String... profiles);
    public ServicePod withSpringProperty(String key, String propertyContextKey);
    
    // Service exposure
    public ServicePod withPort(int port);
    public ServicePod withPort(String name, int port);
    
    // Health probes
    public ServicePod withReadinessProbe(String path, int port);
    public ServicePod withLivenessProbe(String path, int port);
    
    // Explicit pod dependency
    public ServicePod dependsOn(TestPod<?>... pods);
    
    // External URL for test code
    public String getExternalUrl();
}
```

---

## Fluent Configuration: Escape Hatches

The API uses progressive disclosure with three levels:

### High-Level (Most Users)

```java
MongoDBPod mongo = new MongoDBPod()
    .withVersion("6.0")
    .withCredentials("admin", "secret")
    .withPersistence(true);
```

### Mid-Level (Add Containers)

Uses lambda/Consumer pattern - no `.endX()` required because lambda boundary handles scoping:

```java
MongoDBPod mongo = new MongoDBPod()
    .withInitContainer(init -> init
        .withName("permission-fix")
        .withImage("busybox:latest")
        .withCommand("sh", "-c", "chmod -R 777 /data/db"))
    
    .withSidecar(sidecar -> sidecar
        .withName("metrics-exporter")
        .withImage("bitnami/mongodb-exporter:latest")
        .withPort(9216));
```

### Low-Level (Full Fabric8 Access)

Uses standard Fabric8 builder pattern with `.endX()`:

```java
MongoDBPod mongo = new MongoDBPod()
    .withPodCustomizer(podSpec -> podSpec
        .editOrNewSecurityContext()
            .withRunAsUser(1000L)
            .withFsGroup(1000L)
        .endSecurityContext())
    
    .withStatefulSetCustomizer(ss -> ss
        .editSpec()
            .withReplicas(3)
        .endSpec())
    
    .withServiceCustomizer(svc -> svc
        .editSpec()
            .withType("NodePort")
        .endSpec());
```

The `*Customizer` methods accept `UnaryOperator<Builder>` for full Fabric8 fluency.

---

## Cluster and Namespace Management

### K8sCluster Interface

```java
public interface K8sCluster extends Closeable {
    
    KubernetesClient getClient();
    ExternalAccessStrategy getAccessStrategy();
    
    // Factory methods
    static K8sCluster minikube();
    static K8sCluster kind(String clusterName);
    static K8sCluster fromKubeconfig();              // Current context
    static K8sCluster fromKubeconfig(String path);   // Explicit config
}
```

### TestNamespace (Isolation Boundary)

Analogous to Testcontainers Network:

```java
public class TestNamespace implements Closeable {
    
    public TestNamespace(K8sCluster cluster);
    
    public TestNamespace withName(String name);
    public TestNamespace withRandomSuffix();         // test-abc123
    public TestNamespace withNetworkPolicy(NetworkPolicySpec policy);
    public TestNamespace withResourceQuota(ResourceQuotaSpec quota);
    public TestNamespace deleteOnClose(boolean delete);
    
    public String getName();
    public K8sCluster getCluster();
    public void create();
}
```

---

## Configuration Injection: PropertyContext

### The Problem

Two different connection contexts exist:

```
┌─────────────────────────────────────────────────────────────────┐
│  Test JVM (Spring Boot Integration Test)                        │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ SUT (Service Under Test)                                 │   │
│  │ Needs: kafka.bootstrap-servers, service-b.url            │   │
│  │ Connection: EXTERNAL (port-forward/nodeport)             │   │
│  └─────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│  Kubernetes Cluster                                             │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐      │
│  │  KafkaPod    │◄───│  ServiceA    │    │  ServiceB    │      │
│  │              │    │  Needs:      │    │  Needs:      │      │
│  │              │◄───│  - kafka     │◄───│  - kafka     │      │
│  │              │    │  - serviceB  │    │              │      │
│  └──────────────┘    └──────────────┘    └──────────────┘      │
│                                                                 │
│  Connection: INTERNAL (kafka.test-ns.svc.cluster.local:9092)   │
└─────────────────────────────────────────────────────────────────┘
```

### PropertyContext Design

A shared registry where pods publish properties and other pods consume them:

```java
public class PropertyContext {
    
    private final Map<String, Supplier<String>> properties = new ConcurrentHashMap<>();
    
    // Properties are suppliers - resolved lazily after pods start
    public void publish(String key, Supplier<String> valueSupplier);
    public Supplier<String> get(String key);
    public String resolve(String key);  // Fails if pod not started
    public Map<String, String> resolveAll(String... keys);
    
    // Convenience for ${property.name} interpolation
    public String interpolate(String template);
}
```

### Pods Publish Properties Automatically

```java
public class KafkaPod extends TestPod<KafkaPod> {
    
    @Override
    protected void publishProperties(PropertyContext ctx) {
        String prefix = getName();  // e.g., "kafka" or custom name
        
        // Internal - for other pods in cluster
        ctx.publish(prefix + ".internal.bootstrap-servers", this::getInternalBootstrapServers);
        ctx.publish(prefix + ".internal.host", this::getInternalHost);
        ctx.publish(prefix + ".internal.port", () -> String.valueOf(getInternalPort()));
        
        // External - for test code
        ctx.publish(prefix + ".external.bootstrap-servers", this::getBootstrapServers);
    }
}
```

### Property Naming Convention

Properties are namespaced by pod name to avoid collisions:

```java
KafkaPod kafka1 = new KafkaPod().withName("kafka-orders");
KafkaPod kafka2 = new KafkaPod().withName("kafka-events");

// Properties become:
// kafka-orders.internal.bootstrap-servers
// kafka-events.internal.bootstrap-servers
```

---

## ServiceGroup: Composable Deployment Units

### Core Design

```java
public class ServiceGroup {
    
    private final String name;
    private final List<ServiceGroup> dependencies = new ArrayList<>();
    private final List<TestPod<?>> pods = new ArrayList<>();
    private final PropertyContext propertyContext;
    
    public static ServiceGroup named(String name);
    
    // Add pods
    public ServiceGroup withPod(TestPod<?> pod);
    public ServiceGroup withPod(TestPod<?> pod, Consumer<TestPod<?>> customizer);
    
    // Dependencies - this group starts AFTER dependency groups are ready
    public ServiceGroup dependsOn(ServiceGroup... groups);
    
    // Share property context
    public ServiceGroup withPropertyContext(PropertyContext ctx);
    
    // Lifecycle
    public void start();      // Starts dependencies first (DAG order), then this group's pods in parallel
    public void stop();       // Stops this group first, then dependencies
    public boolean isReady(); // All pods ready
    
    // Access
    public <T extends TestPod<T>> T getPod(Class<T> type);
    public <T extends TestPod<T>> T getPod(String name, Class<T> type);
    public PropertyContext getPropertyContext();
    
    // Failure handling
    public ServiceGroup withFailurePolicy(FailurePolicy policy);
    public ServiceGroup withRetryAttempts(int attempts);
}

public enum FailurePolicy {
    FAIL_FAST,           // Stop immediately, fail test
    CONTINUE_OTHERS,     // Let other pods in group try to start
    RETRY                // Retry failed pod N times
}
```

### Dependency Resolution

Dependencies form a DAG (Directed Acyclic Graph). Startup validates and uses topological sort:

```java
public void start() {
    List<ServiceGroup> sortedGroups = TopologicalSort.sort(this);
    if (sortedGroups == null) {
        throw new CircularDependencyException("Circular dependency detected");
    }
    for (ServiceGroup group : sortedGroups) {
        group.startPodsInParallel();
        group.waitForReady();
    }
}
```

### Fine-Grained vs Coarse-Grained Dependencies

**Coarse-grained (ServiceGroup level)**:
```java
applicationServices.dependsOn(infrastructure);
// ServiceA waits for ALL of infrastructure
```

**Fine-grained (Pod level)**:
```java
ServicePod serviceA = new ServicePod("service-a:latest")
    .dependsOn(kafka)      // Only waits for kafka
    .withEnvFromProperty("KAFKA_BOOTSTRAP_SERVERS", "kafka.internal.bootstrap-servers");
```

**Auto-dependency via `withEnvFromPod`**:
```java
ServicePod serviceA = new ServicePod("service-a:latest")
    .withEnvFromPod(kafka, "KAFKA_BOOTSTRAP_SERVERS", KafkaPod::getInternalBootstrapServers);
    // Implicitly depends on kafka now
```

---

## Reusable ServiceGroup Definitions

### Class-Based Definitions

```java
public abstract class ServiceGroupDefinition {
    
    public abstract String getName();
    
    public List<Class<? extends ServiceGroupDefinition>> dependsOn() {
        return Collections.emptyList();
    }
    
    public Scope getDefaultScope() {
        return Scope.CLASS;
    }
    
    public NamespaceStrategy getNamespaceStrategy() {
        return NamespaceStrategy.SHARED_RANDOM;
    }
    
    protected abstract void define(ServiceGroupBuilder builder, PropertyContext props);
}

// Concrete implementation
public class InfrastructureGroup extends ServiceGroupDefinition {
    
    @Override
    public String getName() {
        return "infrastructure";
    }
    
    @Override
    public Scope getDefaultScope() {
        return Scope.GLOBAL;  // Expensive to start, share across tests
    }
    
    @Override
    protected void define(ServiceGroupBuilder builder, PropertyContext props) {
        builder
            .withPod(new KafkaPod()
                .withKraftMode(true)
                .withTopics("orders", "inventory", "events"))
            .withPod(new MongoDBPod()
                .withCredentials("admin", "secret")
                .withDatabase("testdb"))
            .withPod(new RedisPod());
    }
}

public class CoreServicesGroup extends ServiceGroupDefinition {
    
    @Override
    public String getName() {
        return "core-services";
    }
    
    @Override
    public List<Class<? extends ServiceGroupDefinition>> dependsOn() {
        return List.of(InfrastructureGroup.class);
    }
    
    @Override
    protected void define(ServiceGroupBuilder builder, PropertyContext props) {
        builder
            .withPod(new ServicePod("mycompany/inventory-service:latest")
                .withName("inventory-service")
                .withPort(8080)
                .withEnvFromProperty("KAFKA_BOOTSTRAP_SERVERS", "kafka.internal.bootstrap-servers")
                .withEnvFromProperty("MONGO_URI", "mongodb.internal.uri"))
            .withPod(new ServicePod("mycompany/pricing-service:latest")
                .withName("pricing-service")
                .withPort(8080)
                .withEnvFromProperty("KAFKA_BOOTSTRAP_SERVERS", "kafka.internal.bootstrap-servers"));
    }
}
```

### Alternative: Catalog/Registry Pattern

For large projects:

```java
public class TestPodsCatalog {
    
    public static final ServiceGroupDefinition INFRASTRUCTURE = 
        ServiceGroupDefinition.builder("infrastructure")
            .withPod(new KafkaPod().withKraftMode(true))
            .withPod(new MongoDBPod().withCredentials("admin", "secret"))
            .build();
    
    public static final ServiceGroupDefinition CORE_SERVICES = 
        ServiceGroupDefinition.builder("core-services")
            .dependsOn(INFRASTRUCTURE)
            .withPod(new ServicePod("mycompany/inventory-service:latest")
                .withName("inventory-service")
                .withEnvFromProperty("KAFKA_BOOTSTRAP_SERVERS", "kafka.internal.bootstrap-servers"))
            .build();
    
    public static final ServiceGroupDefinition FULL_STACK = 
        ServiceGroupDefinition.builder("full-stack")
            .includes(INFRASTRUCTURE)
            .includes(CORE_SERVICES)
            .build();
}
```

---

## Lifecycle Scopes

### Scope Enum

```java
public enum Scope {
    METHOD,     // Fresh for each @Test (expensive, but isolated)
    CLASS,      // Shared within test class (default)
    GLOBAL      // Shared across all tests in the JVM run
}
```

### Namespace Strategy

```java
public enum NamespaceStrategy {
    SHARED_RANDOM,      // All groups in one random namespace (test-abc123)
    PER_GROUP,          // Each group gets its own namespace
    FIXED               // Use a specific namespace name
}
```

### Cleanup Strategy

```java
public enum Cleanup {
    IMMEDIATE,          // After scope ends
    ON_JVM_SHUTDOWN,    // Via shutdown hook (default for GLOBAL)
    NEVER               // Leave running (for debugging)
}
```

---

## JUnit 5 Integration

### Annotations

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(TestPodsExtension.class)
public @interface TestPods {
}

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(UseServiceGroups.class)
public @interface UseServiceGroup {
    Class<? extends ServiceGroupDefinition> value();
    Scope scope() default Scope.CLASS;
    Cleanup cleanup() default Cleanup.IMMEDIATE;
}

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface UseCluster {
    Class<? extends ClusterProvider> value();
}

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface UseNamespace {
    NamespaceStrategy strategy() default NamespaceStrategy.SHARED_RANDOM;
    String prefix() default "test";
    String name() default "";  // For FIXED strategy
}

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ServiceGroupRef {
    String value() default "";  // Group name, or empty for type-based lookup
}

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PodRef {
    String value() default "";  // Pod name, or empty for type-based lookup
}

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PropertyContextRef {
}

// For manual registration (programmatic approach)
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RegisterCluster {
}

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RegisterNamespace {
}
```

### Usage: Annotation-Driven

```java
@TestPods
@UseCluster(Minikube.class)
@UseServiceGroup(value = InfrastructureGroup.class, scope = Scope.GLOBAL)
class OrderServiceIT {
    
    @ServiceGroupRef
    static ServiceGroup infrastructure;
    
    @PodRef
    static KafkaPod kafka;
    
    @PodRef
    static MongoDBPod mongo;
    
    @PropertyContextRef
    static PropertyContext props;
    
    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.data.mongodb.uri", mongo::getConnectionString);
    }
    
    @Test
    void shouldProcessOrder() {
        // Infrastructure is running
    }
}
```

### Usage: Programmatic

```java
@ExtendWith(TestPodsExtension.class)
class OrderServiceIT {

    @RegisterCluster
    static K8sCluster cluster = K8sCluster.minikube();
    
    @RegisterNamespace
    static TestNamespace namespace = new TestNamespace(cluster).withRandomSuffix();
    
    @TestPod
    static MongoDBPod mongo = new MongoDBPod()
        .inNamespace(namespace)
        .withCredentials("test", "test");
    
    @TestPod
    static KafkaPod kafka = new KafkaPod()
        .inNamespace(namespace)
        .withKraftMode(true);
    
    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.data.mongodb.uri", mongo::getConnectionString);
    }
    
    @Test
    void shouldProcessOrder() {
        // All pods are started and ready
    }
}
```

### Usage: ServiceGroup Inline

```java
@ExtendWith(TestPodsExtension.class)
class OrderSystemIT {

    static PropertyContext props = new PropertyContext();
    
    @RegisterNamespace
    static TestNamespace namespace = new TestNamespace(K8sCluster.minikube());
    
    @ServiceGroup
    static ServiceGroup infrastructure = ServiceGroup.named("infrastructure")
        .withPropertyContext(props)
        .withPod(new KafkaPod()
            .inNamespace(namespace)
            .withKraftMode(true))
        .withPod(new MongoDBPod()
            .inNamespace(namespace)
            .withCredentials("admin", "secret"));
    
    @ServiceGroup
    static ServiceGroup applicationServices = ServiceGroup.named("app-services")
        .withPropertyContext(props)
        .dependsOn(infrastructure)
        .withPod(new ServicePod("mycompany/inventory-service:latest")
            .inNamespace(namespace)
            .withPort(8080)
            .withReadinessProbe("/actuator/health", 8080)
            .withEnvFromProperty("KAFKA_BOOTSTRAP_SERVERS", "kafka.internal.bootstrap-servers")
            .withEnvFromProperty("MONGO_URI", "mongodb.internal.uri"));
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", 
            () -> props.resolve("kafka.external.bootstrap-servers"));
        registry.add("inventory-service.url", 
            () -> applicationServices.getPod("inventory-service", ServicePod.class).getExternalUrl());
    }
    
    @Test
    void shouldProcessOrderThroughEntireSystem() {
        // All infrastructure + services are running
    }
}
```

---

## Wait Strategies

```java
public interface WaitStrategy {
    
    void waitUntilReady(TestPod<?> pod);
    
    // Factory methods
    static WaitStrategy forReadinessProbe();           // Trust K8s probe
    static WaitStrategy forLogMessage(String regex);
    static WaitStrategy forLogMessage(String regex, int times);
    static WaitStrategy forPort(int port);
    static WaitStrategy forHttp(String path, int port);
    static WaitStrategy forCommand(String... command); // Exit 0
    
    // Modifiers
    default WaitStrategy withTimeout(Duration timeout);
    default WaitStrategy withPollInterval(Duration interval);
    
    // Composition
    static WaitStrategy allOf(WaitStrategy... strategies);
}
```

---

## External Access Strategy

Different clusters need different approaches to reach pods from test code:

```java
public interface ExternalAccessStrategy {
    
    HostAndPort getExternalEndpoint(TestPod<?> pod, int internalPort);
    
    // Implementations
    static ExternalAccessStrategy portForward();      // Works everywhere
    static ExternalAccessStrategy nodePort();         // Minikube-friendly
    static ExternalAccessStrategy loadBalancer();     // Cloud/MetalLB
    static ExternalAccessStrategy minikubeService();  // minikube service URL
}

// Configured at cluster level
K8sCluster cluster = K8sCluster.minikube()
    .withAccessStrategy(ExternalAccessStrategy.minikubeService());
```

---

## Design Principles Summary

| Principle | How It's Applied |
|-----------|------------------|
| **Sensible defaults** | `new MongoDBPod()` works out of the box |
| **Progressive disclosure** | High-level → mid-level (lambda) → low-level (Fabric8) |
| **Familiar patterns** | Mirrors Testcontainers API where sensible |
| **K8s-native waiting** | Readiness probes, not just port checks |
| **Dual connection model** | Internal (`*.internal.*`) vs External (`*.external.*`) |
| **Framework agnostic** | JUnit 5 + Fabric8; Spring via `@DynamicPropertySource` |
| **Type-safe infrastructure** | `MongoDBPod`, `KafkaPod` with domain-specific methods |
| **Escape hatches** | `*Customizer` for full K8s control |
| **Composable groups** | `ServiceGroup` with DAG-based dependencies |
| **Reusable definitions** | `ServiceGroupDefinition` classes or catalog pattern |
| **Scoped lifecycle** | METHOD, CLASS, or GLOBAL scope |
| **Lazy property resolution** | Properties resolved at pod start time |

---

## Multi-Module Project Structure

```
my-project/
├── test-infrastructure/          # Shared test support module
│   └── src/main/java/
│       └── com/mycompany/test/
│           ├── groups/
│           │   ├── InfrastructureGroup.java
│           │   ├── CoreServicesGroup.java
│           │   └── FullStackGroup.java
│           └── TestPodsCatalog.java
│
├── order-service/
│   └── src/test/java/
│       └── OrderServiceIT.java   # Uses InfrastructureGroup
│
├── inventory-service/
│   └── src/test/java/
│       └── InventoryServiceIT.java  # Uses InfrastructureGroup
│
└── system-tests/
    └── src/test/java/
        └── FullSystemIT.java     # Uses FullStackGroup
```

---

## Open Design Questions

1. **Class-based definitions vs builder-based**: Should `ServiceGroupDefinition` be a class to extend or a builder pattern?

2. **Scope defaults**: Should `GLOBAL` be the default for infrastructure groups (optimizing for speed) or `CLASS` (optimizing for isolation)?

3. **Injection style**: Prefer `@PodRef` for individual pods, or always go through `@ServiceGroupRef` and call `group.getPod(KafkaPod.class)`?

4. **Spring-specific module**: Should there be an optional `testpods-spring` module with additional annotations like `@InjectProperty`?

---

## Technology Stack

- **Core**: Java 21+, JUnit 5, Fabric8 Kubernetes Client
- **Build**: Maven multi-module with BOM
- **Distribution**: Maven Central under `org.testpods` groupId
- **Documentation**: MkDocs with Material theme at beta.testpods.org
