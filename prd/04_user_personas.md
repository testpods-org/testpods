# 4. User Personas

<!-- TOC -->
- [4. User Personas](#4-user-personas)
  - [4.1. Primary Persona: Java Backend Developer](#41-primary-persona-java-backend-developer)
  - [4.2. Secondary Persona: DevOps/Platform Engineer](#42-secondary-persona-devopsplatform-engineer)
<!-- /TOC -->

## 4.1. Primary Persona: Java Backend Developer

- **Context**: Develops Spring Boot microservices that interact with databases, message queues, and other services
- **Needs**:
  - Fast, reliable integration tests
  - Test environment that matches production Kubernetes setup
  - Easy setup without deep Kubernetes knowledge
- **Pain Points**:
  - Current Docker-based tests don't catch Kubernetes-specific issues
  - Setting up local Kubernetes for testing is complex and slow
  - Differences between test and production environments cause bugs

## 4.2. Secondary Persona: DevOps/Platform Engineer

- **Context**: Maintains testing infrastructure and CI/CD pipelines
- **Needs**:
  - Consistent test environments across team members
  - Ability to customize cluster configuration
  - Integration with existing CI/CD systems
- **Pain Points**:
  - Developers' local tests don't match CI environment
  - Hard to debug test failures in containerized environments
  - Resource consumption of test infrastructure
