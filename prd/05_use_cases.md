# 5. Use Cases

<!-- TOC -->
- [5. Use Cases](#5-use-cases)
  - [5.1. UC-1: Basic Database Integration Test](#51-uc-1-basic-database-integration-test)
  - [5.2. UC-2: Multi-Container Pod Testing](#52-uc-2-multi-container-pod-testing)
  - [5.3. UC-3: ConfigMap and Secret Management](#53-uc-3-configmap-and-secret-management)
  - [5.4. UC-4: Network Policy Testing](#54-uc-4-network-policy-testing)
  - [5.5. UC-5: Custom Module Creation](#55-uc-5-custom-module-creation)
  - [5.6. UC-6: Multi-Service Integration](#56-uc-6-multi-service-integration)
<!-- /TOC -->

## 5.1. UC-1: Basic Database Integration Test

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

## 5.2. UC-2: Multi-Container Pod Testing

**Actor**: Java Backend Developer
**Goal**: Test application with a pod containing main container and sidecar (e.g., log forwarder)

## 5.3. UC-3: ConfigMap and Secret Management

**Actor**: Java Backend Developer
**Goal**: Test application configuration injection via Kubernetes ConfigMaps and Secrets

## 5.4. UC-4: Network Policy Testing

**Actor**: Java Backend Developer
**Goal**: Verify application behavior under specific network policies

## 5.5. UC-5: Custom Module Creation

**Actor**: Java Backend Developer
**Goal**: Create a reusable module for a proprietary internal service

## 5.6. UC-6: Multi-Service Integration

**Actor**: Java Backend Developer
**Goal**: Test application with multiple dependencies (database, message queue, cache)
