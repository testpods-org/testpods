# Getting Started

This guide will help you get started with TestPods.

## Prerequisites

- Java 17 or later
- Maven or Gradle
- A local Kubernetes cluster (minikube, kind, or k3d)
- kubectl configured

## Installation

### Maven
```xml
<dependency>
    <groupId>org.testpods</groupId>
    <artifactId>testpods-core</artifactId>
    <version>0.1.0-BETA</version>
    <scope>test</scope>
</dependency>
```

### Gradle
```gradle
testImplementation 'org.testpods:testpods-core:0.1.0-BETA'
```

## Setting Up Your Kubernetes Cluster

### Option 1: Minikube
```bash
minikube start
```

### Option 2: kind
```bash
kind create cluster
```

### Option 3: k3d
```bash
k3d cluster create testpods
```

## Your First Test

Create a test class:
```java
import org.testpods.core.annotation.TestK8s;
import org.testpods.mongodb.MongoDBPod;
import org.junit.jupiter.api.Test;

@TestK8s
class MyFirstTest {
    
    @K8sPod
    static MongoDBPod mongodb = new MongoDBPod();
    
    @Test
    void testWithMongoDB() {
        String connectionString = mongodb.getConnectionString();
        // Use MongoDB in your test
        System.out.println("MongoDB running at: " + connectionString);
    }
}
```

Run your test:
```bash
mvn test
```

## What Happens?

When you run the test:

1. TestPods detects your local Kubernetes cluster
2. Creates a namespace for your test
3. Deploys MongoDB as a Pod
4. Waits for it to be ready
5. Provides the connection string to your test
6. Cleans up after the test completes

## Next Steps

- Learn about [available Pods](pods.md) *(coming soon)*
- Configure [Spring Boot integration](spring-boot.md) *(coming soon)*
- Explore [advanced features](advanced.md) *(coming soon)*

## Need Help?

- Check the [FAQ](faq.md) *(coming soon)*
- Open an [issue on GitHub](https://github.com/testpods-org/testpods/issues)
- Join the discussion *(community links coming soon)*