# 8. Platform Layer and Kubernetes Operators

<!-- TOC -->
- [8. Platform Layer and Kubernetes Operators](#8-platform-layer-and-kubernetes-operators)
  - [8.1. Architectural Layers Overview](#81-architectural-layers-overview)
  - [8.2. Layer Characteristics](#82-layer-characteristics)
  - [8.3. Startup Order](#83-startup-order)
  - [8.4. Platform Components](#84-platform-components)
    - [8.4.1. Platform Services](#841-platform-services)
    - [8.4.2. Platform Operators](#842-platform-operators)
  - [8.5. Infrastructure Deployment Options](#85-infrastructure-deployment-options)
    - [8.5.1. Option 1: Direct Container Deployment (Default)](#851-option-1-direct-container-deployment-default)
    - [8.5.2. Option 2: Operator-Based Deployment](#852-option-2-operator-based-deployment)
  - [8.6. Functional Requirements: Platform Layer](#86-functional-requirements-platform-layer)
    - [8.6.1. FR-12: Platform Layer Support](#861-fr-12-platform-layer-support)
  - [8.7. API Design: Platform Layer](#87-api-design-platform-layer)
    - [8.7.1. Platform Definition Example](#871-platform-definition-example)
    - [8.7.2. Complete Four-Layer Test Environment](#872-complete-four-layer-test-environment)
<!-- /TOC -->

## 8.1. Architectural Layers Overview

The Testpods testing model consists of four distinct layers, each potentially including both direct deployments and Kubernetes Operators:

```
┌─────────────────────────────────────────────────────────────────┐
│                    APPLICATION LAYER                            │
├─────────────────────────────────────────────────────────────────┤
│  Service Under Test          │  Operator Under Test (Future)    │
│  (Spring Boot app)           │  (Custom K8s Operator)           │
│                              │  - Controller code               │
│  Companion Services          │  - CRDs                          │
│  (other microservices)       │  - RBAC                          │
└──────────────────────────────┴──────────────────────────────────┘
                              ▲
                              │ depends on
┌─────────────────────────────────────────────────────────────────┐
│                    PLATFORM LAYER                               │
├─────────────────────────────────────────────────────────────────┤
│  Platform Services           │  Platform Operators              │
│  - API Gateway (Kong)        │  - cert-manager                  │
│  - Auth Server (Keycloak)    │  - External Secrets Operator     │
│  - Config Server             │  - Prometheus Operator           │
│  - Feature Flag Service      │  - Istio Operator                │
│  - Observability (Jaeger)    │  - Vault Operator                │
└──────────────────────────────┴──────────────────────────────────┘
                              ▲
                              │ depends on
┌─────────────────────────────────────────────────────────────────┐
│                  INFRASTRUCTURE LAYER                           │
├─────────────────────────────────────────────────────────────────┤
│  Direct Deployment           │  Via Operators                   │
│  - PostgreSQL container      │  - Postgres Operator → PG cluster│
│  - Kafka container           │  - Strimzi → Kafka cluster       │
│  - Redis container           │  - Redis Operator → Redis cluster│
│  - MongoDB container         │  - MongoDB Operator → MongoDB    │
│                              │                                  │
│  (Simple, fast startup)      │  (Production-like, uses CRDs)    │
└──────────────────────────────┴──────────────────────────────────┘
                              ▲
                              │ runs on
┌─────────────────────────────────────────────────────────────────┐
│                  KUBERNETES LAYER                               │
├─────────────────────────────────────────────────────────────────┤
│  Minikube Cluster                                               │
│  - Container Runtime (containerd / Docker Engine)               │
│  - Installed CRDs                                               │
│  - Namespaces                                                   │
│  - RBAC policies                                                │
└─────────────────────────────────────────────────────────────────┘
```

## 8.2. Layer Characteristics

| Layer | Purpose | Direct Deployment | Operator Deployment |
|-------|---------|-------------------|---------------------|
| Application | Business logic under test | Spring Boot services | Custom business operators |
| Platform | Cross-cutting concerns | Gateway, Auth, Observability | cert-manager, Prometheus Operator |
| Infrastructure | Data persistence & messaging | PostgreSQL, Kafka, Redis | Postgres Operator, Strimzi |
| Kubernetes | Runtime foundation | Minikube cluster | N/A |

## 8.3. Startup Order

The layers start in bottom-up order. Within each layer, operators must complete before the resources or services that depend on them:

```
1. Kubernetes Layer
   └─► Minikube cluster starts
   └─► CRDs installed (for operators at all layers)

2. Infrastructure Layer
   2a. Infrastructure Operators (if using operator mode)
       └─► Zalando Postgres Operator, Strimzi, Redis Operator
       └─► Wait for operator pods ready and CRDs registered
   2b. Infrastructure Resources (parallel where possible)
       ├─► Direct containers: PostgreSQL, Kafka, Redis
       └─► Operator-managed: PostgresCluster CR, Kafka CR → wait for ready

3. Platform Layer
   3a. Platform Operators (sequential or parallel among themselves)
       └─► cert-manager, External Secrets Operator, Prometheus Operator
       └─► Wait for operator pods ready and CRDs registered
   3b. Platform Services (parallel where possible, after 3a completes)
       └─► Keycloak (needs DB from layer 2, may need certs from cert-manager)
       └─► Kong, Jaeger, Config Server

4. Application Layer
   ├─► Companion Services (inventory-service, notification-service)
   └─► Service Under Test (via @SpringBootTest)
```

**Key ordering constraints:**

| Constraint | Reason |
|------------|--------|
| CRDs before operators | Operators need CRDs to watch for custom resources |
| Infrastructure operators before infrastructure CRs | Operator must be running to reconcile CRs |
| Platform operators before platform services | Services may need TLS (cert-manager) or secrets (External Secrets) |
| Infrastructure before platform services | Keycloak needs PostgreSQL, services may need Kafka |
| All layers before application | Application depends on platform and infrastructure |

**Note:** Direct containers and operator-managed resources can coexist in the same test. For example, a test might use a direct Redis container (fast startup) alongside an operator-managed PostgreSQL cluster (production-like).

## 8.4. Platform Components

### 8.4.1. Platform Services

Services that provide cross-cutting platform capabilities:

| Component | Purpose | Example Products |
|-----------|---------|------------------|
| **API Gateway** | Ingress, routing, rate limiting | Kong, Ambassador, Traefik, NGINX |
| **Identity Provider** | OAuth2/OIDC authentication | Keycloak, Dex, Ory Hydra |
| **Config Server** | Externalized configuration | Spring Cloud Config, Consul |
| **Feature Flags** | Runtime feature toggles | Unleash, Flagsmith, LaunchDarkly |
| **Service Registry** | Service discovery (beyond K8s) | Consul, Eureka |
| **Distributed Tracing** | Request tracing | Jaeger, Zipkin, Tempo |
| **Metrics Collection** | Observability metrics | Prometheus, InfluxDB |
| **Log Aggregation** | Centralized logging | Loki, Elasticsearch |
| **Dashboards** | Visualization | Grafana |

### 8.4.2. Platform Operators

Operators that manage platform infrastructure declaratively:

| Operator | Purpose | Manages |
|----------|---------|---------|
| **cert-manager** | TLS certificate lifecycle | Certificate, Issuer CRs |
| **External Secrets Operator** | Sync secrets from external stores | ExternalSecret CRs |
| **Prometheus Operator** | Monitoring stack | Prometheus, ServiceMonitor CRs |
| **Istio Operator** | Service mesh | IstioOperator CRs |
| **Vault Operator** | Secrets management | VaultAuth, VaultSecret CRs |
| **Crossplane** | Cloud resource provisioning | Various cloud resource CRs |

## 8.5. Infrastructure Deployment Options

Testpods supports two approaches for infrastructure, allowing developers to choose based on their needs:

### 8.5.1. Option 1: Direct Container Deployment (Default)

Simple, fast, suitable for most integration tests:

```java
TestpodsInfrastructure.builder()
    .postgresql("db")
        .database("myapp")
        .version("15")
    .endPostgresql()
    .kafka("events")
        .brokers(1)
    .endKafka()
    .build();
```

**Characteristics:**
- Fast startup (seconds)
- Simple configuration
- Sufficient for most testing scenarios
- Lower resource consumption

### 8.5.2. Option 2: Operator-Based Deployment

Production-like, uses Kubernetes operators to provision infrastructure:

```java
TestpodsInfrastructure.builder()
    .postgresqlViaOperator("db")
        .operator(PostgresOperator.ZALANDO)  // or CRUNCHYDATA
        .instances(1)
        .database("myapp")
        .version("15")
        .storageSize("1Gi")
    .endPostgresqlViaOperator()
    .kafkaViaOperator("events")
        .operator(KafkaOperator.STRIMZI)
        .brokers(1)
        .zookeeperNodes(1)
    .endKafkaViaOperator()
    .build();
```

**Characteristics:**
- Closer to production setup
- Tests CRD-based provisioning
- Validates operator configurations
- Longer startup time
- Higher resource consumption

**Use When:**
- Testing operator-specific configurations
- Validating production-like deployments
- Testing HA configurations (replicas, failover)
- Compliance requires operator-based infrastructure

## 8.6. Functional Requirements: Platform Layer

### 8.6.1. FR-12: Platform Layer Support

- **FR-12.1**: **Platform Service Declaration**
  - Declare platform services as a distinct layer
  - Platform services can depend on infrastructure
  - Configure platform services via fluent API
  - Built-in modules for common platform components

- **FR-12.2**: **Platform Operator Support**
  - Install and manage platform operators
  - Support for Helm-based operator installation
  - Support for OLM (Operator Lifecycle Manager) based installation
  - CRD installation and management
  - Operator readiness verification

- **FR-12.3**: **Infrastructure Operators**
  - Support operator-based infrastructure provisioning
  - Abstract differences between direct and operator deployment
  - Same connection info interface regardless of deployment method
  - Support for major operators (Zalando Postgres, Strimzi, Redis Operator)

- **FR-12.4**: **Operator Dependencies**
  - Operators can declare dependencies on infrastructure
  - Operators can declare dependencies on other operators
  - Proper ordering of operator installation
  - CRD availability verification before CR creation

## 8.7. API Design: Platform Layer

### 8.7.1. Platform Definition Example

```java
/**
 * Defines platform components for the e-commerce system.
 */
public class ECommercePlatform {

    private final TestpodsPlatform platform;

    public ECommercePlatform(ECommerceInfrastructure infrastructure) {
        this.platform = TestpodsPlatform.builder()

            // Platform Operator: cert-manager for TLS
            .operator("cert-manager")
                .fromHelm("jetstack/cert-manager")
                .version("1.13.0")
                .namespace("cert-manager")
                .values(Map.of("installCRDs", "true"))
            .endOperator()

            // Platform Service: Keycloak for authentication
            .keycloak("auth")
                .realm("ecommerce")
                .clients(
                    client("order-service")
                        .secret("order-secret")
                        .redirectUris("http://localhost:8080/*"),
                    client("inventory-service")
                        .secret("inventory-secret")
                )
                .users(
                    user("admin").password("admin").roles("admin"),
                    user("customer").password("customer").roles("user")
                )
                .database(infrastructure.postgresql("auth-db"))
                .resources()
                    .memory("512Mi")
                    .cpu("500m")
                .endResources()
            .endKeycloak()

            // Platform Service: Kong API Gateway
            .apiGateway("gateway")
                .type(ApiGatewayType.KONG)
                .routes(
                    route("/api/orders/**").upstream("order-service").stripPrefix(false),
                    route("/api/inventory/**").upstream("inventory-service").stripPrefix(false)
                )
                .plugins(
                    plugin("rate-limiting").config("minute", 100),
                    plugin("jwt").config("claims_to_verify", "exp")
                )
            .endApiGateway()

            // Platform Service: Jaeger for distributed tracing
            .jaeger("tracing")
                .allInOne()  // Single container for testing
                .samplingRate(1.0)  // Sample all traces in tests
            .endJaeger()

            // Platform Operator: Prometheus Operator for monitoring
            .operator("prometheus")
                .fromHelm("prometheus-community/kube-prometheus-stack")
                .version("45.0.0")
                .namespace("monitoring")
                .values(Map.of(
                    "grafana.enabled", "true",
                    "alertmanager.enabled", "false"
                ))
            .endOperator()

            .build();
    }

    public void start() {
        platform.start();
        platform.awaitReady(Duration.ofMinutes(3));
    }

    public void stop() {
        platform.stop();
    }

    // Access platform component endpoints
    public String keycloakUrl() {
        return platform.keycloak("auth").getAuthServerUrl();
    }

    public String gatewayUrl() {
        return platform.apiGateway("gateway").getBaseUrl();
    }

    public String jaegerUrl() {
        return platform.jaeger("tracing").getQueryUrl();
    }

    // Spring properties for platform integration
    public Map<String, String> asSpringProperties() {
        Map<String, String> props = new HashMap<>();

        // Keycloak/OAuth2 properties
        props.put("spring.security.oauth2.resourceserver.jwt.issuer-uri",
            keycloakUrl() + "/realms/ecommerce");

        // Tracing properties
        props.put("management.tracing.enabled", "true");
        props.put("management.zipkin.tracing.endpoint",
            platform.jaeger("tracing").getCollectorUrl() + "/api/traces");

        return props;
    }
}
```

### 8.7.2. Complete Four-Layer Test Environment

```java
/**
 * Complete test environment with all four layers.
 */
public class ECommerceFullTestEnvironment implements TestEnvironment {

    private final ECommerceInfrastructure infrastructure;
    private final ECommercePlatform platform;
    private final ECommerceServices services;
    private boolean started = false;

    public ECommerceFullTestEnvironment() {
        this.infrastructure = new ECommerceInfrastructure();
        this.platform = new ECommercePlatform(infrastructure);
        this.services = new ECommerceServices(infrastructure, platform);
    }

    @Override
    public void start() {
        if (!started) {
            // Layer 1: Infrastructure
            infrastructure.start();

            // Layer 2: Platform
            platform.start();

            // Layer 3: Companion Services
            services.start();

            // Layer 4: Service Under Test started by Spring Boot Test
            started = true;
        }
    }

    @Override
    public void stop() {
        if (started) {
            services.stop();
            platform.stop();
            infrastructure.stop();
            started = false;
        }
    }

    public Map<String, String> getSpringProperties() {
        Map<String, String> properties = new HashMap<>();
        properties.putAll(infrastructure.asSpringProperties());
        properties.putAll(platform.asSpringProperties());
        properties.put("inventory.service.url", services.inventoryServiceUrl());
        properties.put("notification.service.url", services.notificationServiceUrl());
        return properties;
    }

    // Component access for test assertions
    public ECommerceInfrastructure infrastructure() { return infrastructure; }
    public ECommercePlatform platform() { return platform; }
    public ECommerceServices services() { return services; }
}
```
