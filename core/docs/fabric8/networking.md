# Networking Resources

Keywords: Ingress, IngressBuilder, NetworkPolicy, NetworkPolicyBuilder, EndpointSlice, network(), discovery(), ingress rules, network policies

## Ingress

`Ingress` resource is available in Kubernetes Client API via `client.network().v1().ingress()`. Here are some examples regarding its usage:

### Loading from YAML
- Load `Ingress` from yaml:
```java
Ingress ingress = client.network().v1().ingress().load(new FileInputStream("ingress.yml")).item();
```

### Getting an Ingress
- Get `Ingress` from Kubernetes API server:
```java
Ingress ingress = client.network().v1().ingress().inNamespace("default").withName("ingress1").get();
```

### Creating an Ingress
- Create `Ingress`:
```java
Ingress ingress = new IngressBuilder()
  .withNewMetadata().withName("test-ingress").addToAnnotations("nginx.ingress.kubernetes.io/rewrite-target", "/").endMetadata()
  .withNewSpec()
  .addNewRule()
  .withNewHttp()
  .addNewPath()
  .withPath("/testPath").withNewBackend().withServiceName("test").withServicePort(new IntOrString(80)).endBackend()
  .endPath()
  .endHttp()
  .endRule()
  .endSpec()
  .build();
client.network().v1().ingress().inNamespace("default").resource(ingress).create();
```

### Applying an Ingress
- Apply `Ingress` onto Kubernetes Cluster:
```java
Ingress igx = client.network().v1().ingresses().inNamespace("default").resource(ingress).serverSideApply();
```

### Listing Ingresses
- List `Ingress` in some namespace:
```java
IngressList ingressList = client.network().v1().ingress().inNamespace("default").list();
```
- List `Ingress` in any namespace:
```java
IngressList ingressList = client.network().v1().ingress().inAnyNamespace().list();
```
- List `Ingress` with some label in any namespace:
```java
IngressList ingressList = client.network().v1().ingress().inNamespace("default").withLabel("foo", "bar").list();
```

### Deleting an Ingress
- Delete `Ingress`:
```java
client.network().v1().ingress().inNamespace("default").withName("ingress1").delete();
```

---

## EndpointSlice

`EndpointSlice` resource is available in Kubernetes Client API via `client.discovery().v1().endpointSlices()`. Here are some examples of its common usage:

### Loading from YAML
- Load `EndpointSlice` from yaml:
```java
EndpointSlice es = client.discovery().v1().endpointSlices().load(getClass().getResourceAsStream("/endpointslice.yml")).item();
```

### Getting an EndpointSlice
- Get `EndpointSlice` from Kubernetes API server:
```java
EndpointSlice esFromServer = client.discovery().v1().endpointSlices().inNamespace("default").withName("es1").get();
```

### Creating an EndpointSlice
- Create `EndpointSlice`:
```java
EndpointSlice esToCreate = new EndpointSliceBuilder()
  .withNewMetadata()
  .withName(name)
  .addToLabels("kubernetes.io/service-name", "example")
  .endMetadata()
  .withAddressType("IPv4")
  .addNewPort()
  .withName("http")
  .withPort(80)
  .endPort()
  .addNewEndpoint()
  .withAddresses("10.1.2.3")
  .withNewConditions().withReady(true).endConditions()
  .withHostname("pod-1")
  .addToTopology("kubernetes.io/hostname", "node-1")
  .addToTopology("topology.kubernetes.io/zone", "us-west2-a")
  .endEndpoint()
  .build();
esToCreate = client.discovery().v1().endpointSlices().inNamespace("ns1").resource(esToCreate).create();
```

### Applying an EndpointSlice
- Apply `EndpointSlice` onto Kubernetes Cluster:
```java
EndpointSlice es = client.discovery().v1().endpointSlices().inNamespace("ns1").resource(endpointSlice).serverSideApply();
```

### Listing EndpointSlices
- List `EndpointSlice` in some namespace:
```java
EndpointSliceList esList = client.discovery().v1().endpointSlices().inNamespace("default").list();
```
- List `EndpointSlice` in any namespace:
```java
EndpointSliceList esList = client.discovery().v1().endpointSlices().inAnyNamespace().list();
```
- List `EndpointSlice` with some label:
```java
EndpointSliceList esList = client.discovery().v1().endpointSlices().inNamespace("default").withLabel("foo", "bar").list();
```

### Deleting an EndpointSlice
- Delete `EndpointSlice`:
```java
client.discovery().v1().endpointSlices().inNamespace("default").withName("test-es").delete();
```

### Watching EndpointSlices
- Watch `EndpointSlice`:
```java
client.discovery().v1().endpointSlices().inNamespace("default").watch(new Watcher<>() {
  @Override
  public void eventReceived(Action action, EndpointSlice resource) {

  }

  @Override
  public void onClose(WatcherException cause) {

  }
});
```

---

## NetworkPolicy

`NetworkPolicy` is available in Kubernetes Client API via `client.network().networkPolicies()`. Here are some examples of it's common usages:

### Loading from YAML
- Load a `NetworkPolicy` from yaml:
```java
NetworkPolicy loadedNetworkPolicy = client.network().networkPolicies()
  .load(new FileInputStream("/test-networkpolicy.yml")).item();
```

### Getting a NetworkPolicy
- Get `NetworkPolicy` from Kubernetes API server:
```java
NetworkPolicy getNetworkPolicy = client.network().networkPolicies()
  .withName("networkpolicy").get();
```

### Creating a NetworkPolicy
- Create `NetworkPolicy`:
```java
NetworkPolicy networkPolicy = new NetworkPolicyBuilder()
      .withNewMetadata()
      .withName("networkpolicy")
      .addToLabels("foo","bar")
      .endMetadata()
      .withNewSpec()
      .withNewPodSelector()
      .addToMatchLabels("role","db")
      .endPodSelector()
      .addToIngress(0,
        new NetworkPolicyIngressRuleBuilder()
        .addToFrom(0, new NetworkPolicyPeerBuilder().withNewPodSelector()
          .addToMatchLabels("role","frontend").endPodSelector()
          .build()
        ).addToFrom(1, new NetworkPolicyPeerBuilder().withNewNamespaceSelector()
          .addToMatchLabels("project","myproject").endNamespaceSelector()
            .build()
        )
        .addToPorts(0,new NetworkPolicyPortBuilder().withPort(new IntOrString(6379))
          .withProtocol("TCP").build())
        .build()
      )
      .endSpec()
      .build();

NetworkPolicy npCreated = client.network().networkPolicies().resource(networkPolicy).create();
```

### Applying a NetworkPolicy
- Apply `NetworkPolicy` onto Kubernetes Cluster:
```java
NetworkPolicy npCreated = client.network().networkPolicies().resource(networkPolicy).serverSideApply();
```

### Listing NetworkPolicies
- List `NetworkPolicy`:
```java
NetworkPolicyList networkPolicyList = client.network().networkPolicies().list();
```
- List with labels `NetworkPolicy`:
```java
NetworkPolicyList networkPolicyList = client.network().networkPolicies()
  .withLabels(Collections.singletonMap("foo","bar")).list();
```

### Deleting a NetworkPolicy
- Delete `NetworkPolicy`:
```java
client.network().networkPolicies().withName("np-test").delete();
```
