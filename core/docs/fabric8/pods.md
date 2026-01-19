# Pods

Keywords: Pod, PodBuilder, pods(), logs, exec, portForward, watch, ephemeral containers, file upload, file download, waitUntilReady, waitUntilCondition, LocalPortForward

## Overview
`Pods` are available in our dsl via the `client.pods()`. Here are some of the common usages of `Pod` resource:

## Loading from YAML
- Loading a `Pod` from a `yaml` file into `Pod` object:
```java
Pod myPod = client.pods().load(new FileInputStream("some-pod.yml")).item();
```

## Listing Pods
- Listing all `Pod` objects in some specific namespace:
```java
PodList podList = client.pods().inNamespace("default").list();
```
- List all `Pod` objects in all namespaces:
```java
PodList podList = client.pods().inAnyNamespace().list();
```
- List `Pod` objects containing some labels:
```java
PodList podList = client.pods().inNamespace("default").withLabel("foo", "bar").list();
```

## Getting a Pod
- Get `Pod` from server with some specific name:
```java
Pod myPod = client.pods().inNamespace("default").withName("nginx-pod").get();
```

## Creating a Pod
- Create a `Pod`:
```java
Pod aPod = new PodBuilder().withNewMetadata().withName("demo-pod1").endMetadata()
    .withNewSpec()
    .addNewContainer()
    .withName("nginx")
    .withImage("nginx:1.7.9")
    .addNewPort().withContainerPort(80).endPort()
    .endContainer()
    .endSpec()
    .build();
Pod createdPod = client.pods().inNamespace("default").resource(aPod).create();
```

## Applying a Pod
- Apply a `Pod` to Kubernetes Cluster with some existing object:
```java
client.pods().inNamespace("default").resource(pod).serverSideApply();
```

## Editing a Pod
- Edit a `Pod` object:
```java
client.pods().inNamespace("default").withName("nginx").edit(
  p -> new PodBuilder(p).editOrNewMetadata().addToLabels("new","label").endMetadata().build()
);
```

## Pod Logs
- Get logs for `Pod` object:
```java
String log = client.pods().inNamespace("default").withName("test-pod").getLog();
```
- Watch logs for `Pod`:
```java
LogWatch watch = client.pods().inNamespace(namespace).withName(podName).tailingLines(10).watchLog(System.out);
```

## Deleting Pods
- Delete a `Pod`:
```java
client.pods().inNamespace("default").withName("nginx").delete();
```
- Delete multiple `Pod` objects:
```java
client.resourceList(pod1, pod2).inNamespace("default").delete();
```

## Waiting for Pod Readiness
- Wait until a `Pod` is ready:
```java
Pod pod = client.pods().inNamespace("default").withName("nginx").waitUntilReady(5, TimeUnit.MINUTES);
```
- Wait until `Pod` meets some specific condition:
```java
Pod pod = client.pods().inNamespace("default").withName("nginx").waitUntilCondition(pod -> pod.getStatus().getPhase().equals("Succeeded"), 1, TimeUnit.MINUTES);
```

## Port Forwarding
- Port Forward a `Pod`
```java
int containerPort =  client.pods().inNamespace("default").withName("testpod").get().getSpec().getContainers().get(0).getPorts().get(0).getContainerPort();
LocalPortForward portForward = client.pods().inNamespace("default").withName("testpod").portForward(containerPort, 8080);
```

## Watching Pods
- Watching `Pod`:
```java
final CountDownLatch deleteLatch = new CountDownLatch(1);
Watch podWatch = client.pods().withName("pod1").watch(new Watcher<>() {
    @Override
    public void eventReceived(Action action, Pod resource) {
      switch (action) {
        case DELETED:
          deleteLatch.countDown();
      }
    }

    @Override
    public void onClose(WatcherException e) { }
});
deleteLatch.await(10, TimeUnit.MINUTES);
```

## File Operations
- Upload file into a `Pod`
```java
    client.pods().inNamespace(currentNamespace).withName(pod1.getMetadata().getName())
      .file("/tmp/toBeUploaded").upload(tmpFile.toPath());
```
- Read file from a `Pod`
```java
    try (InputStream is = client.pods().inNamespace(currentNamespace).withName(pod1.getMetadata().getName()).file("/msg").read())  {
      String result = new BufferedReader(new InputStreamReader(is)).lines().collect(Collectors.joining("\n"));
    }
```

## Ephemeral Containers
- Add ephemeral container to a `Pod`
```java
PodResource resource = client.pods().withName("pod1");
resource.ephemeralContainers()
  .edit(p -> new PodBuilder(p)
    .editSpec()
    .addNewEphemeralContainer()
    .withName("debugger")
    .withImage("busybox")
    .withCommand("sleep", "36000")
    .endEphemeralContainer()
    .endSpec()
    .build());

resource.waitUntilCondition(p -> p.getStatus()
	.getEphemeralContainerStatuses()
	.stream()
	.filter(s -> s.getName().equals("debugger"))
	.anyMatch(s -> s.getState().getRunning() != null), 2, TimeUnit.MINUTES);
ByteArrayOutputStream out = new ByteArrayOutputStream();
try (ExecWatch watch = resource.inContainer("debugger")
  .writingOutput(out)
  .exec("sh", "-c", "echo 'hello world!'")) {
  assertEquals(0, watch.exitCode().join());
  assertEquals("hello world!\n", out.toString());
}
```

## Using Client from Within a Pod
- Using Kubernetes Client from within a `Pod`
When trying to access Kubernetes API from within a `Pod` authentication is done a bit differently as compared to when being done on your system. If you checkout [documentation](https://kubernetes.io/docs/tasks/access-application-cluster/access-cluster/#accessing-the-api-from-a-pod). Client authenticates by reading `ServiceAccount` from `/var/run/secrets/kubernetes.io/serviceaccount/` and reads environment variables like `KUBERNETES_SERVICE_HOST` and `KUBERNETES_SERVICE_PORT` for apiServer URL. You don't have to worry about all this when using Fabric8 Kubernetes Client. You can simply use it like this and client will take care of everything:
```
// reads serviceaccount from mounted volume and gets apiServer url from environment variables itself.
KubernetesClient client = new KubernetesClientBuilder().build();
```
You can also checkout a demo example here: [kubernetes-client-inside-pod](https://github.com/rohanKanojia/kubernetes-client-inside-pod)
