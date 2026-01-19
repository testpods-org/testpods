# Getting Started

This guide will help you get started with TestPods.

## Prerequisites

- Java 21 or later
- Maven or Gradle
- A local Minikube running a Kubernetes cluster 
- kubectl configured

## Installation

### Maven
```xml
<dependency>
    <groupId>org.testpods</groupId>
    <artifactId>testpods-core</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <scope>test</scope>
</dependency>
```

### Gradle
```gradle
testImplementation 'org.testpods:testpods-core:0.1.0-SNAPSHOT'
```

### Minikube setup using MiniKit CLI

Follow the [MiniKit CLI](../development/minikit-agent.md) installation instructions to create and start a MiniKube VM running a Kubernetes cluster.