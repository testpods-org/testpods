# Advanced Features

Keywords: SharedInformer, SharedIndexInformer, ResourceEventHandler, watch, list options, delete options, watch options, log options, pagination, cascading, propagationPolicy, Serialization, asYaml, run

## SharedInformers

Kubernetes Client provides `SharedInformer` support in order to stay updated to events happening to your resource inside Kubernetes. Its implementation is simply list and watch operations after a certain interval of time.

### Getting SharedInformerFactory
```java
SharedInformerFactory sharedInformerFactory = client.informers();
```

### Create SharedIndexInformer for Kubernetes Resources
Create `SharedIndexInformer` for some Kubernetes Resource (requires resource's class and resync period):
```java
SharedIndexInformer<Pod> podInformer = sharedInformerFactory.sharedIndexInformerFor(Pod.class, 30 * 1000L);
podInformer.addEventHandler(new ResourceEventHandler<Pod>() {
  @Override
  public void onAdd(Pod pod) {
    logger.info("{} pod added", pod.getMetadata().getName());
  }

  @Override
  public void onUpdate(Pod oldPod, Pod newPod) {
    logger.info("{} pod updated", oldPod.getMetadata().getName());
  }

  @Override
  public void onDelete(Pod pod, boolean deletedFinalStateUnknown) {
    logger.info("{} pod deleted", pod.getMetadata().getName());
  }
});
```

### Create Namespaced SharedIndexInformer
```java
SharedIndexInformer<Pod> podInformer = client.pods().inNamespace("default").inform(new ResourceEventHandler<>() {
    @Override
    public void onAdd(Pod pod) {
        logger.info("Pod " + pod.getMetadata().getName() + " got added");
    }

    @Override
    public void onUpdate(Pod oldPod, Pod newPod) {
        logger.info("Pod " + oldPod.getMetadata().getName() + " got updated");
    }

    @Override
    public void onDelete(Pod pod, boolean deletedFinalStateUnknown) {
        logger.info("Pod " + pod.getMetadata().getName() + " got deleted");
    }
},  30 * 1000L);
```

### Start and Stop Informers
```java
sharedInformerFactory.startAllRegisteredInformers();
sharedInformerFactory.stopAllRegisteredInformers();
```

---

## List Options

Various options provided by Kubernetes Client API when it comes to listing resources.

### Pagination
```java
PodList podList = client.pods().inNamespace("myproject").list(new ListOptionsBuilder().withLimit(5L).build());

podList = client.pods().inNamespace("myproject").list(new ListOptionsBuilder().withLimit(5L)
		.withContinue(podList.getMetadata().getContinue())
		.build());
```

### Label Selectors
```java
// With label
PodList podList = client.pods().inNamespace("default").withLabel("foo", "bar").list();

// With multiple labels
PodList podList = client.pods().inNamespace("default").withLabels(Collections.singletonMap("foo", "bar")).list();

// Without label
PodList podList = client.pods().inNamespace("default").withoutLabel("foo", "bar").list();

// Labels in/not in
PodList podList = client.pods().inNamespace("default").withLabelIn("foo", "bar").list();
PodList podList = client.pods().inNamespace("default").withLabelNotIn("foo", "bar").list();
```

### Field Selectors
```java
// With field
PodList podList = client.pods().inNamespace("default").withField("foo", "bar").list();

// Without field
PodList podList = client.pods().inNamespace("default").withoutField("foo", "bar").list();
```

### With ListOptions
```java
PodList podList = client.pods().inNamespace("default").list(new ListOptionsBuilder()
  .withLimit(1L)
  .withContinue(null)
  .build());
```

---

## Delete Options

Kubernetes Client provides ways to delete dependents of some Kubernetes resource.

### Cascading Delete
```java
// Delete dependent resources (default: true)
client.apps().deployments().inNamespace("default").withName("nginx-deploy").cascading(true).delete();
```

### Propagation Policy
```java
client.apps().deployments().inNamespace("default").withName("nginx-deploy").withPropagationPolicy(DeletionPropagation.FOREGROUND).delete();
```

### Grace Period
```java
client.apps().deployments().inNamespace("ns1").withName("mydeployment").withPropagationPolicy(DeletionPropagation.FOREGROUND).withGracePeriod(10).delete();
```

---

## Watch Options

Three different ways of using `Watch`:

### Plain Watch
```java
client.pods().inNamespace(namespace).watch(new Watcher<>() {
    @Override
    public void eventReceived(Action action, Pod pod) {
        switch (action.name()) {
            case "ADDED":
                logger.log(Level.INFO, pod.getMetadata().getName() + "got added");
                break;
            case "DELETED":
                logger.log(Level.INFO, pod.getMetadata().getName() + "got deleted");
                break;
            case "MODIFIED":
                logger.log(Level.INFO, pod.getMetadata().getName() + "got modified");
                break;
        }
    }

    @Override
    public void onClose(WatcherException e) {
        logger.log(Level.INFO, "Closed");
    }
});
```

### Watch with ListOptions
```java
client.pods().watch(new ListOptionsBuilder().withTimeoutSeconds(30L).build(), new Watcher<>() {
  @Override
  public void eventReceived(Action action, Pod resource) { }

  @Override
  public void onClose(WatcherException cause) { }
});
```

---

## Log Options

### Pretty Output
```java
client.pods().inNamespace("test").withName("foo").withPrettyOutput().getLog();
```

### Specific Container
```java
client.pods().inNamespace("test").withName("foo").inContainer("container1").getLog();
```

### Previous Container Instance
```java
client.pods().inNamespace("test").withName("foo").terminated().getLog();
```

### Time-based Filtering
```java
// After specific date (RFC3339)
client.pods().inNamespace("test").withName("foo").sinceTime("2020-09-10T12:53:30.154148788Z").getLog();

// After duration of seconds
client.pods().inNamespace("test").withName("foo").sinceSeconds(10).getLog();
```

### Line and Byte Limits
```java
// Tail lines
client.pods().inNamespace("test").withName("foo").tailingLines(10).getLog();

// Limit bytes
client.pods().inNamespace("test").withName("foo").limitBytes(102).getLog();
```

### Timestamps
```java
client.pods().inNamespace("test").withName("foo").usingTimestamps().getLog();
```

---

## Serializing to YAML

Resources can be exported to a YAML String via the `Serialization` class:
```java
Pod myPod;

String myPodAsYaml = Serialization.asYaml(myPod);
```

---

## Running a Pod

Kubernetes Client provides mechanism similar to `kubectl run`:
```java
try (KubernetesClient client = new KubernetesClientBuilder().build()) {
    client.run().inNamespace("default")
        .withName("my-pod")
        .withImage("nginx:latest")
        .done();
}
```

---

## Server Side Apply

Apply resources with server-side apply:
```java
// Single resource
client.pods().inNamespace("default").resource(pod).serverSideApply();

// Multiple resources
client.resourceList(list).inNamespace("default").serverSideApply();
```
