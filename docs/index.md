# TestPods

**Kubernetes-native testing for Java microservices**

TestPods is a testing library that brings Testcontainers-style simplicity to Kubernetes environments. Test your Spring Boot microservices with real infrastructure components running in Kubernetes, not Docker.

---

## Why TestPods?

**🚀 Kubernetes-Native**  
Built specifically for Kubernetes testing, not retrofitted from Docker

**🔄 Familiar API**  
If you know Testcontainers, you already know TestPods

**⚡ Test Like Production**  
Test with real Kubernetes features: network policies, service mesh, pod disruption budgets

**🎯 Zero Configuration**  
Works with minikube, kind, k3d, or remote clusters out of the box

---

## Quick Example
```java
@TestK8s
class OrderServiceTest {
    
    @K8sPod
    static MongoDBPod mongodb = new MongoDBPod();
    
    @K8sPod
    static KafkaPod kafka = new KafkaPod();
    
    @Test
    void shouldProcessOrder() {
        // Your test code here
        // MongoDB and Kafka are running in Kubernetes
    }
}
```

---

## Current Status

**⚠️ Beta Release**  
TestPods is currently in beta. APIs may change before 1.0 release.

**🔧 In Development**
- Core TestPods library
- Spring Boot integration
- Documentation and examples

---

## Get Started

👉 [Getting Started Guide](getting-started.md)

---

## Project Links

- **GitHub**: [github.com/testpods-org/testpods](https://github.com/testpods-org/testpods)
- **Website**: [testpods.org](https://testpods.org) *(coming soon)*
- **Issues**: [github.com/testpods-org/testpods/issues](https://github.com/testpods-org/testpods/issues)

---

## Philosophy

TestPods believes that testing microservices should be:

1. **As close to production as possible** - test in Kubernetes, deploy to Kubernetes
2. **Simple and intuitive** - annotations and fluent APIs, not complex YAML
3. **Fast to iterate** - start/stop pods in seconds, not minutes
4. **Compatible with existing tools** - works with JUnit 5, Spring Boot Test, Maven/Gradle

---

## Roadmap

- ✅ Core library architecture
- ✅ Fabric8 Kubernetes client integration
- 🚧 MongoDBPod, KafkaPod, PostgreSQLPod
- 🚧 Spring Boot starter
- 🚧 Gradle/Maven plugins
- 📋 Support for StatefulSets
- 📋 Service mesh integration
- 📋 Chaos engineering features

---

## Contributing

TestPods is open source and welcomes contributions!

See our [Contributing Guide](https://github.com/testpods-org/testpods/blob/main/CONTRIBUTING.md) *(coming soon)*

---

## License

TestPods is licensed under the Apache License 2.0