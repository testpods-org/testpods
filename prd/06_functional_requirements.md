# 6. Functional Requirements

<!-- TOC -->
- [6. Functional Requirements](#6-functional-requirements)
  - [6.1. FR-1: Cluster Management](#61-fr-1-cluster-management)
  - [6.2. FR-2: Container/Pod Management](#62-fr-2-containerpod-management)
  - [6.3. FR-3: Kubernetes Resources](#63-fr-3-kubernetes-resources)
  - [6.4. FR-4: Networking](#64-fr-4-networking)
  - [6.5. FR-5: Pre-built Modules](#65-fr-5-pre-built-modules)
  - [6.6. FR-6: Custom Container Support](#66-fr-6-custom-container-support)
  - [6.7. FR-7: JUnit Integration](#67-fr-7-junit-integration)
  - [6.8. FR-8: Fluent API](#68-fr-8-fluent-api)
  - [6.9. FR-9: Debugging and Observability](#69-fr-9-debugging-and-observability)
  - [6.10. FR-10: Runtime Abstraction](#610-fr-10-runtime-abstraction)
  - [6.11. FR-13: Property Context and Spring Integration](#611-fr-13-property-context-and-spring-integration)
  - [6.12. FR-14: TestPodGroup and TestPodCatalog](#612-fr-14-testpodgroup-and-testpodcatalog)
<!-- /TOC -->

## 6.1. FR-1: Cluster Management

- **FR-1.1**: Provide configuration for connecting to the local minikube with different profiles and default namespaces
- **FR-1.2**: Create/delete/manage test namespaces in the minikube instance
- **FR-1.3**: Reuse existing cluster across test runs when possible
- **FR-1.4**: Provide cluster cleanup/reset capabilities
- **FR-1.5**: Support custom Minikube configuration parameters

## 6.2. FR-2: Container/Pod Management

- **FR-2.1**: Create pods from container images
- **FR-2.2**: Support multi-container pods
- **FR-2.3**: Configure pod resource limits and requests
- **FR-2.4**: Support init containers
- **FR-2.5**: Manage pod lifecycle (create, start, stop, delete)
- **FR-2.6**: Support pod restart policies
- **FR-2.7**: Configure liveness and readiness probes

## 6.3. FR-3: Kubernetes Resources

- **FR-3.1**: Create and manage Services for pod exposure
- **FR-3.2**: Create and inject ConfigMaps
- **FR-3.3**: Create and inject Secrets
- **FR-3.4**: Support PersistentVolumeClaims for stateful workloads
- **FR-3.5**: Support volume mounts (hostPath, emptyDir, configMap, secret)
- **FR-3.6**: Support environment variable injection

## 6.4. FR-4: Networking

- **FR-4.1**: Expose pod ports to test host
- **FR-4.2**: Support Kubernetes service discovery (DNS)
- **FR-4.3**: Provide direct pod IP access when needed
- **FR-4.4**: Support NodePort services for external access
- **FR-4.5**: Enable port-forward functionality

## 6.5. FR-5: Pre-built Modules

- **FR-5.1**: Provide modules for common databases (PostgreSQL, MySQL, MongoDB, Redis)
- **FR-5.2**: Provide modules for message platforms (Kafka with KRaft mode, RabbitMQ)
- **FR-5.3**: Each module should have sensible defaults
- **FR-5.4**: Modules should be customizable via fluent API
- **FR-5.5**: Module initialization should include readiness checks
- **FR-5.6**: MVP Priority: PostgreSQL and Kafka (KRaft, no ZooKeeper)

## 6.6. FR-6: Custom Container Support

- **FR-6.1**: Allow developers to define custom containers via `GenericTestPod`
- **FR-6.2**: Support all Kubernetes container spec options programmatically via `withPodCustomizer()`
- **FR-6.3**: Enable creation of reusable custom modules by extending `DeploymentPod` or `StatefulSetPod`
- **FR-6.4**: Support loading from Kubernetes YAML manifests (future)

## 6.7. FR-7: JUnit Integration

### 6.7.1. Core Extension

- **FR-7.1**: Provide JUnit 5 extension via `@TestPods` class-level annotation
- **FR-7.2**: Support `@Pod` annotation on fields for automatic lifecycle management
- **FR-7.3**: Support both static fields (class-level sharing) and instance fields (per-test isolation)
- **FR-7.4**: Support parallel test execution via random namespace suffix

### 6.7.2. Field Injection and Lifecycle

- **FR-7.5**: Discover `@Pod` annotated fields and manage their lifecycle
- **FR-7.6**: Start static `@Pod` fields in `@BeforeAll`, stop in `@AfterAll`
- **FR-7.7**: Start instance `@Pod` fields before each test, stop after each test
- **FR-7.8**: Inject connection information into test methods via direct accessor methods

### 6.7.3. Dependency Management

- **FR-7.9**: Support `@DependsOn("podName")` annotation for explicit startup ordering
- **FR-7.10**: Start pods in parallel by default when no dependencies declared
- **FR-7.11**: Build dependency graph and respect startup order based on declared dependencies
- **FR-7.12**: Support sequential startup via `@TestPodGroup(parallel=false)` with registration order

### 6.7.4. Namespace Management

- **FR-7.13**: Create one namespace per test class with random suffix for isolation
- **FR-7.14**: Support namespace override via `@TestPods(namespace="...")` or `@Pod(namespace="...")`
- **FR-7.15**: Configurable cleanup policy: `MANAGED` (default), `NAMESPACE`, or `NONE`

## 6.8. FR-8: Fluent API

- **FR-8.1**: Provide builder-style API similar to Testcontainers
- **FR-8.2**: Support method chaining for configuration
- **FR-8.3**: Provide type-safe configuration options
- **FR-8.4**: Enable declarative container definition
- **FR-8.5**: Use generics with `SELF` type parameter for fluent return types

## 6.9. FR-9: Debugging and Observability

### 6.9.1. Core Observability

- **FR-9.1**: Provide access to container logs via `getLogs()` methods
- **FR-9.2**: Enable exec into running containers via `exec(String... command)`
- **FR-9.3**: Expose pod events and status via `isRunning()` and `isReady()`
- **FR-9.4**: Provide detailed error messages for common failure scenarios

### 6.9.2. Debug Mode

- **FR-9.5**: Support `@TestPods(debug=true)` to keep pods running after test failure
- **FR-9.6**: Support `@TestPods(failFast=true|false)` for configurable failure behavior
- **FR-9.7**: When `failFast=false`, continue starting other pods for partial inspection

### 6.9.3. Development Mode

- **FR-9.8**: Support `@TestPods(devMode=true)` for long-lived local development
- **FR-9.9**: In dev mode, start pods and wait for shutdown signal without running tests
- **FR-9.10**: Enable IDE connection to cluster pods for development workflows
- **FR-9.11**: Future: Support remote debugging into cluster pods from IDE

## 6.10. FR-10: Runtime Abstraction

- **FR-10.1**: Abstract CRI-compliant runtime details
- **FR-10.2**: Support containerd runtime
- **FR-10.3**: Support Docker Engine runtime
- **FR-10.4**: Design for future runtime additions
- **FR-10.5**: Validate runtime compatibility

## 6.11. FR-13: Property Context and Spring Integration

### 6.11.1. Property Publishing

- **FR-13.1**: Pods publish connection properties via `publishProperties(PropertyContext)`
- **FR-13.2**: External properties by default: `{pod}.host`, `{pod}.port`, `{pod}.uri`
- **FR-13.3**: Internal properties on request: `{pod}.internal.host`, `{pod}.internal.port`, `{pod}.internal.uri`
- **FR-13.4**: Support template interpolation: `${pod.property}` resolved at start time

### 6.11.2. Hierarchical PropertyContext

- **FR-13.5**: Each TestPodGroup has its own PropertyContext
- **FR-13.6**: PropertyContext inherits from dependency groups (infrastructure -> services -> test)
- **FR-13.7**: Later groups can read properties from earlier groups
- **FR-13.8**: Test code can read all accumulated properties

### 6.11.3. Spring Boot Integration

- **FR-13.9**: Native integration with `@DynamicPropertySource` for property injection
- **FR-13.10**: Support `@Pod(springProperties=true)` to also publish standard Spring property names
- **FR-13.11**: Configurable mapping between pod properties and Spring Boot properties
- **FR-13.12**: Future: Framework-agnostic adapters for Quarkus, Micronaut

## 6.12. FR-14: TestPodGroup and TestPodCatalog

### 6.12.1. TestPodGroup

- **FR-14.1**: Group related pods that share lifecycle and namespace
- **FR-14.2**: Support inline grouping via `@Pod(group="groupName")` attribute
- **FR-14.3**: Support explicit groups via `TestPodGroup.builder().add(pod1).add(pod2).build()`
- **FR-14.4**: Groups can declare dependencies on other groups via `@DependsOn` or `.dependsOn()`
- **FR-14.5**: Support parallel (default) and sequential startup within groups

### 6.12.2. TestPodCatalog

- **FR-14.6**: Typed registry for pod definitions with fluent accessor methods
- **FR-14.7**: Register pods via `TestPodCatalog.builder().register(kafkaPod).register(postgresPod).build()`
- **FR-14.8**: Access pods via typed methods: `catalog.kafkaPod()`, `catalog.postgresPod()`
- **FR-14.9**: Create groups from catalog: `catalog.kafkaPod().postgresPod().asGroup()`
- **FR-14.10**: Implementation via dynamic proxy with method interception (consider Java 21+ features)
- **FR-14.11**: No string-based lookups - all access via typed method names derived from variable names
