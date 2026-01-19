# 20. Brainstorming Notes

<!-- TOC -->
- [20. Brainstorming Notes](#20-brainstorming-notes)
  - [20.1. Session 1: Initial Requirements Capture](#201-session-1-initial-requirements-capture)
  - [20.2. Session 2: Project Structure and Development Approach](#202-session-2-project-structure-and-development-approach)
  - [20.3. Session 3: Advanced Multi-Service Integration Testing](#203-session-3-advanced-multi-service-integration-testing)
  - [20.4. Session 4: Platform Layer and Kubernetes Operators](#204-session-4-platform-layer-and-kubernetes-operators)
<!-- /TOC -->

## 20.1. Session 1: Initial Requirements Capture

*Date: 2026-01-09*

**Key Points from Initial Discussion:**
- Target audience: Java developers writing integration tests for Spring Boot apps
- Primary comparison: Testcontainers library but for Kubernetes
- Initial platform: macOS only (Docker Desktop, Hyperkit drivers)
- Core runtimes: containerd and Docker Engine (extensible to other CRI runtimes)
- Cluster technology: Minikube
- API style: Fluent interface similar to Testcontainers
- Module system: Pre-built modules for common infrastructure + easy custom module creation
- Kubernetes manifest support: Full programmatic access to k8s deployment specifications

**Initial Requirements:**
1. Start/stop lightweight k8s node in Minikube
2. Create, configure, and manage containers as pods
3. Support multiple CRI-compliant runtimes (architecture must be extensible)
4. Built-in modules: MySQL, MongoDB, Kafka, RabbitMQ, and other common infrastructure
5. Easy custom module creation
6. Programmatic API for all k8s manifest deployment options
7. JUnit integration for test lifecycle management

**Next Steps:**
- Refine use cases and user personas
- Detailed API design exploration
- Technical architecture deep-dive
- Module system design
- Runtime abstraction layer design

## 20.2. Session 2: Project Structure and Development Approach

*Date: 2026-01-09*

**Project Artifacts Defined:**
1. **testpods-core**: Main Java library artifact (Maven/Gradle dependency)
2. **testpods-examples**: Example projects demonstrating usage patterns and best practices
3. **website-docs**: Documentation content folder for testpods.org website

**Agentic Development Strategy:**
- Project wrapped in agentic layer for AI-assisted development
- Inspired by IndyDevDan's tactical agentic coding methodology
- Lead developer provides high-level instructions to orchestration layer
- Subagents handle implementation, testing, documentation, and review
- Multi-agent collaboration with planning, implementation, review, and integration agents
- Quality gates including code review, security, performance, and documentation checks
- Human-in-the-loop for strategic decisions and architectural approval
- Agentic infrastructure includes orchestration framework, specialized agents, and automated validation

**Benefits of Agentic Approach:**
- Increased development velocity through parallel agent work
- Consistent code patterns maintained by agents
- Multiple review layers for quality assurance
- Auto-generated and maintained documentation
- Lead developer focuses on architecture while agents handle implementation
- Scalable development across multiple features simultaneously

**Key Principles:**
1. Instruction-driven development (high-level goals → agent decomposition)
2. Multi-agent collaboration (specialized agents for different concerns)
3. Iterative refinement through feedback loops
4. Context-aware development (agents understand patterns and conventions)
5. Quality gates at multiple stages

## 20.3. Session 3: Advanced Multi-Service Integration Testing

*Date: 2026-01-09*

**Problem Statement:**
Testing a Spring Boot service that operates within a larger system requires more than single container management. Developers need to orchestrate:
- Shared infrastructure (databases, message brokers, caches)
- Companion microservices that are part of the same application
- The service under test (started via standard @SpringBootTest)

**Key Requirements Identified:**
1. **Tiered Startup Ordering**: Infrastructure → Companion Services → Service Under Test
2. **Connection Info Propagation**: All components need connection details for shared infrastructure
3. **Readiness Coordination**: Each tier must be ready before the next tier starts
4. **Spring Boot Integration**: Must work seamlessly with @SpringBootTest and @DynamicPropertySource

**Scenario Example: E-Commerce System**
- Infrastructure: PostgreSQL, Kafka, Redis
- Companion Services: inventory-service, notification-service
- Service Under Test: order-service

**API Design Decisions:**
1. **Recommended Approach**: Separate configuration classes (Infrastructure, Services, TestEnvironment)
   - Promotes reusability across test classes
   - Clear separation of concerns
   - Explicit lifecycle control
   - Full IDE support with autocomplete

2. **Alternative Approaches**:
   - Annotation-based configuration for simpler cases
   - YAML declarative configuration for config-as-code teams

**Functional Requirements Added (FR-11):**
- FR-11.1: Infrastructure Declaration
- FR-11.2: Service Declaration
- FR-11.3: Dependency Resolution and Ordering
- FR-11.4: Connection Info Management
- FR-11.5: Spring Boot Test Integration
- FR-11.6: Reusable Test Environments
- FR-11.7: Service Container Configuration

**Design Rationale:**
- Separate Infrastructure and Services for reusability and clarity
- Explicit connection info propagation for transparency and debuggability
- Builder pattern over annotations for complex configurations (IDE support, conditional logic, composition)
- Annotations still available for simpler use cases

## 20.4. Session 4: Platform Layer and Kubernetes Operators

*Date: 2026-01-09*

**Question Explored:**
Is there a concept of "Platform" that sits between Infrastructure (databases, message queues) and Application Services (companion services, service under test)?

**Answer: Yes - Platform Layer Identified**

The Platform layer provides cross-cutting concerns that are:
- Not raw data stores (not Infrastructure)
- Not business logic (not Application Services)
- Shared across multiple services
- Often deployed as Kubernetes operators or platform services

**Four-Layer Model Established:**
```
Layer 4: Application (Service Under Test + Companion Services)
Layer 3: Platform (API Gateway, Auth, Observability, Platform Operators)
Layer 2: Infrastructure (Databases, Message Queues, Caches)
Layer 1: Kubernetes (Minikube cluster, CRDs, RBAC)
```

**Kubernetes Operators at All Layers:**

1. **Infrastructure Operators** (provision data stores):
   - Zalando Postgres Operator, CrunchyData Postgres Operator
   - Strimzi Kafka Operator
   - Redis Operator
   - MongoDB Operator

2. **Platform Operators** (provide platform capabilities):
   - cert-manager (TLS certificates)
   - External Secrets Operator (secrets sync)
   - Prometheus Operator (monitoring)
   - Istio Operator (service mesh)
   - Vault Operator (secrets management)

3. **Custom Business Operators** (future consideration):
   - Custom operators as "service under test"
   - Different testing paradigm (K8s API vs HTTP)

**Infrastructure Deployment Options:**
- **Direct Deployment**: Simple containers, fast startup, sufficient for most tests
- **Operator-Based Deployment**: Production-like, uses CRDs, longer startup, validates operator configs

**Platform Components Identified:**
- Platform Services: API Gateway, Identity Provider, Config Server, Feature Flags, Tracing, Metrics
- Platform Operators: cert-manager, External Secrets, Prometheus Operator, Istio Operator

**Functional Requirements Added (FR-12):**
- FR-12.1: Platform Service Declaration
- FR-12.2: Platform Operator Support (Helm, OLM)
- FR-12.3: Infrastructure Operators
- FR-12.4: Operator Dependencies

**Future Consideration: Operator Testing Mode**

Testing custom K8s operators as "service under test" is a distinct but related use case:
- Different interaction model (K8s API vs HTTP/messaging)
- Different build pipeline (operator-sdk, kubebuilder)
- Different verification (reconciliation, created resources)
- Deferred to future phase as separate module (`testpods-operator-testing`)

**Key Insight:**
Operators appear at all layers, and developers may choose between direct deployment (fast, simple) and operator-based deployment (production-like, validates CRD configurations) for infrastructure components.

---

*Document Status: This is an initial draft PRD structure. Sections will be refined and expanded through collaborative discussion.*
