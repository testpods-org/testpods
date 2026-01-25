# 2. Goals and Objectives

<!-- TOC -->
- [2. Goals and Objectives](#2-goals-and-objectives)
  - [2.1. Primary Goals](#21-primary-goals)
  - [2.2. Secondary Goals](#22-secondary-goals)
  - [2.3. Non-Goals (Initial Release)](#23-non-goals-initial-release)
<!-- /TOC -->

## 2.1. Primary Goals

1. **Kubernetes-Native Testing**: Enable integration tests that run in actual Kubernetes pods rather than standalone Docker containers
2. **Developer Experience**: Provide an intuitive, fluent API similar to Testcontainers for defining test dependencies

4. **Extensibility**: Make it trivial to add new infrastructure modules and create custom container configurations
5. Rich debugging capabilities with easy access to logs and cluster state

## 2.2. Secondary Goals

1. Fast test execution through efficient cluster management and caching
2. Minimal resource footprint for local development machines
3. **Production Parity**: Allow tests to use the same Kubernetes manifests and configurations used in production
4. **Runtime Flexibility**: Support multiple CRI-compliant runtimes (initially containerd and Docker Engine)


## 2.3. Non-Goals (Initial Release)

- Windows and Linux support (macOS only initially)
- Production Kubernetes cluster management
- Multi-node cluster support
- Advanced Kubernetes features (operators, CRDs, Helm)
- Cloud provider integrations
