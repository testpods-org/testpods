# Custom Resources

Keywords: CustomResourceDefinition, CRD, CustomResource, GenericKubernetesResource, Resource API, ResourceList API, typed API, typeless API, apiextensions(), resources(), genericKubernetesResources(), ResourceDefinitionContext

## Fetching Metrics

Kubernetes Client also supports fetching metrics from API server if metrics are enabled on it. You can access metrics via `client.top()`. Here are some examples of its usage:

- Get `NodeMetrics` for all nodes:
```java
NodeMetricsList nodeMetricList = client.top().nodes().metrics();
```
- Get `NodeMetrics` for some specific nodes:
```java
NodeMetrics nodeMetric = client.top().nodes().withName("minikube").metric();
```
- Get `PodMetrics` for all pods in all namespaces:
```java
PodMetricsList podMetricsList = client.top().pods().metrics();
```
- Get `PodMetrics` for all pods in some specific namespace:
```java
PodMetricsList podMetricsList = client.top().pods().inNamespace("default").metrics();
```
- Get `PodMetrics` for a particular pod:
```java
PodMetrics podMetrics = client.top().pods().metrics("default", "nginx-pod");
```

---

## Resource API

Kubernetes Client also offers a generic API to handle different kind of Kubernetes resources. Most of the Kubernetes resources in Kubernetes Model are extending a class named `HasMetadata`. Resource API can work with any kind of Kubernetes Resource which extends this class.

- Get a Kubernetes Resource from Kubernetes API server:
```
Pod pod = client.resource(pod1).inNamespace("default").get();
```
- Apply a Kubernetes resource onto Kubernetes Cluster (Server Side Apply):
```java
Pod pod1 = new PodBuilder()
  .withNewMetadata().withName("resource-pod-" + RandomStringUtils.randomAlphanumeric(6).toLowerCase(Locale.ROOT)).endMetadata()
  .withNewSpec()
  .addNewContainer().withName("nginx").withImage("nginx").endContainer()
  .endSpec()
  .build();

client.resource(pod1).inNamespace("default").serverSideApply();
```
- Apply a Kubernetes resource And Wait until resource is ready:
```java
pod1 = client.resource(pod1).serverSideApply();
Pod p = client.pods().resource(pod1).waitUntilReady(10, TimeUnit.SECONDS);
```
- Delete a Kubernetes Resource:
```java
client.resource(pod1).inNamespace("default").delete();
```

---

## ResourceList API

Just like generic Kubernetes Resource API, Kubernetes client also provides a generic API to deal with Kubernetes List.

- Apply a list of Kubernetes resources onto Kubernetes Cluster:
```java
Service service =  new ServiceBuilder()
  .withNewMetadata().withName("my-service").endMetadata()
  .withNewSpec()
  .addToSelector("app", "Myapp")
  .addNewPort().withProtocol("TCP").withPort(80).withTargetPort(new IntOrString(9376)).endPort()
  .endSpec()
  .build();

ConfigMap configMap = new ConfigMapBuilder()
  .withNewMetadata().withName("my-configmap").endMetadata()
  .addToData(Collections.singletonMap("app", "Myapp"))
  .build();

KubernetesList list = new KubernetesListBuilder().withItems(deployment, service, configMap).build();

// Apply
client.resourceList(list).inNamespace("default").serverSideApply();
```
- Delete a list of items:
```java
client.resourceList(new PodListBuilder().withItems(pod1, pod2, pod3).build()).inNamespace("default").delete();
```

---

## CustomResourceDefinition

`CustomResourceDefinition` which are like templates for `CustomResource` objects in Kubernetes API are available in Kubernetes Client API via `client.apiextensions().v1().customResourceDefinitions()`.

- Load a `CustomResourceDefinition` from yaml:
```java
CustomResourceDefinition customResourceDefinition = client.apiextensions().v1().customResourceDefinitions().load(new FileInputStream("/sparkapplication-crd.yml")).item();
```
- Get a `CustomResourceDefinition` from Kubernetes APIServer
```java
CustomResourceDefinition crd = client.apiextensions().v1().customResourceDefinitions().withName("sparkclusters.radanalytics.io").get();
```
- Create `CustomResourceDefinition`:
```java
CustomResourceDefinition customResourceDefinition = new CustomResourceDefinitionBuilder()
      .withApiVersion("apiextensions.k8s.io/v1")
      .withNewMetadata().withName("sparkclusters.radanalytics.io")
      .endMetadata()
      .withNewSpec()
      .withNewNames()
      .withKind("SparkCluster")
      .withPlural("sparkclusters")
      .endNames()
      .withGroup("radanalytics.io")
      .withVersion("v1")
      .withScope("Namespaced")
      .endSpec()
      .build();

CustomResourceDefinition crd = client.apiextensions().v1().customResourceDefinitions().resource(customResourceDefinition).create();
```
- List and Delete `CustomResourceDefinition`:
```java
CustomResourceDefinitionList crdList = client.apiextensions().v1().customResourceDefinitions().list();
client.apiextensions().v1().customResourceDefinitions().withName("sparkclusters.radanalytics.io").delete();
```

---

## Resource Typed API

Any resource, custom or built-in, is available in Kubernetes API via the `client.resources(Class)` method. You need to provide POJOs for your custom resource.

**Note:** Your CustomResource POJO must implement `Namespaced` interface if it's a namespaced resource.

Example CustomResource POJO:
```java
@Version("v1")
@Group("stable.example.com")
public class CronTab extends CustomResource<CronTabSpec, CronTabStatus> implements Namespaced {
}
```

Usage:
```java
// Get Instance of client for our CustomResource
MixedOperation<CronTab, KubernetesResourceList<CronTab>, Resource<CronTab>> cronTabClient = client.resources(CronTab.class);

// Get CustomResource from Kubernetes APIServer
CronTab ct = cronTabClient.inNamespace("default").withName("my-second-cron-object").get();

// Create CustomResource
cronTabClient.inNamespace("default").create(cronTab1);

// List CustomResource
CronTabList cronTabList = cronTabClient.inNamespace("default").list();

// Delete CustomResource
cronTabClient.inNamespace("default").withName("my-third-cron-object").delete();

// Replace Status of CustomResource
cronTabClient.inNamespace("default").resource(updatedCronTab).updateStatus();

// Patch Status of CustomResource
cronTabClient.inNamespace("default").resource(updatedCronTab).patchStatus();

// Edit Status of CustomResource
cronTabClient.inNamespace("default").resource(cronTab1).editStatus(cronTab->updatedCronTab);

// Watch CustomResource
cronTabClient.inNamespace("default").watch(new Watcher<>() {
   @Override
   public void eventReceived(Action action, CronTab resource) { }

   @Override
   public void onClose(WatcherException cause) { }
});
```

---

## Resource Typeless API

If you don't need or want to use a strongly typed client, the Kubernetes Client also provides a typeless/raw API to handle your resources in form of `GenericKubernetesResource`.

Create a `ResourceDefinitionContext`:
```java
ResourceDefinitionContext resourceDefinitionContext = new ResourceDefinitionContext.Builder()
      .withGroup("jungle.example.com")
      .withVersion("v1")
      .withPlural("animals")
      .withNamespaced(true)
      .build();
```

Usage:
```java
// Load a resource from yaml
GenericKubernetesResource customResource = client.genericKubernetesResources(context).load(new FileInputStream("cr.yaml")).item();

// Get a resource from Kubernetes API server
GenericKubernetesResource customResourceObject = client.genericKubernetesResources(resourceDefinitionContext).inNamespace(currentNamespace).withName("otter").get();

// Create a resource
GenericKubernetesResource object = client.genericKubernetesResources(resourceDefinitionContext).inNamespace(currentNamespace).load(new FileInputStream("test-rawcustomresource.yml")).create();

// List CustomResource
GenericKubernetesResourceList list = client.genericKubernetesResources(resourceDefinitionContext).inNamespace(currentNamespace).list();

// Delete CustomResource
client.genericKubernetesResources(resourceDefinitionContext).inNamespace(currentNamespace).withName("otter").delete();

// Watch CustomResource
client.genericKubernetesResources(crdContext).inNamespace(namespace).watch(new Watcher<>() {
    @Override
    public void eventReceived(Action action, GenericKubernetesResource resource) {
        logger.info("{}: {}", action, resource);
    }

    @Override
    public void onClose(WatcherException e) { }
});
```

---

## Resource Typed API vs. Resource Typeless API

Following examples demonstrate how to define the same context for custom resources in two different ways:

**Resource Typed API:**
```java
@Group("sparkoperator.k8s.io")
@Plural("sparkapps")
@Version("v1beta2")
@Kind("SparkApplication")
public class SparkOperatorResource extends GenericKubernetesResource implements Namespaced { ... }

// Usage
kubernetesClient.resources(SparkOperatorResource.class).inNamespace("myNamespace")...
```

**Resource Typeless API:**
```java
public static ResourceDefinitionContext getResourceDefinitionContext() {
    return new ResourceDefinitionContext.Builder()
            .withGroup("sparkoperator.k8s.io")
            .withPlural("sparkapps")
            .withVersion("v1beta2")
            .withKind("SparkApplication")
            .withNamespaced(true)
            .build();
}

// Usage
kubernetesClient.genericKubernetesResources(getResourceDefinitionContext()).inNamespace("myNamespace")...
```
