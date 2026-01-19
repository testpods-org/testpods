<!-- vscode-markdown-toc -->
* 1. [Executive Summary](#ExecutiveSummary)
	* 1.1. [Vision](#Vision)
	* 1.2. [Problem Statement](#ProblemStatement)
	* 1.3. [Solution Overview](#SolutionOverview)
* 2. [Goals and Objectives](#GoalsandObjectives)
	* 2.1. [Primary Goals](#PrimaryGoals)
	* 2.2. [Secondary Goals](#SecondaryGoals)
	* 2.3. [Non-Goals (Initial Release)](#Non-GoalsInitialRelease)
* 3. [Project Structure and Development Approach](#ProjectStructureandDevelopmentApproach)
	* 3.1. [Project Structure](#ProjectStructure)
	* 3.2. [Development Approach: Agentic Engineering](#DevelopmentApproach:AgenticEngineering)
		* 3.2.1. [Agentic Layer Architecture](#AgenticLayerArchitecture)
		* 3.2.2. [Tactical Agentic Coding Principles](#TacticalAgenticCodingPrinciples)
		* 3.2.3. [Development Workflow](#DevelopmentWorkflow)
		* 3.2.4. [Benefits of Agentic Approach](#BenefitsofAgenticApproach)
		* 3.2.5. [Agentic Development Infrastructure](#AgenticDevelopmentInfrastructure)
		* 3.2.6. [Evolution and Learning](#EvolutionandLearning)
* 4. [User Personas](#UserPersonas)
	* 4.1. [Primary Persona: Java Backend Developer](#PrimaryPersona:JavaBackendDeveloper)
	* 4.2. [Secondary Persona: DevOps/Platform Engineer](#SecondaryPersona:DevOpsPlatformEngineer)
* 5. [Use Cases](#UseCases)
	* 5.1. [UC-1: Basic Database Integration Test](#UC-1:BasicDatabaseIntegrationTest)
	* 5.2. [UC-2: Multi-Container Pod Testing](#UC-2:Multi-ContainerPodTesting)
	* 5.3. [UC-3: ConfigMap and Secret Management](#UC-3:ConfigMapandSecretManagement)
	* 5.4. [UC-4: Network Policy Testing](#UC-4:NetworkPolicyTesting)
	* 5.5. [UC-5: Custom Module Creation](#UC-5:CustomModuleCreation)
	* 5.6. [UC-6: Multi-Service Integration](#UC-6:Multi-ServiceIntegration)
* 6. [Functional Requirements](#FunctionalRequirements)
	* 6.1. [FR-1: Cluster Management](#FR-1:ClusterManagement)
	* 6.2. [FR-2: Container/Pod Management](#FR-2:ContainerPodManagement)
	* 6.3. [FR-3: Kubernetes Resources](#FR-3:KubernetesResources)
	* 6.4. [FR-4: Networking](#FR-4:Networking)
	* 6.5. [FR-5: Pre-built Modules](#FR-5:Pre-builtModules)
	* 6.6. [FR-6: Custom Container Support](#FR-6:CustomContainerSupport)
	* 6.7. [FR-7: JUnit Integration](#FR-7:JUnitIntegration)
	* 6.8. [FR-8: Fluent API](#FR-8:FluentAPI)
	* 6.9. [FR-9: Debugging and Observability](#FR-9:DebuggingandObservability)
	* 6.10. [FR-10: Runtime Abstraction](#FR-10:RuntimeAbstraction)
* 7. [Advanced Multi-Service Integration Testing](#AdvancedMulti-ServiceIntegrationTesting)
	* 7.1. [Scenario Overview](#ScenarioOverview)
	* 7.2. [Detailed Scenario: E-Commerce Order Service](#DetailedScenario:E-CommerceOrderService)
	* 7.3. [Functional Requirements: Multi-Service Integration](#FunctionalRequirements:Multi-ServiceIntegration)
		* 7.3.1. [FR-11: Test Environment Composition](#FR-11:TestEnvironmentComposition)
	* 7.4. [API Design Examples](#APIDesignExamples)
		* 7.4.1. [Approach 1: Separate Configuration Classes (Recommended)](#Approach1:SeparateConfigurationClassesRecommended)
		* 7.4.2. [Approach 2: JUnit Extension with Annotations](#Approach2:JUnitExtensionwithAnnotations)
		* 7.4.3. [Approach 3: Declarative YAML Configuration](#Approach3:DeclarativeYAMLConfiguration)
	* 7.5. [Connection Info Propagation Details](#ConnectionInfoPropagationDetails)
		* 7.5.1. [Automatic Property Mapping](#AutomaticPropertyMapping)
		* 7.5.2. [Custom Property Mapping](#CustomPropertyMapping)
		* 7.5.3. [Environment Variable Conventions](#EnvironmentVariableConventions)
	* 7.6. [Lifecycle Coordination Diagram](#LifecycleCoordinationDiagram)
	* 7.7. [Design Considerations](#DesignConsiderations)
		* 7.7.1. [Why Separate Infrastructure and Services?](#WhySeparateInfrastructureandServices)
		* 7.7.2. [Why Explicit Connection Info Propagation?](#WhyExplicitConnectionInfoPropagation)
		* 7.7.3. [Why Builder Pattern over Annotations?](#WhyBuilderPatternoverAnnotations)
* 8. [Platform Layer and Kubernetes Operators](#PlatformLayerandKubernetesOperators)
	* 8.1. [Architectural Layers Overview](#ArchitecturalLayersOverview)
	* 8.2. [Layer Characteristics](#LayerCharacteristics)
	* 8.3. [Startup Order](#StartupOrder)
	* 8.4. [Platform Components](#PlatformComponents)
		* 8.4.1. [Platform Services](#PlatformServices)
		* 8.4.2. [Platform Operators](#PlatformOperators)
	* 8.5. [Infrastructure Deployment Options](#InfrastructureDeploymentOptions)
		* 8.5.1. [Option 1: Direct Container Deployment (Default)](#Option1:DirectContainerDeploymentDefault)
		* 8.5.2. [Option 2: Operator-Based Deployment](#Option2:Operator-BasedDeployment)
	* 8.6. [Functional Requirements: Platform Layer](#FunctionalRequirements:PlatformLayer)
		* 8.6.1. [FR-12: Platform Layer Support](#FR-12:PlatformLayerSupport)
	* 8.7. [API Design: Platform Layer](#APIDesign:PlatformLayer)
		* 8.7.1. [Platform Definition Example](#PlatformDefinitionExample)
		* 8.7.2. [Complete Four-Layer Test Environment](#CompleteFour-LayerTestEnvironment)
* 9. [Future Consideration: Operator Testing Mode](#FutureConsideration:OperatorTestingMode)
	* 9.1. [Overview](#Overview)
	* 9.2. [Operator Testing vs Service Testing](#OperatorTestingvsServiceTesting)
	* 9.3. [Conceptual API for Operator Testing](#ConceptualAPIforOperatorTesting)
	* 9.4. [Requirements for Operator Testing (Future)](#RequirementsforOperatorTestingFuture)
	* 9.5. [Scope Decision](#ScopeDecision)
* 10. [Non-Functional Requirements](#Non-FunctionalRequirements)
	* 10.1. [NFR-1: Performance](#NFR-1:Performance)
	* 10.2. [NFR-2: Resource Efficiency](#NFR-2:ResourceEfficiency)
	* 10.3. [NFR-3: Reliability](#NFR-3:Reliability)
	* 10.4. [NFR-4: Usability](#NFR-4:Usability)
	* 10.5. [NFR-5: Compatibility](#NFR-5:Compatibility)
	* 10.6. [NFR-6: Maintainability](#NFR-6:Maintainability)
	* 10.7. [NFR-7: Security](#NFR-7:Security)
* 11. [Technical Architecture (High-Level)](#TechnicalArchitectureHigh-Level)
	* 11.1. [Component Overview](#ComponentOverview)
		* 11.1.1. [Core Components](#CoreComponents)
		* 11.1.2. [Module System](#ModuleSystem)
	* 11.2. [Runtime Support Architecture](#RuntimeSupportArchitecture)
	* 11.3. [Technology Stack (Initial Assumptions)](#TechnologyStackInitialAssumptions)
* 12. [API Design Principles](#APIDesignPrinciples)
	* 12.1. [Fluent Interface Pattern](#FluentInterfacePattern)
	* 12.2. [Declarative Configuration](#DeclarativeConfiguration)
	* 12.3. [Module System](#ModuleSystem-1)
* 13. [Module Specifications](#ModuleSpecifications)
	* 13.1. [Built-in Modules (Phase 1)](#Built-inModulesPhase1)
		* 13.1.1. [Database Modules](#DatabaseModules)
		* 13.1.2. [Messaging Modules](#MessagingModules)
	* 13.2. [Module Interface Requirements](#ModuleInterfaceRequirements)
* 14. [Success Metrics](#SuccessMetrics)
	* 14.1. [Adoption Metrics](#AdoptionMetrics)
	* 14.2. [Technical Metrics](#TechnicalMetrics)
	* 14.3. [Developer Experience Metrics](#DeveloperExperienceMetrics)
* 15. [Dependencies and Prerequisites](#DependenciesandPrerequisites)
	* 15.1. [External Dependencies](#ExternalDependencies)
	* 15.2. [Library Dependencies (to be determined)](#LibraryDependenciestobedetermined)
* 16. [Risks and Mitigations](#RisksandMitigations)
	* 16.1. [Risk-1: Minikube Stability](#Risk-1:MinikubeStability)
	* 16.2. [Risk-2: Runtime Compatibility](#Risk-2:RuntimeCompatibility)
	* 16.3. [Risk-3: Resource Consumption](#Risk-3:ResourceConsumption)
	* 16.4. [Risk-4: Complexity vs. Testcontainers](#Risk-4:Complexityvs.Testcontainers)
	* 16.5. [Risk-5: Cross-Platform Support](#Risk-5:Cross-PlatformSupport)
* 17. [Development Phases (High-Level)](#DevelopmentPhasesHigh-Level)
	* 17.1. [Phase 1: Foundation (MVP)](#Phase1:FoundationMVP)
	* 17.2. [Phase 2: Core Features](#Phase2:CoreFeatures)
	* 17.3. [Phase 3: Advanced Features](#Phase3:AdvancedFeatures)
	* 17.4. [Phase 4: Polish and Ecosystem](#Phase4:PolishandEcosystem)
* 18. [Open Questions and Discussion Points](#OpenQuestionsandDiscussionPoints)
	* 18.1. [Technical Decisions](#TechnicalDecisions)
	* 18.2. [API Design Questions](#APIDesignQuestions)
	* 18.3. [Runtime Questions](#RuntimeQuestions)
	* 18.4. [Module System Questions](#ModuleSystemQuestions)
	* 18.5. [Performance Questions](#PerformanceQuestions)
* 19. [Appendices](#Appendices)
	* 19.1. [Appendix A: Testcontainers Comparison](#AppendixA:TestcontainersComparison)
	* 19.2. [Appendix B: Kubernetes Concepts Quick Reference](#AppendixB:KubernetesConceptsQuickReference)
	* 19.3. [Appendix C: Glossary](#AppendixC:Glossary)
* 20. [Brainstorming Notes](#BrainstormingNotes)
	* 20.1. [Session 1: Initial Requirements Capture](#Session1:InitialRequirementsCapture)
	* 20.2. [Session 2: Project Structure and Development Approach](#Session2:ProjectStructureandDevelopmentApproach)
	* 20.3. [Session 3: Advanced Multi-Service Integration Testing](#Session3:AdvancedMulti-ServiceIntegrationTesting)
	* 20.4. [Session 4: Platform Layer and Kubernetes Operators](#Session4:PlatformLayerandKubernetesOperators)

<!-- vscode-markdown-toc-config
	numbering=true
	autoSave=true
	/vscode-markdown-toc-config -->
<!-- /vscode-markdown-toc --># Testpods Library - Product Requirements Document

**Version:** 0.1.0 (Draft)
**Last Updated:** 2026-01-09
**Status:** Initial Draft - Brainstorming Phase

---

##  1. <a name='ExecutiveSummary'></a>Executive Summary

###  1.1. <a name='Vision'></a>Vision
Testpods is a Java testing library that enables developers to write integration tests for Spring Boot and other Java applications by managing containerized dependencies in a local Kubernetes environment. Similar to Testcontainers but specifically designed for Kubernetes, Testpods bridges the gap between container-based testing and real-world Kubernetes deployments.

###  1.2. <a name='ProblemStatement'></a>Problem Statement
Current integration testing approaches using Testcontainers provide excellent Docker-based testing but don't capture the complexities of Kubernetes deployments including:
- Pod networking and service discovery
- ConfigMaps and Secrets management
- Resource constraints and scheduling
- Multi-container pod configurations
- Kubernetes-specific failure modes

###  1.3. <a name='SolutionOverview'></a>Solution Overview
Testpods provides a JUnit-integrated library that:
- Manages a lightweight local Kubernetes node via Minikube
- Offers a fluent API for defining and deploying containerized dependencies as Kubernetes pods
- Supports multiple CRI-compliant container runtimes
- Includes pre-built modules for common infrastructure components
- Enables developers to test applications in an environment that mirrors production Kubernetes deployments

---

##  2. <a name='GoalsandObjectives'></a>Goals and Objectives

###  2.1. <a name='PrimaryGoals'></a>Primary Goals
1. **Kubernetes-Native Testing**: Enable integration tests that run in actual Kubernetes pods rather than standalone Docker containers
2. **Developer Experience**: Provide an intuitive, fluent API similar to Testcontainers for defining test dependencies
3. **Runtime Flexibility**: Support multiple CRI-compliant runtimes (initially containerd and Docker Engine)
4. **Extensibility**: Make it trivial to add new infrastructure modules and create custom container configurations
5. **Production Parity**: Allow tests to use the same Kubernetes manifests and configurations used in production

###  2.2. <a name='SecondaryGoals'></a>Secondary Goals
1. Fast test execution through efficient cluster management and caching
2. Minimal resource footprint for local development machines
3. Rich debugging capabilities with easy access to logs and cluster state
4. Integration with popular Java testing frameworks (JUnit 5 initially)

###  2.3. <a name='Non-GoalsInitialRelease'></a>Non-Goals (Initial Release)
- Windows and Linux support (macOS only initially)
- Production Kubernetes cluster management
- Multi-node cluster support
- Advanced Kubernetes features (operators, CRDs, Helm)
- Cloud provider integrations

---

##  3. <a name='ProjectStructureandDevelopmentApproach'></a>Project Structure and Development Approach

###  3.1. <a name='ProjectStructure'></a>Project Structure

The Testpods project consists of three main artifacts:

1. **testpods-core** (Java Library Artifact)
   - The main Java library containing the core Testpods framework
   - Runtime adapters for CRI-compliant container runtimes
   - Built-in modules for common infrastructure components
   - JUnit integration and fluent API
   - Distributed as Maven/Gradle dependency

2. **testpods-examples** (Java Examples Artifact)
   - Comprehensive example projects demonstrating Testpods usage
   - Integration test examples for various scenarios
   - Module usage demonstrations
   - Custom module creation examples
   - Best practices and patterns
   - Serves as both documentation and validation of the library

3. **website-docs** (Documentation Website)
   - Content for testpods.org documentation site
   - Getting started guides
   - API reference documentation
   - Module documentation
   - Tutorials and how-to guides
   - Architecture and design documentation
   - Published to testpods.org

###  3.2. <a name='DevelopmentApproach:AgenticEngineering'></a>Development Approach: Agentic Engineering

The Testpods project employs an **agentic development strategy** inspired by IndyDevDan's tactical agentic coding methodology. This approach fundamentally changes how the project is developed and evolved.

####  3.2.1. <a name='AgenticLayerArchitecture'></a>Agentic Layer Architecture

The project is wrapped in an agentic layer that enables the lead developer to orchestrate development through AI agents rather than direct implementation:

```
┌─────────────────────────────────────────────────┐
│         Lead Developer (Strategic)              │
│         High-level instructions & goals         │
└────────────────────┬────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────┐
│           Agentic Orchestration Layer           │
│  - Task decomposition                           │
│  - Agent coordination                           │
│  - Code generation & review                     │
│  - Testing and validation                       │
└────────────────────┬────────────────────────────┘
                     │
        ┌────────────┴────────────┐
        │                         │
┌───────▼────────┐      ┌────────▼──────────┐
│  Implementation │      │  Review & Quality  │
│    Subagents    │      │    Subagents       │
│                 │      │                    │
│ - Code writers  │      │ - Code reviewers   │
│ - Test writers  │      │ - Security check   │
│ - Doc writers   │      │ - Performance eval │
└────────┬────────┘      └────────┬───────────┘
         │                        │
         └────────────┬───────────┘
                      │
         ┌────────────▼────────────┐
         │   Testpods Codebase     │
         │ (core, examples, docs)  │
         └─────────────────────────┘
```

####  3.2.2. <a name='TacticalAgenticCodingPrinciples'></a>Tactical Agentic Coding Principles

Based on IndyDevDan's agentic engineering methodology, the development approach follows these principles:

1. **Instruction-Driven Development**
   - Lead developer provides high-level instructions and goals
   - Agentic layer decomposes into actionable tasks
   - Subagents implement specific components with guidance

2. **Multi-Agent Collaboration**
   - **Planning Agents**: Break down features into implementation plans
   - **Implementation Agents**: Write Java code, tests, and documentation
   - **Review Agents**: Code review, quality checks, security analysis
   - **Documentation Agents**: Generate and maintain docs from code
   - **Integration Agents**: Ensure components work together

3. **Iterative Refinement**
   - Initial implementation by code generation agents
   - Review and critique by specialized review agents
   - Refinement based on feedback loops
   - Validation through automated testing

4. **Context-Aware Development**
   - Agents maintain understanding of:
     - Overall project architecture
     - Existing patterns and conventions
     - Java and Kubernetes best practices
     - Testcontainers API patterns for consistency
   - Code generation aligns with established patterns

5. **Quality Gates**
   - Automated code review by review agents
   - Security scanning for vulnerabilities
   - Performance analysis for resource usage
   - Documentation completeness checks
   - Test coverage validation

####  3.2.3. <a name='DevelopmentWorkflow'></a>Development Workflow

**Typical Feature Implementation Flow:**

1. **Lead Developer**: "Implement PostgreSQL module with connection pooling support"

2. **Planning Agent**:
   - Analyzes existing module patterns
   - Creates implementation plan
   - Identifies required components (module class, tests, docs)

3. **Implementation Agents**:
   - **Code Agent**: Generates PostgreSQLContainer class
   - **Test Agent**: Creates integration tests
   - **Doc Agent**: Writes module documentation

4. **Review Agents**:
   - **Code Reviewer**: Checks adherence to patterns, quality
   - **Security Agent**: Scans for security issues
   - **Performance Agent**: Validates resource usage

5. **Integration Agent**: Ensures module integrates with core framework

6. **Lead Developer**: Reviews final output, provides refinement instructions if needed

####  3.2.4. <a name='BenefitsofAgenticApproach'></a>Benefits of Agentic Approach

1. **Velocity**: Rapid implementation of well-structured code
2. **Consistency**: Agents maintain patterns across codebase
3. **Quality**: Multiple review layers catch issues early
4. **Documentation**: Auto-generated and maintained
5. **Learning**: Lead developer focuses on architecture, agents handle implementation details
6. **Scalability**: Can parallelize development across multiple features
7. **Maintainability**: Consistent code patterns make future changes easier

####  3.2.5. <a name='AgenticDevelopmentInfrastructure'></a>Agentic Development Infrastructure

**Required Components:**
- Agent orchestration framework (based on tactical agentic coding patterns)
- Code generation agents with Java/Kubernetes expertise
- Review agents with specialized knowledge domains
- Documentation generation pipeline
- Automated testing and validation framework
- Version control integration for agent-generated code

**Human-in-the-Loop:**
- Lead developer provides strategic direction
- Architectural decisions require human approval
- Critical code changes reviewed by lead developer
- Agent refinement based on feedback

####  3.2.6. <a name='EvolutionandLearning'></a>Evolution and Learning

The agentic layer itself evolves:
- Agents learn from code review feedback
- Patterns codified into agent instructions
- Best practices discovered during development become agent knowledge
- Continuous improvement of agent capabilities

---

##  4. <a name='UserPersonas'></a>User Personas

###  4.1. <a name='PrimaryPersona:JavaBackendDeveloper'></a>Primary Persona: Java Backend Developer
- **Context**: Develops Spring Boot microservices that interact with databases, message queues, and other services
- **Needs**:
  - Fast, reliable integration tests
  - Test environment that matches production Kubernetes setup
  - Easy setup without deep Kubernetes knowledge
- **Pain Points**:
  - Current Docker-based tests don't catch Kubernetes-specific issues
  - Setting up local Kubernetes for testing is complex and slow
  - Differences between test and production environments cause bugs

###  4.2. <a name='SecondaryPersona:DevOpsPlatformEngineer'></a>Secondary Persona: DevOps/Platform Engineer
- **Context**: Maintains testing infrastructure and CI/CD pipelines
- **Needs**:
  - Consistent test environments across team members
  - Ability to customize cluster configuration
  - Integration with existing CI/CD systems
- **Pain Points**:
  - Developers' local tests don't match CI environment
  - Hard to debug test failures in containerized environments
  - Resource consumption of test infrastructure

---

##  5. <a name='UseCases'></a>Use Cases

###  5.1. <a name='UC-1:BasicDatabaseIntegrationTest'></a>UC-1: Basic Database Integration Test
**Actor**: Java Backend Developer
**Goal**: Test a Spring Boot application with a PostgreSQL database running in a Kubernetes pod

**Flow**:
1. Developer annotates JUnit test with `@Testpods`
2. Test defines a PostgreSQL container using fluent API
3. Testpods starts Minikube cluster (if not running)
4. Testpods deploys PostgreSQL as a pod with service
5. Test application connects to PostgreSQL via Kubernetes service DNS
6. Test executes application logic
7. Testpods tears down pod after test completion

###  5.2. <a name='UC-2:Multi-ContainerPodTesting'></a>UC-2: Multi-Container Pod Testing
**Actor**: Java Backend Developer
**Goal**: Test application with a pod containing main container and sidecar (e.g., log forwarder)

###  5.3. <a name='UC-3:ConfigMapandSecretManagement'></a>UC-3: ConfigMap and Secret Management
**Actor**: Java Backend Developer
**Goal**: Test application configuration injection via Kubernetes ConfigMaps and Secrets

###  5.4. <a name='UC-4:NetworkPolicyTesting'></a>UC-4: Network Policy Testing
**Actor**: Java Backend Developer
**Goal**: Verify application behavior under specific network policies

###  5.5. <a name='UC-5:CustomModuleCreation'></a>UC-5: Custom Module Creation
**Actor**: Java Backend Developer
**Goal**: Create a reusable module for a proprietary internal service

###  5.6. <a name='UC-6:Multi-ServiceIntegration'></a>UC-6: Multi-Service Integration
**Actor**: Java Backend Developer
**Goal**: Test application with multiple dependencies (database, message queue, cache)

---

##  6. <a name='FunctionalRequirements'></a>Functional Requirements

###  6.1. <a name='FR-1:ClusterManagement'></a>FR-1: Cluster Management
- **FR-1.1**: Automatically start/stop Minikube instance
- **FR-1.2**: Support multiple Minikube driver options (Docker Desktop, Hyperkit on macOS)
- **FR-1.3**: Configure Minikube with specified container runtime (containerd, Docker Engine)
- **FR-1.4**: Reuse existing cluster across test runs when possible
- **FR-1.5**: Provide cluster cleanup/reset capabilities
- **FR-1.6**: Support custom Minikube configuration parameters

###  6.2. <a name='FR-2:ContainerPodManagement'></a>FR-2: Container/Pod Management
- **FR-2.1**: Create pods from container images
- **FR-2.2**: Support multi-container pods
- **FR-2.3**: Configure pod resource limits and requests
- **FR-2.4**: Support init containers
- **FR-2.5**: Manage pod lifecycle (create, start, stop, delete)
- **FR-2.6**: Support pod restart policies
- **FR-2.7**: Configure liveness and readiness probes

###  6.3. <a name='FR-3:KubernetesResources'></a>FR-3: Kubernetes Resources
- **FR-3.1**: Create and manage Services for pod exposure
- **FR-3.2**: Create and inject ConfigMaps
- **FR-3.3**: Create and inject Secrets
- **FR-3.4**: Support PersistentVolumeClaims for stateful workloads
- **FR-3.5**: Support volume mounts (hostPath, emptyDir, configMap, secret)
- **FR-3.6**: Support environment variable injection

###  6.4. <a name='FR-4:Networking'></a>FR-4: Networking
- **FR-4.1**: Expose pod ports to test host
- **FR-4.2**: Support Kubernetes service discovery (DNS)
- **FR-4.3**: Provide direct pod IP access when needed
- **FR-4.4**: Support NodePort services for external access
- **FR-4.5**: Enable port-forward functionality

###  6.5. <a name='FR-5:Pre-builtModules'></a>FR-5: Pre-built Modules
- **FR-5.1**: Provide modules for common databases (PostgreSQL, MySQL, MongoDB, Redis)
- **FR-5.2**: Provide modules for message platforms (Kafka, RabbitMQ)
- **FR-5.3**: Each module should have sensible defaults
- **FR-5.4**: Modules should be customizable via fluent API
- **FR-5.5**: Module initialization should include readiness checks

###  6.6. <a name='FR-6:CustomContainerSupport'></a>FR-6: Custom Container Support
- **FR-6.1**: Allow developers to define custom containers
- **FR-6.2**: Support all Kubernetes container spec options programmatically
- **FR-6.3**: Enable creation of reusable custom modules
- **FR-6.4**: Support loading from Kubernetes YAML manifests

###  6.7. <a name='FR-7:JUnitIntegration'></a>FR-7: JUnit Integration
- **FR-7.1**: Provide JUnit 5 extension for automatic lifecycle management
- **FR-7.2**: Support test class-level and method-level scoping
- **FR-7.3**: Inject container connection info into test methods
- **FR-7.4**: Support parallel test execution where safe

###  6.8. <a name='FR-8:FluentAPI'></a>FR-8: Fluent API
- **FR-8.1**: Provide builder-style API similar to Testcontainers
- **FR-8.2**: Support method chaining for configuration
- **FR-8.3**: Provide type-safe configuration options
- **FR-8.4**: Enable declarative container definition

###  6.9. <a name='FR-9:DebuggingandObservability'></a>FR-9: Debugging and Observability
- **FR-9.1**: Provide access to container logs
- **FR-9.2**: Enable exec into running containers
- **FR-9.3**: Expose pod events and status
- **FR-9.4**: Support debugging mode (keep cluster running after test failure)
- **FR-9.5**: Provide detailed error messages for common failure scenarios

###  6.10. <a name='FR-10:RuntimeAbstraction'></a>FR-10: Runtime Abstraction
- **FR-10.1**: Abstract CRI-compliant runtime details
- **FR-10.2**: Support containerd runtime
- **FR-10.3**: Support Docker Engine runtime
- **FR-10.4**: Design for future runtime additions
- **FR-10.5**: Validate runtime compatibility

---

##  7. <a name='AdvancedMulti-ServiceIntegrationTesting'></a>Advanced Multi-Service Integration Testing

###  7.1. <a name='ScenarioOverview'></a>Scenario Overview

A critical use case for Testpods is testing a Spring Boot service that operates within a larger system consisting of:
- **Shared infrastructure**: Databases, message brokers, caches (e.g., Kafka, PostgreSQL, Redis)
- **Companion services**: Other Spring Boot microservices that are part of the same application/system
- **Service under test**: The Spring Boot application being tested, started via standard `@SpringBootTest`

The testing challenge is orchestrating all these components with proper:
1. **Startup ordering**: Infrastructure → Companion services → Service under test
2. **Connection info propagation**: All services need connection details for shared infrastructure
3. **Readiness coordination**: Each tier must be fully ready before the next tier starts
4. **Spring Boot integration**: The service under test uses standard Spring Boot Test mechanisms

###  7.2. <a name='DetailedScenario:E-CommerceOrderService'></a>Detailed Scenario: E-Commerce Order Service

**System Components:**
- **Infrastructure:**
  - PostgreSQL (shared database)
  - Kafka (event streaming)
  - Redis (caching/session store)

- **Companion Services:**
  - `inventory-service`: Manages product inventory, consumes Kafka events, uses PostgreSQL
  - `notification-service`: Sends notifications, consumes Kafka events, uses Redis for rate limiting

- **Service Under Test:**
  - `order-service`: Processes orders, publishes Kafka events, uses PostgreSQL, calls inventory-service

**Test Requirements:**
1. Start infrastructure and wait for readiness
2. Inject infrastructure connection info into companion services
3. Start companion services and wait for readiness
4. Make infrastructure connection info available to Spring Boot Test context
5. Spring Boot Test starts order-service with injected properties
6. Execute integration tests that exercise the full system
7. Tear down in reverse order

###  7.3. <a name='FunctionalRequirements:Multi-ServiceIntegration'></a>Functional Requirements: Multi-Service Integration

####  7.3.1. <a name='FR-11:TestEnvironmentComposition'></a>FR-11: Test Environment Composition

- **FR-11.1**: **Infrastructure Declaration**
  - Declare multiple infrastructure components as a cohesive unit
  - Each component has a unique name for reference
  - Components are individually configurable
  - Infrastructure starts as a single coordinated operation
  - Readiness checks ensure all components are available before proceeding

- **FR-11.2**: **Service Declaration**
  - Declare multiple application services as a cohesive unit
  - Services can declare dependencies on infrastructure components
  - Services can declare dependencies on other services
  - Services automatically receive connection info from declared dependencies
  - Environment variables and/or configuration files injected automatically

- **FR-11.3**: **Dependency Resolution and Ordering**
  - Automatic startup ordering based on declared dependencies
  - Infrastructure tier always starts before dependent services
  - Inter-service dependencies respected (service A depends on service B)
  - Parallel startup within a tier where dependencies allow
  - Configurable timeouts for each tier's readiness

- **FR-11.4**: **Connection Info Management**
  - Automatic extraction of connection info from started infrastructure
  - Connection info available in multiple formats:
    - Spring Boot properties (`spring.datasource.url`, `spring.kafka.bootstrap-servers`, etc.)
    - Environment variables (`DATABASE_URL`, `KAFKA_BROKERS`, etc.)
    - Programmatic access (Java objects with typed accessors)
  - Single source of truth for connection info
  - Support for custom connection info mappings

- **FR-11.5**: **Spring Boot Test Integration**
  - Seamless integration with `@SpringBootTest`
  - Support for `@DynamicPropertySource` for property injection
  - Lifecycle coordination with Spring TestContext
  - Test environment ready before Spring context initialization
  - Support for `@TestConfiguration` to customize test context

- **FR-11.6**: **Reusable Test Environments**
  - Define test environments in separate Java classes
  - Test environments shareable across multiple test classes
  - Support for environment inheritance and composition
  - Autowiring support for test environment components
  - Environment variants (e.g., minimal vs. full infrastructure)

- **FR-11.7**: **Service Container Configuration**
  - Full Kubernetes deployment options for companion services
  - Environment variable injection from infrastructure
  - Resource limits and requests
  - Health checks and readiness probes
  - Service exposure and inter-service networking
  - Volume mounts for configuration files

###  7.4. <a name='APIDesignExamples'></a>API Design Examples

The following examples illustrate the developer experience for multi-service integration testing. These are conceptual designs to be refined during implementation.

####  7.4.1. <a name='Approach1:SeparateConfigurationClassesRecommended'></a>Approach 1: Separate Configuration Classes (Recommended)

This approach separates infrastructure and services into reusable configuration classes that can be composed and shared across tests.

**Infrastructure Definition:**
```java
/**
 * Defines the shared infrastructure for the e-commerce system.
 * Can be reused across multiple test classes.
 */
public class ECommerceInfrastructure {

    private final TestpodsInfrastructure infrastructure;

    public ECommerceInfrastructure() {
        this.infrastructure = TestpodsInfrastructure.builder()
            .postgresql("orders-db")
                .database("ecommerce")
                .schema("orders", "inventory", "notifications")
                .username("app")
                .password("secret")
                .initScript("classpath:db/init.sql")
                .resources()
                    .memory("512Mi")
                    .cpu("500m")
                .endResources()
            .endPostgresql()

            .kafka("events")
                .brokers(1)
                .topics(
                    topic("order-events").partitions(3).replication(1),
                    topic("inventory-events").partitions(3).replication(1),
                    topic("notification-events").partitions(1).replication(1)
                )
                .resources()
                    .memory("1Gi")
                    .cpu("1000m")
                .endResources()
            .endKafka()

            .redis("cache")
                .maxMemory("256mb")
                .evictionPolicy(EvictionPolicy.ALLKEYS_LRU)
            .endRedis()

            .build();
    }

    public void start() {
        infrastructure.start();
        infrastructure.awaitReady(Duration.ofMinutes(2));
    }

    public void stop() {
        infrastructure.stop();
    }

    // Typed accessors for connection info
    public PostgresConnectionInfo postgresConnection() {
        return infrastructure.postgresql("orders-db").connectionInfo();
    }

    public KafkaConnectionInfo kafkaConnection() {
        return infrastructure.kafka("events").connectionInfo();
    }

    public RedisConnectionInfo redisConnection() {
        return infrastructure.redis("cache").connectionInfo();
    }

    // Spring Boot property injection
    public Map<String, String> asSpringProperties() {
        return infrastructure.toSpringProperties();
    }

    // Environment variables for container injection
    public Map<String, String> asEnvironment() {
        return infrastructure.toEnvironmentVariables();
    }
}
```

**Companion Services Definition:**
```java
/**
 * Defines companion services that integrate with the infrastructure.
 */
public class ECommerceServices {

    private final TestpodsServices services;

    public ECommerceServices(ECommerceInfrastructure infrastructure) {
        this.services = TestpodsServices.builder()
            .service("inventory-service")
                .image("mycompany/inventory-service:latest")
                .environment(infrastructure.asEnvironment())
                .environment("SERVICE_NAME", "inventory-service")
                .environment("LOG_LEVEL", "DEBUG")
                .port(8081)
                .healthCheck()
                    .httpGet("/actuator/health")
                    .port(8081)
                    .initialDelay(Duration.ofSeconds(10))
                    .period(Duration.ofSeconds(5))
                .endHealthCheck()
                .readinessProbe()
                    .httpGet("/actuator/health/readiness")
                    .port(8081)
                .endReadinessProbe()
                .resources()
                    .memory("512Mi")
                    .cpu("500m")
                .endResources()
            .endService()

            .service("notification-service")
                .image("mycompany/notification-service:latest")
                .environment(infrastructure.asEnvironment())
                .environment("SERVICE_NAME", "notification-service")
                .environment("SMTP_HOST", "mailhog")  // mock mail server
                .port(8082)
                .healthCheck()
                    .httpGet("/actuator/health")
                    .port(8082)
                .endHealthCheck()
            .endService()

            .build();
    }

    public void start() {
        services.start();
        services.awaitReady(Duration.ofMinutes(1));
    }

    public void stop() {
        services.stop();
    }

    // Get base URLs for companion services
    public String inventoryServiceUrl() {
        return services.service("inventory-service").getBaseUrl();
    }

    public String notificationServiceUrl() {
        return services.service("notification-service").getBaseUrl();
    }
}
```

**Composed Test Environment:**
```java
/**
 * Complete test environment combining infrastructure and services.
 * Manages lifecycle and provides unified access to connection info.
 */
public class ECommerceTestEnvironment implements TestEnvironment {

    private final ECommerceInfrastructure infrastructure;
    private final ECommerceServices services;
    private boolean started = false;

    public ECommerceTestEnvironment() {
        this.infrastructure = new ECommerceInfrastructure();
        this.services = new ECommerceServices(infrastructure);
    }

    @Override
    public void start() {
        if (!started) {
            // Ordered startup: infrastructure first, then services
            infrastructure.start();
            services.start();
            started = true;
        }
    }

    @Override
    public void stop() {
        if (started) {
            // Reverse order shutdown
            services.stop();
            infrastructure.stop();
            started = false;
        }
    }

    // Expose all connection properties for Spring Boot Test
    public Map<String, String> getSpringProperties() {
        Map<String, String> properties = new HashMap<>(infrastructure.asSpringProperties());
        properties.put("inventory.service.url", services.inventoryServiceUrl());
        properties.put("notification.service.url", services.notificationServiceUrl());
        return properties;
    }

    // Direct access to components for test assertions
    public ECommerceInfrastructure infrastructure() {
        return infrastructure;
    }

    public ECommerceServices services() {
        return services;
    }
}
```

**JUnit Test Using the Environment:**
```java
@SpringBootTest(classes = OrderServiceApplication.class)
@Testpods
class OrderServiceIntegrationTest {

    // Shared environment instance - started once for all tests in class
    static ECommerceTestEnvironment testEnv = new ECommerceTestEnvironment();

    @BeforeAll
    static void startEnvironment() {
        testEnv.start();
    }

    @AfterAll
    static void stopEnvironment() {
        testEnv.stop();
    }

    // Inject infrastructure connection properties into Spring Boot Test context
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        testEnv.getSpringProperties().forEach(registry::add);
    }

    // Spring beans from the application under test
    @Autowired
    private OrderService orderService;

    @Autowired
    private KafkaTemplate<String, OrderEvent> kafkaTemplate;

    @Test
    void shouldCreateOrderAndPublishEvent() {
        // Arrange
        CreateOrderRequest request = new CreateOrderRequest("SKU-123", 2);

        // Act
        Order order = orderService.createOrder(request);

        // Assert - verify order created
        assertThat(order.getId()).isNotNull();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);

        // Assert - verify event published to Kafka and processed by inventory-service
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            // Query inventory-service to verify it received the event
            ResponseEntity<InventoryResponse> response = restTemplate.getForEntity(
                testEnv.services().inventoryServiceUrl() + "/api/inventory/SKU-123",
                InventoryResponse.class
            );
            assertThat(response.getBody().getReservedQuantity()).isEqualTo(2);
        });
    }

    @Test
    void shouldHandleInventoryServiceFailure() {
        // Test resilience when inventory-service is unavailable
        testEnv.services().service("inventory-service").stop();

        try {
            CreateOrderRequest request = new CreateOrderRequest("SKU-456", 1);
            Order order = orderService.createOrder(request);

            // Should still create order with PENDING_INVENTORY status
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_INVENTORY);
        } finally {
            testEnv.services().service("inventory-service").start();
        }
    }
}
```

####  7.4.2. <a name='Approach2:JUnitExtensionwithAnnotations'></a>Approach 2: JUnit Extension with Annotations

For simpler cases or when preferring annotation-based configuration:

```java
@SpringBootTest
@TestpodsEnvironment(
    infrastructure = @Infrastructure(
        postgresql = @PostgreSQL(
            name = "db",
            database = "ecommerce",
            initScript = "classpath:db/init.sql"
        ),
        kafka = @Kafka(
            name = "events",
            topics = {"order-events", "inventory-events"}
        ),
        redis = @Redis(name = "cache")
    ),
    services = @Services({
        @Service(
            name = "inventory-service",
            image = "mycompany/inventory-service:latest",
            port = 8081,
            connectsTo = {"db", "events"}
        ),
        @Service(
            name = "notification-service",
            image = "mycompany/notification-service:latest",
            port = 8082,
            connectsTo = {"events", "cache"}
        )
    })
)
class OrderServiceIntegrationTest {

    @Autowired
    TestpodsContext testpods;  // Injected by Testpods extension

    @Autowired
    OrderService orderService;

    @Test
    void shouldProcessOrder() {
        // testpods.services().get("inventory-service") available for assertions
        // ...
    }
}
```

####  7.4.3. <a name='Approach3:DeclarativeYAMLConfiguration'></a>Approach 3: Declarative YAML Configuration

For teams preferring configuration-as-code or sharing environments across language boundaries:

**test-environment.yaml:**
```yaml
apiVersion: testpods/v1
kind: TestEnvironment
metadata:
  name: ecommerce-integration
spec:
  infrastructure:
    postgresql:
      - name: orders-db
        database: ecommerce
        initScript: classpath:db/init.sql
        resources:
          memory: 512Mi
          cpu: 500m
    kafka:
      - name: events
        brokers: 1
        topics:
          - name: order-events
            partitions: 3
          - name: inventory-events
            partitions: 3
    redis:
      - name: cache
        maxMemory: 256mb

  services:
    - name: inventory-service
      image: mycompany/inventory-service:latest
      port: 8081
      connectsTo: [orders-db, events]
      environment:
        SERVICE_NAME: inventory-service
      healthCheck:
        httpGet:
          path: /actuator/health
          port: 8081

    - name: notification-service
      image: mycompany/notification-service:latest
      port: 8082
      connectsTo: [events, cache]

  springProperties:
    mapping:
      orders-db: spring.datasource
      events: spring.kafka
      cache: spring.data.redis
```

**Java test using YAML:**
```java
@SpringBootTest
@Testpods
@TestEnvironmentConfig("classpath:test-environment.yaml")
class OrderServiceIntegrationTest {

    @Autowired
    TestpodsEnvironment env;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        TestpodsEnvironment.loadFromConfig("classpath:test-environment.yaml")
            .getSpringProperties()
            .forEach(registry::add);
    }

    // ... tests
}
```

###  7.5. <a name='ConnectionInfoPropagationDetails'></a>Connection Info Propagation Details

####  7.5.1. <a name='AutomaticPropertyMapping'></a>Automatic Property Mapping

Testpods automatically maps infrastructure connection info to standard Spring Boot properties:

| Infrastructure | Spring Property | Example Value |
|---------------|-----------------|---------------|
| PostgreSQL | `spring.datasource.url` | `jdbc:postgresql://orders-db:5432/ecommerce` |
| PostgreSQL | `spring.datasource.username` | `app` |
| PostgreSQL | `spring.datasource.password` | `secret` |
| Kafka | `spring.kafka.bootstrap-servers` | `events-kafka:9092` |
| Redis | `spring.data.redis.host` | `cache-redis` |
| Redis | `spring.data.redis.port` | `6379` |

####  7.5.2. <a name='CustomPropertyMapping'></a>Custom Property Mapping

```java
infrastructure.toSpringProperties(PropertyMapping.builder()
    .map("orders-db").toPrefix("spring.datasource")
    .map("events").toPrefix("app.messaging.kafka")
    .map("cache").toPrefix("app.cache.redis")
    .build()
);
```

####  7.5.3. <a name='EnvironmentVariableConventions'></a>Environment Variable Conventions

For injection into companion service containers:

```java
infrastructure.toEnvironmentVariables(EnvVarNaming.builder()
    .convention(EnvVarNaming.SCREAMING_SNAKE_CASE)
    .prefix("APP_")
    .build()
);
// Produces: APP_DATABASE_URL, APP_DATABASE_USERNAME, APP_KAFKA_BROKERS, etc.
```

###  7.6. <a name='LifecycleCoordinationDiagram'></a>Lifecycle Coordination Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           Test Lifecycle                                    │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  1. @BeforeAll / Testpods Extension Initialization                          │
│     │                                                                       │
│     ├─► Start Infrastructure (Parallel where possible)                      │
│     │   ├─► PostgreSQL ──► Wait for ready ─┐                                │
│     │   ├─► Kafka ───────► Wait for ready ─┼─► Infrastructure Ready         │
│     │   └─► Redis ───────► Wait for ready ─┘                                │
│     │                                                                       │
│     ├─► Extract Connection Info from Infrastructure                         │
│     │                                                                       │
│     ├─► Start Companion Services (Respecting dependencies)                  │
│     │   ├─► inventory-service (inject env vars) ──► Wait for ready ─┐       │
│     │   └─► notification-service (inject env vars) ► Wait for ready ─┼─►    │
│     │                                                      Services Ready   │
│     │                                                                       │
│     └─► Make Connection Info Available to Spring Test Context               │
│                                                                             │
│  2. Spring TestContext Initialization                                       │
│     │                                                                       │
│     ├─► @DynamicPropertySource populates Spring Environment                 │
│     └─► Spring Boot Application starts (order-service)                      │
│                                                                             │
│  3. Test Methods Execute                                                    │
│     │                                                                       │
│     ├─► @Test methods run against fully integrated system                   │
│     └─► Access to testEnv for assertions and control                        │
│                                                                             │
│  4. @AfterAll / Cleanup                                                     │
│     │                                                                       │
│     ├─► Stop Companion Services                                             │
│     ├─► Stop Infrastructure                                                 │
│     └─► Cleanup Kubernetes resources                                        │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

###  7.7. <a name='DesignConsiderations'></a>Design Considerations

####  7.7.1. <a name='WhySeparateInfrastructureandServices'></a>Why Separate Infrastructure and Services?

1. **Reusability**: Infrastructure definitions can be shared across multiple test classes
2. **Clarity**: Clear separation between stateless infrastructure and application services
3. **Flexibility**: Different tests can use same infrastructure with different service configurations
4. **Lifecycle Control**: Infrastructure can be started once and reused across test suites

####  7.7.2. <a name='WhyExplicitConnectionInfoPropagation'></a>Why Explicit Connection Info Propagation?

1. **Transparency**: Developers see exactly what connection info is being injected
2. **Debuggability**: Easy to inspect connection strings when tests fail
3. **Customization**: Allows overriding or transforming connection info as needed
4. **Framework Independence**: Works with Spring Boot, Quarkus, Micronaut, or plain Java

####  7.7.3. <a name='WhyBuilderPatternoverAnnotations'></a>Why Builder Pattern over Annotations?

1. **IDE Support**: Full autocomplete and type checking during construction
2. **Conditional Logic**: Can programmatically vary configuration based on conditions
3. **Composition**: Builders can be composed and extended
4. **Readability**: Complex configurations remain readable with proper indentation
5. **Annotations Available**: Annotation-based approach available for simpler cases

---

##  8. <a name='PlatformLayerandKubernetesOperators'></a>Platform Layer and Kubernetes Operators

###  8.1. <a name='ArchitecturalLayersOverview'></a>Architectural Layers Overview

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

###  8.2. <a name='LayerCharacteristics'></a>Layer Characteristics

| Layer | Purpose | Direct Deployment | Operator Deployment |
|-------|---------|-------------------|---------------------|
| Application | Business logic under test | Spring Boot services | Custom business operators |
| Platform | Cross-cutting concerns | Gateway, Auth, Observability | cert-manager, Prometheus Operator |
| Infrastructure | Data persistence & messaging | PostgreSQL, Kafka, Redis | Postgres Operator, Strimzi |
| Kubernetes | Runtime foundation | Minikube cluster | N/A |

###  8.3. <a name='StartupOrder'></a>Startup Order

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

###  8.4. <a name='PlatformComponents'></a>Platform Components

####  8.4.1. <a name='PlatformServices'></a>Platform Services

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

####  8.4.2. <a name='PlatformOperators'></a>Platform Operators

Operators that manage platform infrastructure declaratively:

| Operator | Purpose | Manages |
|----------|---------|---------|
| **cert-manager** | TLS certificate lifecycle | Certificate, Issuer CRs |
| **External Secrets Operator** | Sync secrets from external stores | ExternalSecret CRs |
| **Prometheus Operator** | Monitoring stack | Prometheus, ServiceMonitor CRs |
| **Istio Operator** | Service mesh | IstioOperator CRs |
| **Vault Operator** | Secrets management | VaultAuth, VaultSecret CRs |
| **Crossplane** | Cloud resource provisioning | Various cloud resource CRs |

###  8.5. <a name='InfrastructureDeploymentOptions'></a>Infrastructure Deployment Options

Testpods supports two approaches for infrastructure, allowing developers to choose based on their needs:

####  8.5.1. <a name='Option1:DirectContainerDeploymentDefault'></a>Option 1: Direct Container Deployment (Default)

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

####  8.5.2. <a name='Option2:Operator-BasedDeployment'></a>Option 2: Operator-Based Deployment

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

###  8.6. <a name='FunctionalRequirements:PlatformLayer'></a>Functional Requirements: Platform Layer

####  8.6.1. <a name='FR-12:PlatformLayerSupport'></a>FR-12: Platform Layer Support

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

###  8.7. <a name='APIDesign:PlatformLayer'></a>API Design: Platform Layer

####  8.7.1. <a name='PlatformDefinitionExample'></a>Platform Definition Example

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

####  8.7.2. <a name='CompleteFour-LayerTestEnvironment'></a>Complete Four-Layer Test Environment

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

---

##  9. <a name='FutureConsideration:OperatorTestingMode'></a>Future Consideration: Operator Testing Mode

###  9.1. <a name='Overview'></a>Overview

A future enhancement to Testpods could support testing custom Kubernetes Operators as the "service under test" rather than traditional Spring Boot applications. This is a distinct but related use case.

###  9.2. <a name='OperatorTestingvsServiceTesting'></a>Operator Testing vs Service Testing

| Aspect | Service Testing (Current) | Operator Testing (Future) |
|--------|---------------------------|---------------------------|
| Service Under Test | Spring Boot application | Kubernetes Controller/Operator |
| Deployment | Container image → Pod | Controller + CRDs + RBAC |
| Test Interaction | HTTP/REST, messaging, database | Kubernetes API (create/watch CRs) |
| Verification | Response bodies, state, events | Reconciliation, created resources |
| Build Step | Maven/Gradle → JAR → Image | operator-sdk/kubebuilder → Image |
| Runtime | JVM in container | Controller watching K8s API |

###  9.3. <a name='ConceptualAPIforOperatorTesting'></a>Conceptual API for Operator Testing

```java
@Testpods
class OrderProcessorOperatorTest {

    static ECommerceInfrastructure infra = new ECommerceInfrastructure();

    // Operator under test - built and deployed to cluster
    @OperatorUnderTest
    static TestpodsOperator operator = TestpodsOperator.builder()
        .name("order-processor-operator")
        .fromLocalSource("./operator")
            .buildTool(BuildTool.OPERATOR_SDK)
        .withCRDs("classpath:crds/*.yaml")
        .withRBAC("classpath:rbac/")
        .namespace("order-system")
        .dependsOn(infra)
        .build();

    @Autowired
    KubernetesClient k8sClient;

    @Test
    void shouldReconcileOrderRequest() {
        // Create Custom Resource
        OrderRequest order = new OrderRequestBuilder()
            .withNewMetadata()
                .withName("test-order-001")
                .withNamespace("order-system")
            .endMetadata()
            .withNewSpec()
                .withCustomerId("CUST-123")
                .withItems(List.of(
                    new OrderItem("SKU-001", 2),
                    new OrderItem("SKU-002", 1)
                ))
            .endSpec()
            .build();

        k8sClient.resource(order).create();

        // Verify operator reconciliation
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            OrderRequest result = k8sClient
                .resources(OrderRequest.class)
                .inNamespace("order-system")
                .withName("test-order-001")
                .get();

            assertThat(result.getStatus().getPhase()).isEqualTo("Processed");
        });

        // Verify operator created downstream resources
        List<Job> jobs = k8sClient.batch().v1().jobs()
            .inNamespace("order-system")
            .withLabel("order-id", "test-order-001")
            .list().getItems();

        assertThat(jobs).hasSize(1);
    }
}
```

###  9.4. <a name='RequirementsforOperatorTestingFuture'></a>Requirements for Operator Testing (Future)

1. **Build Integration**: Support for operator-sdk, kubebuilder, ko, jib
2. **CRD Management**: Install CRDs before operator deployment
3. **RBAC Setup**: Create ServiceAccounts, Roles, RoleBindings
4. **Kubernetes Client Integration**: Inject Fabric8 or official K8s client
5. **CR Type Generation**: Generate Java classes from CRD schemas
6. **Reconciliation Verification**: Tools to verify operator behavior

###  9.5. <a name='ScopeDecision'></a>Scope Decision

Operator testing is **deferred to a future phase** because:
- Core service testing use case must be solid first
- Operator testing has additional complexity (build tools, CRDs, RBAC)
- Different target audience (platform engineers vs application developers)
- Can be added as a separate module (`testpods-operator-testing`)

---

##  10. <a name='Non-FunctionalRequirements'></a>Non-Functional Requirements

###  10.1. <a name='NFR-1:Performance'></a>NFR-1: Performance
- **NFR-1.1**: Cluster startup time < 60 seconds (subsequent starts)
- **NFR-1.2**: Pod deployment time < 10 seconds for simple containers
- **NFR-1.3**: Cluster reuse should reduce test suite overhead to < 5 seconds
- **NFR-1.4**: Support concurrent test execution where possible

###  10.2. <a name='NFR-2:ResourceEfficiency'></a>NFR-2: Resource Efficiency
- **NFR-2.1**: Minikube cluster should use minimal resources (< 2GB RAM default)
- **NFR-2.2**: Automatic cleanup of stopped pods and unused resources
- **NFR-2.3**: Support resource limits configuration

###  10.3. <a name='NFR-3:Reliability'></a>NFR-3: Reliability
- **NFR-3.1**: Handle transient network failures gracefully
- **NFR-3.2**: Detect and recover from cluster failures
- **NFR-3.3**: Ensure proper cleanup even on test failure/abort
- **NFR-3.4**: Validate cluster health before running tests

###  10.4. <a name='NFR-4:Usability'></a>NFR-4: Usability
- **NFR-4.1**: Clear documentation with examples for all modules
- **NFR-4.2**: Helpful error messages with troubleshooting guidance
- **NFR-4.3**: Minimal required configuration for basic use cases
- **NFR-4.4**: IDE auto-completion support for fluent API

###  10.5. <a name='NFR-5:Compatibility'></a>NFR-5: Compatibility
- **NFR-5.1**: Support Java 11+ (consider LTS versions)
- **NFR-5.2**: Compatible with JUnit 5.x
- **NFR-5.3**: Support macOS 12+ (Monterey and later)
- **NFR-5.4**: Compatible with Minikube 1.30+
- **NFR-5.5**: Support Spring Boot 2.7+ and 3.x

###  10.6. <a name='NFR-6:Maintainability'></a>NFR-6: Maintainability
- **NFR-6.1**: Modular architecture for easy extension
- **NFR-6.2**: Clear separation between core and modules
- **NFR-6.3**: Comprehensive test coverage (>80%)
- **NFR-6.4**: Follow Java best practices and conventions

###  10.7. <a name='NFR-7:Security'></a>NFR-7: Security
- **NFR-7.1**: Don't expose sensitive data in logs
- **NFR-7.2**: Support secure credential injection
- **NFR-7.3**: Isolate test environments from production

---

##  11. <a name='TechnicalArchitectureHigh-Level'></a>Technical Architecture (High-Level)

###  11.1. <a name='ComponentOverview'></a>Component Overview

####  11.1.1. <a name='CoreComponents'></a>Core Components
1. **Cluster Manager**: Minikube lifecycle management
2. **Runtime Adapter**: CRI abstraction layer
3. **Resource Manager**: Kubernetes resource creation/management
4. **Lifecycle Coordinator**: Test lifecycle integration
5. **API Layer**: Fluent interface implementation

####  11.1.2. <a name='ModuleSystem'></a>Module System
1. **Module Registry**: Discovery and loading of modules
2. **Base Module**: Abstract class for all modules
3. **Built-in Modules**: Pre-configured infrastructure components
4. **Custom Modules**: User-defined extensions

###  11.2. <a name='RuntimeSupportArchitecture'></a>Runtime Support Architecture

```
┌─────────────────────────────────────┐
│       Testpods Fluent API           │
└─────────────────┬───────────────────┘
                  │
┌─────────────────▼───────────────────┐
│      Runtime Adapter Interface      │
└─────────────────┬───────────────────┘
                  │
        ┌─────────┴─────────┐
        │                   │
┌───────▼────────┐  ┌──────▼──────────┐
│   Containerd   │  │  Docker Engine  │
│    Adapter     │  │     Adapter     │
└───────┬────────┘  └──────┬──────────┘
        │                   │
        └─────────┬─────────┘
                  │
        ┌─────────▼─────────┐
        │     Minikube      │
        └───────────────────┘
```

###  11.3. <a name='TechnologyStackInitialAssumptions'></a>Technology Stack (Initial Assumptions)
- **Language**: Java 17 (LTS)
- **Build Tool**: Maven or Gradle
- **Kubernetes Client**: Fabric8 Kubernetes Client or Official Kubernetes Java Client
- **Testing Framework**: JUnit 5
- **Minikube**: Version 1.30+
- **Container Runtimes**: containerd, Docker Engine

---

##  12. <a name='APIDesignPrinciples'></a>API Design Principles

###  12.1. <a name='FluentInterfacePattern'></a>Fluent Interface Pattern
```java
// Example conceptual API (to be refined)
PostgreSQLContainer postgres = new PostgreSQLContainer()
    .withDatabaseName("testdb")
    .withUsername("user")
    .withPassword("password")
    .withResourceLimits("500m", "512Mi")
    .withPersistentStorage("1Gi")
    .start();
```

###  12.2. <a name='DeclarativeConfiguration'></a>Declarative Configuration
```java
// Multi-container pod example
PodContainer pod = new PodBuilder()
    .withName("app-with-sidecar")
    .addContainer(
        new GenericContainer("nginx:latest")
            .withName("web")
            .withPort(80)
    )
    .addContainer(
        new GenericContainer("fluent/fluentd:latest")
            .withName("log-forwarder")
    )
    .withConfigMap("app-config", configMapData)
    .start();
```

###  12.3. <a name='ModuleSystem-1'></a>Module System
```java
// Using built-in module
@Testpods
class MyIntegrationTest {
    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer();

    @Container
    static KafkaContainer kafka = new KafkaContainer()
        .withBrokers(3);

    @Test
    void testWithDependencies() {
        // Test code with postgres and kafka available
    }
}
```

---

##  13. <a name='ModuleSpecifications'></a>Module Specifications

###  13.1. <a name='Built-inModulesPhase1'></a>Built-in Modules (Phase 1)

####  13.1.1. <a name='DatabaseModules'></a>Database Modules
1. **PostgreSQL**: Standard configuration, custom initialization scripts
2. **MySQL**: Version selection, custom config
3. **MongoDB**: Replica set support, authentication
4. **Redis**: Single instance, cluster mode

####  13.1.2. <a name='MessagingModules'></a>Messaging Modules
1. **Kafka**: Multi-broker, topic creation, schema registry
2. **RabbitMQ**: Queue/exchange pre-configuration, management UI access

###  13.2. <a name='ModuleInterfaceRequirements'></a>Module Interface Requirements
- Sensible defaults for quick setup
- Full customization via fluent API
- Readiness check implementation
- Connection info exposure
- Proper cleanup

---

##  14. <a name='SuccessMetrics'></a>Success Metrics

###  14.1. <a name='AdoptionMetrics'></a>Adoption Metrics
- Number of downloads/installations
- Active projects using Testpods
- Community contributions (modules, PRs)

###  14.2. <a name='TechnicalMetrics'></a>Technical Metrics
- Test execution time vs. Testcontainers baseline
- Cluster startup reliability (>99%)
- Resource usage (memory, CPU) vs. targets
- Code coverage maintained >80%

###  14.3. <a name='DeveloperExperienceMetrics'></a>Developer Experience Metrics
- Time to first successful test
- Documentation clarity (survey)
- Issue resolution time
- API usability (survey)

---

##  15. <a name='DependenciesandPrerequisites'></a>Dependencies and Prerequisites

###  15.1. <a name='ExternalDependencies'></a>External Dependencies
1. **Minikube**: Version 1.30+ installed and configured
2. **Container Runtime**: Docker Desktop or containerd on macOS
3. **kubectl**: For potential manual debugging
4. **Java**: JDK 11+ (recommend 17 LTS)
5. **Hypervisor**: Docker Desktop VM or Hyperkit on macOS

###  15.2. <a name='LibraryDependenciestobedetermined'></a>Library Dependencies (to be determined)
- Kubernetes Java Client (Fabric8 or official)
- JUnit 5
- Logging framework (SLF4J)
- YAML parsing library
- Others TBD

---

##  16. <a name='RisksandMitigations'></a>Risks and Mitigations

###  16.1. <a name='Risk-1:MinikubeStability'></a>Risk-1: Minikube Stability
- **Impact**: High - Core dependency
- **Likelihood**: Medium
- **Mitigation**:
  - Extensive testing across Minikube versions
  - Fallback/recovery mechanisms
  - Clear version compatibility matrix

###  16.2. <a name='Risk-2:RuntimeCompatibility'></a>Risk-2: Runtime Compatibility
- **Impact**: High - Different CRI implementations
- **Likelihood**: Medium
- **Mitigation**:
  - Abstraction layer design
  - Comprehensive runtime testing
  - Runtime-specific adapters

###  16.3. <a name='Risk-3:ResourceConsumption'></a>Risk-3: Resource Consumption
- **Impact**: Medium - Developer experience
- **Likelihood**: Medium
- **Mitigation**:
  - Resource limit defaults
  - Cluster reuse strategy
  - Automatic cleanup

###  16.4. <a name='Risk-4:Complexityvs.Testcontainers'></a>Risk-4: Complexity vs. Testcontainers
- **Impact**: Medium - Adoption barrier
- **Likelihood**: Medium
- **Mitigation**:
  - Simple defaults for common cases
  - Progressive disclosure of advanced features
  - Migration guide from Testcontainers

###  16.5. <a name='Risk-5:Cross-PlatformSupport'></a>Risk-5: Cross-Platform Support
- **Impact**: Medium - Limited initial platform support
- **Likelihood**: Low (deferred to later phases)
- **Mitigation**:
  - Architecture designed for multi-platform
  - Community feedback on priority platforms

---

##  17. <a name='DevelopmentPhasesHigh-Level'></a>Development Phases (High-Level)

###  17.1. <a name='Phase1:FoundationMVP'></a>Phase 1: Foundation (MVP)
**Scope**: Basic cluster management and single container support
- Minikube integration
- Single container deployment
- JUnit 5 extension
- Docker Engine runtime support
- Basic PostgreSQL module
- Documentation and examples

###  17.2. <a name='Phase2:CoreFeatures'></a>Phase 2: Core Features
**Scope**: Full Kubernetes resource support
- Multi-container pods
- ConfigMaps and Secrets
- Services and networking
- Volume support
- containerd runtime support
- Additional database modules (MySQL, MongoDB, Redis)

###  17.3. <a name='Phase3:AdvancedFeatures'></a>Phase 3: Advanced Features
**Scope**: Messaging platforms and advanced scenarios
- Kafka and RabbitMQ modules
- StatefulSets support
- Network policies
- Resource quotas
- Advanced debugging tools

###  17.4. <a name='Phase4:PolishandEcosystem'></a>Phase 4: Polish and Ecosystem
**Scope**: Production-ready release
- Performance optimization
- Comprehensive documentation
- Example projects
- Migration tools
- Community module contributions

---

##  18. <a name='OpenQuestionsandDiscussionPoints'></a>Open Questions and Discussion Points

###  18.1. <a name='TechnicalDecisions'></a>Technical Decisions
- [ ] Which Kubernetes Java client library? (Fabric8 vs. official)
- [ ] Maven vs. Gradle for project build?
- [ ] Module discovery mechanism (classpath scanning vs. explicit registration)?
- [ ] Configuration format (Java DSL only or YAML support)?
- [ ] How to handle Kubernetes version compatibility?

###  18.2. <a name='APIDesignQuestions'></a>API Design Questions
- [ ] Annotation-based vs. programmatic lifecycle management?
- [ ] How to expose low-level Kubernetes API when needed?
- [ ] Namespace management strategy?
- [ ] How to handle test parallelization safely?

###  18.3. <a name='RuntimeQuestions'></a>Runtime Questions
- [ ] Runtime detection strategy (auto vs. explicit configuration)?
- [ ] How to validate runtime capabilities?
- [ ] Support for runtime-specific features?

###  18.4. <a name='ModuleSystemQuestions'></a>Module System Questions
- [ ] Module versioning strategy?
- [ ] How to handle module dependencies?
- [ ] Contribution process for community modules?

###  18.5. <a name='PerformanceQuestions'></a>Performance Questions
- [ ] Cluster caching strategy (per test suite, per day, manual)?
- [ ] Image caching/preloading?
- [ ] Parallel pod deployment?

---

##  19. <a name='Appendices'></a>Appendices

###  19.1. <a name='AppendixA:TestcontainersComparison'></a>Appendix A: Testcontainers Comparison
*To be filled with detailed comparison of features, API style, and migration considerations*

###  19.2. <a name='AppendixB:KubernetesConceptsQuickReference'></a>Appendix B: Kubernetes Concepts Quick Reference
*To be filled with relevant Kubernetes concepts for developers new to K8s*

###  19.3. <a name='AppendixC:Glossary'></a>Appendix C: Glossary
- **CRI**: Container Runtime Interface
- **Pod**: Smallest deployable unit in Kubernetes, can contain one or more containers
- **Minikube**: Tool for running single-node Kubernetes cluster locally
- **containerd**: Industry-standard container runtime
- **Fluent API**: API design pattern using method chaining for readable configuration

---

##  20. <a name='BrainstormingNotes'></a>Brainstorming Notes

###  20.1. <a name='Session1:InitialRequirementsCapture'></a>Session 1: Initial Requirements Capture
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

###  20.2. <a name='Session2:ProjectStructureandDevelopmentApproach'></a>Session 2: Project Structure and Development Approach
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

###  20.3. <a name='Session3:AdvancedMulti-ServiceIntegrationTesting'></a>Session 3: Advanced Multi-Service Integration Testing
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

###  20.4. <a name='Session4:PlatformLayerandKubernetesOperators'></a>Session 4: Platform Layer and Kubernetes Operators
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
