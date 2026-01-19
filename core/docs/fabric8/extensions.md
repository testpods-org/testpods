# Client Extensions

Keywords: Tekton, TektonClient, Knative, KnativeClient, Pipeline, PipelineRun, Task, TaskRun, Service, logging, adapt()

## Tekton Client

Fabric8 Kubernetes Client has an extension for Tekton. It is pretty much the same as Kubernetes Client but has support for some additional Tekton resources.

### Initializing Tekton Client

```java
try (final TektonClient client = new KubernetesClientBuilder().build().adapt(TektonClient.class)) {
  // Do stuff with client
}
```

This would pick up default settings, reading your `kubeconfig` file from `~/.kube/config` directory or whatever is defined inside `KUBECONFIG` environment variable.

### Tekton Client DSL Usage

The Tekton client supports CRD API version `tekton.dev/v1`, `tekton.dev/v1alpha1` as well as `tekton.dev/v1beta1`.

- `tekton.dev/v1alpha1` includes: `Pipeline`, `PipelineRun`, `PipelineResource`, `Task`, `TaskRun`, `Condition` and `ClusterTask`
- `tekton.dev/v1beta1` includes: `Pipeline`, `PipelineRun`, `Task`, `TaskRun` and `ClusterTask`
- Tekton Triggers: `TriggerTemplate`, `TriggerBinding`, `EventListener` and `ClusterTriggerBinding`

All resources are available using the DSL `tektonClient.v1alpha1()` or `tektonClient.v1beta1()`.

### Examples

- Listing all `PipelineRun` objects in some specific namespace:
```java
PipelineRunList list = tektonClient.v1().pipelineRuns().inNamespace("default").list();
```

- Create a `PipelineRun`:
```java
PipelineRun pipelineRun = new PipelineRunBuilder()
    .withNewMetadata().withName("demo-run-1").endMetadata()
    .withNewSpec()
    .withNewPipelineRef().withName("demo-pipeline").endPipelineRef()
    .addNewParam().withName("greeting").withNewValue("Hello World!").endParam()
    .endSpec()
    .build();

tektonClient.v1().pipelineRuns().inNamespace("default").resource(pipelineRun).create();
```

---

## Knative Client

Fabric8 Kubernetes Client also has an extension for Knative. It is pretty much the same as Kubernetes Client but has support for some additional Knative resources.

### Initializing Knative Client

```java
try (final KnativeClient client = new KubernetesClientBuilder().build().adapt(KnativeClient.class)) {
  // Do stuff with client
}
```

This would pick up default settings, reading your `kubeconfig` file from `~/.kube/config` directory or whatever is defined inside `KUBECONFIG` environment variable.

### Knative Client DSL Usage

The usage of the resources follows the same pattern as for K8s resources like Pods or Deployments.

### Examples

- Listing all `Service` objects in some specific namespace:
```java
ServiceList list = knativeClient.services().inNamespace("default").list();
```

- Apply a `Service`:
```java
try (KnativeClient kn = new DefaultKnativeClient()) {
    // Create Service object
    Service service = new ServiceBuilder()
            .withNewMetadata().withName("helloworld-go").endMetadata()
            .withNewSpec()
            .withNewTemplate()
            .withNewSpec()
            .addToContainers(new ContainerBuilder()
                    .withImage("gcr.io/knative-samples/helloworld-go")
                    .addNewEnv().withName("TARGET").withValue("Go Sample V1").endEnv()
                    .build())
            .endSpec()
            .endTemplate()
            .endSpec()
            .build();

    // Apply it onto Kubernetes Server
    kn.services().inNamespace("default").resource(service).serverSideApply();
}
```

---

## Logging

Using logging-interceptor for HTTP debugging:

### Configure OkHTTP Logging

Set logging level to trace in your `simplelogger.properties` file:
```properties
org.slf4j.simpleLogger.defaultLogLevel=trace
```

This will enable detailed HTTP request/response logging for debugging purposes.
