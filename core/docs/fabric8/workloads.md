# Workloads

Keywords: ReplicaSet, ReplicaSetBuilder, ReplicationController, StatefulSet, StatefulSetBuilder, DaemonSet, DaemonSetBuilder, Job, JobBuilder, CronJob, CronJobBuilder, batch, apps(), scale, rolling, restart

## ReplicaSet

`ReplicaSet` is available in Kubernetes Client using `client.apps().replicaSets()`. Here are some of the common examples of how to use `ReplicaSet` with Kubernetes Client api:

### Loading from YAML
- Load a `ReplicaSet` object from yaml:
```java
ReplicaSet replicaSet = client.apps().replicaSets().inNamespace("default")
  .load(new FileInputStream("test-replicaset.yml")).item();
```

### Getting a ReplicaSet
- Get a `ReplicaSet` from API server:
```java
ReplicaSet rs = client.apps().replicaSets().inNamespace("default").withName("rs1").get();
```

### Creating a ReplicaSet
- Create a `ReplicaSet`:
```java
ReplicaSet replicaset1 = new ReplicaSetBuilder()
      .withNewMetadata()
      .withName("replicaset1")
      .addToLabels("app", "guestbook")
      .addToLabels("tier", "frontend")
      .endMetadata()
      .withNewSpec()
      .withReplicas(1)
      .withNewSelector()
      .withMatchLabels(Collections.singletonMap("tier", "frontend"))
      .endSelector()
      .withNewTemplate()
      .withNewMetadata()
      .addToLabels("app", "guestbook")
      .addToLabels("tier", "frontend")
      .endMetadata()
      .withNewSpec()
      .addNewContainer()
      .withName("busybox")
      .withImage("busybox")
      .withCommand("sleep","36000")
      .withNewResources()
      .withRequests(requests)
      .endResources()
      .withEnv(envVarList)
      .endContainer()
      .endSpec()
      .endTemplate()
      .endSpec()
      .build();

client.apps().replicaSets().inNamespace("default").resources(replicaset1).create();
```

### Applying a ReplicaSet
- Apply an existing `ReplicaSet`:
```java
ReplicaSet rs = client.apps().replicaSets().inNamespace("default").resource(replicaSet).serverSideApply();
```

### Listing ReplicaSets
- List `ReplicaSet` objects in some namespace:
```java
ReplicaSetList rsList = client.apps().replicaSets().inNamespace("default").list();
```
- List `ReplicaSet` objects in any namespace:
```java
ReplicaSetList rsList = client.apps().replicaSets().inAnyNamespace().list();
```
- List `ReplicaSet` objects in some namespace with some labels:
```java
ReplicaSetList rsList = client.apps().replicaSets().inNamespace("default").withLabel("foo", "bar").list();
```

### Deleting and Watching
- Delete `ReplicaSet`:
```java
client.apps().replicaSets().inNamespace("default").withName("rs1").delete();
```
- Watch `ReplicaSet`:
```java
client.apps().replicaSets().inNamespace("default").watch(new Watcher<>() {
	@Override
	public void eventReceived(Action action, ReplicaSet resource) {
		// Do some stuff depending upon action type
	}

	@Override
	public void onClose(WatcherException e) {

	}
});
```

### Scaling and Image Updates
- Scale `ReplicaSet`
```java
// Scale to 3 replicas
client.apps().replicaSets().inNamespace("default").withName("nginx-rs").scale(3);
```
- Update Image in `ReplicaSet`
```java
ReplicaSet replicaSet = client.apps().replicaSets()
        .inNamespace("default")
        .withName("soaktestrs")
        .updateImage("nickchase/soaktest");
```
- Update multiple Images in `ReplicaSet`:
```java
Map<String, String> containerToImageMap = new HashMap<>();
containerToImageMap.put("c1", "image1");
containerToImageMap.put("c2", "image2");
ReplicaSet replicaSet = client.apps().replicaSets()
            .inNamespace("default")
            .withName("soaktestrs")
            .updateImage(containerToImageMap);
```

---

## ReplicationController

`ReplicationController` resource is available in Kubernetes API using the `client.replicationControllers()`. Here are some of the examples of it's common usage:

### Loading from YAML
- Load `ReplicationController` object from yaml:
```java
ReplicationController aReplicationController = client.replicationControllers().inNamespace("default")
      .load(new FileInputStream("/test-replicationcontroller.yml")).item();
```

### Getting a ReplicationController
- Get `ReplicationController` object from API server:
```java
ReplicationController rc = client.replicationControllers().inNamespace("default").withName("nginx-controller").get();
```

### Creating a ReplicationController
- Create `ReplicationController` object:
```java
ReplicationController rc1 = new ReplicationControllerBuilder()
  .withNewMetadata().withName("nginx-controller").addToLabels("server", "nginx").endMetadata()
  .withNewSpec().withReplicas(3)
  .withNewTemplate()
  .withNewMetadata().addToLabels("server", "nginx").endMetadata()
  .withNewSpec()
  .addNewContainer().withName("nginx").withImage("nginx")
  .addNewPort().withContainerPort(80).endPort()
  .endContainer()
  .endSpec()
  .endTemplate()
  .endSpec().build();

ReplicationController rc = client.replicationControllers().inNamespace("default").resource(rc1).create();
```

### Applying a ReplicationController
- Apply `ReplicationController` object onto Kubernetes Cluster:
```java
ReplicationController rc = client.replicationControllers().inNamespace("default").resource(rc1).serverSideApply();
```

### Listing ReplicationControllers
- List `ReplicationController` object in some namespace:
```java
ReplicationControllerList rcList = client.replicationControllers().inNamespace("default").list();
```
- List `ReplicationController` objects in any namespace:
```java
ReplicationControllerList rcList = client.replicationControllers().inAnyNamespace("default").list();
```
- List `ReplicationController` objects in some namespace with some label:
```java
ReplicationControllerList rcList = client.replicationControllers().inNamespace("default").withLabel("foo", "bar").list();
```

### Deleting and Watching
- Delete `ReplicationController`:
```java
client.replicationControlers().inNamespace("default").withName("nginx-controller").delete();
```
- Watch `ReplicationController` in some specific namespace:
```java
client.replicationControllers().inNamespace(currentNamespace).watch(new Watcher<>() {
  @Override
  public void eventReceived(Action action, ReplicationController resource) {
    // Do something depending upon action type
  }

  @Override
  public void onClose(WatcherException cause) {

  }
});
```

### Scaling and Image Updates
- Scale `ReplicationController`:
```
ReplicationController rc = client.replicationControllers().inNamespace("default").withName("nginx-controller").scale(2);
```
- Update image in `ReplicationController`:
```java
ReplicationController rc = client.replicationControllers()
       .inNamespace("default")
       .withName("nginx")
       .updateImage("nginx:latest");
```
- Update multiple images in `ReplicationController`:
```java
Map<String, String> containerToImageMap = new HashMap<>();
containerToImageMap.put("c1", "image1");
containerToImageMap.put("c2", "image2");
ReplicationController rc = client.replicationControllers()
       .inNamespace("default")
       .withName("nginx")
       .updateImage(controllerToImageMap);
```

---

## StatefulSet

`StatefulSet` resource is available in Kubernetes API via `client.apps().statefulsets()`. Here are some examples of its common usages:

### Loading from YAML
- Load `StatefulSet` from yaml:
```java
StatefulSet aStatefulSet = client.apps().statefulSets()
  .load(new FileInputStream("test-statefulset.yml")).item();
```

### Getting a StatefulSet
- Get a `StatefulSet` from Kubernetes API server:
```java
StatefulSet ss1 = client.apps().statefulSets().inNamespace("default").withName("ss1").get();
```

### Creating a StatefulSet
- Create a `StatefulSet`:
```java
StatefulSet ss1 = new StatefulSetBuilder()
      .withNewMetadata().withName("ss1").endMetadata()
      .withNewSpec()
      .withReplicas(2)
      .withNewSelector().withMatchLabels(Collections.singletonMap("app", "nginx")).endSelector()
      .withNewTemplate()
      .withNewMetadata()
      .addToLabels("app", "nginx")
      .endMetadata()
      .withNewSpec()
      .addNewContainer()
      .withName("nginx")
      .withImage("nginx")
      .addNewPort()
      .withContainerPort(80)
      .withName("web")
      .endPort()
      .addNewVolumeMount()
      .withName("www")
      .withMountPath("/usr/share/nginx/html")
      .endVolumeMount()
      .endContainer()
      .endSpec()
      .endTemplate()
      .addNewVolumeClaimTemplate()
      .withNewMetadata()
      .withName("www")
      .endMetadata()
      .withNewSpec()
      .addToAccessModes("ReadWriteOnce")
      .withNewResources()
      .withRequests(Collections.singletonMap("storage", new Quantity("1Gi")))
      .endResources()
      .endSpec()
      .endVolumeClaimTemplate()
      .endSpec()
      .build();

StatefulSet ss = client.apps().statefulSets().inNamespace("default").resource(ss1).create();
```

### Applying a StatefulSet
- Apply `StatefulSet` onto Kubernetes Cluster:
```java
StatefulSet ss = client.apps().statefulSets().inNamespace("default").resource(ss1).serverSideApply();
```

### Listing StatefulSets
- List `StatefulSet` in some particular namespace:
```java
StatefulSetList statefulSetList = client.apps().statefulSets().inNamespace("default").list();
```
- List `StatefulSet` in any namespace:
```java
StatefulSetList statefulSetList = client.apps().statefulSets().inAnyNamespace().list();
```
- List `StatefulSet` in some namespace with label:
```java
StatefulSetList statefulSetList = client.apps().statefulSets().inNamespace("default").withLabel("foo", "bar").list();
```

### Deleting and Scaling
- Delete `StatefulSet`:
```java
client.apps().statefulSets().inNamespace("default").withName("ss1").delete();
```
- Scale `StatefulSet`:
```java
client.apps().statefulSets().inNamespace("default").withName("ss1").scale(2);
```

### Watching
- Watch `StatefulSet`:
```java
client.apps().statefulSets().inNamespace("default").withName("ss1").watch(new Watcher<>() {
  @Override
  public void eventReceived(Action action, StatefulSet resource) {
    // Do something on action type
  }

  @Override
  public void onClose(WatcherException cause) {

  }
})
```

### Image Updates
- Update Image in `StatefulSet`:
```java
StatefulSet statefulSet = client.apps().statefulSets()
      .inNamespace("default")
      .withName("web")
      .updateImage("nginx:1.19");
```
- Updated multiple containers in `StatefulSet`:
```java
Map<String, String> containerToImageMap = new HashMap<>();
containerToImageMap("container1", "nginx:1.9");
containerToImageMap("container2", "busybox:latest");
Statefulset statefulSet = client.apps().statefulSets()
      .inNamespace("default")
      .withName("web")
      .updateImage(params);
```

### Rolling Updates
- Restart Rollout for `StatefulSet`:
```java
StatefulSet ss = client.apps().statefulSets()
        .inNamespace("default")
        .withName("web")
        .rolling()
        .restart();
```
- Pause Rollout for `StatefulSet`:
```java
StatefulSet ss = client.apps().statefulSets()
         .inNamespace("default")
         .withName("web")
         .rolling()
         .pause();
```
- Resume Rollout for `StatefulSet`:
```java
StatefulSet ss = client.apps().statefulSets()
         .inNamespace("default")
         .withName("web")
         .rolling()
         .resume();
```
- Undo Rollout for `StatefulSet`:
```
StatefulSet ss = client.apps().statefulSets()
     .inNamespace("default")
     .withName("web")
     .rolling()
     .undo();
```

---

## DaemonSet

`DaemonSet` resource is available in Kubernetes Client API via `client.apps().daemonSets()`. Here are some examples of its common usage:

### Loading from YAML
- Load `DaemonSet` from yaml:
```java
DaemonSet ds = client.apps().daemonSets().load(new FileInputStream("daemonset.yaml")).item();
```

### Getting a DaemonSet
- Get `DaemonSet` from Kubernetes API server:
```java
DaemonSet ds = client.apps().daemonSets().inNamespace("default").withName("ds1").get();
```

### Creating a DaemonSet
- Create `DaemonSet`:
```java
DaemonSet ds = new DaemonSetBuilder()
  .withNewMetadata().withName("fluentd-elasticsearch").addToLabels("k8s-app", "fluentd-logging").endMetadata()
  .withNewSpec()
  .withNewSelector()
  .addToMatchLabels("name", "fluentd-elasticsearch")
  .endSelector()
  .withNewTemplate()
  .withNewSpec()
  .addNewToleration().withKey("node-role.kubernetes.io/master").withEffect("NoSchedule").endToleration()
  .addNewContainer()
  .withName("fluentd-elasticsearch").withImage("quay.io/fluentd_elasticsearch/fluentd:v2.5.2")
  .withNewResources()
  .addToLimits(Collections.singletonMap("memory", new Quantity("200Mi")))
  .addToRequests(Collections.singletonMap("cpu", new Quantity("100m")))
  .endResources()
  .addNewVolumeMount().withName("varlog").withMountPath("/var/log").endVolumeMount()
  .endContainer()
  .withTerminationGracePeriodSeconds(30l)
  .addNewVolume()
  .withName("varlog").withNewHostPath().withPath("/var/log").endHostPath()
  .endVolume()
  .endSpec()
  .endTemplate()
  .endSpec()
  .build();
ds = client.apps().daemonSets().inNamespace("default").resource(ds).create();
```

### Applying a DaemonSet
- Apply a `DaemonSet` onto Kubernetes Cluster:
```java
DaemonSet ds = client.apps().daemonSets().inNamespace("default").resource(ds1).serverSideApply();
```

### Listing DaemonSets
- List `DaemonSet` in some namespace:
```java
DaemonSetList dsList = client.apps().daemonSets().inNamespace("default").list();
```
- List `DaemonSet` in any namespace:
```java
DaemonSetList dsList = client.apps().daemonSets().inAnyNamespace().list();
```
- List `DaemonSet` with some label:
```java
DaemonSetList dsList = client.apps().daemonSets().inNamespace("default").withLabel("foo", "bar").list();
```

### Deleting and Watching
- Delete `DaemonSet`:
```java
client.apps().daemonSets().inNamespace("default").withName("ds1").delete();
```
- Watch `DaemonSet`:
```java
client.apps().daemonSets().inNamespace("default").watch(new Watcher<>() {
  @Override
  public void eventReceived(Action action, DaemonSet resource) {
    // Do something depending upon action type
  }

  @Override
  public void onClose(WatcherException cause) {

  }
});
```

---

## Job

`Job` resource is available in Kubernetes Client API via `client.batch().jobs()`. Here are some of the examples of common usage:

### Loading from YAML
- Loading a `Job` from yaml:
```java
Job job = client.batch().jobs().load(new FileInputStream("sample-job.yml")).item();
```

### Getting a Job
- Get a `Job` resource with some name from API server:
```java
Job job = client.batch().jobs().inNamespace("default").withName("pi").get();
```

### Creating a Job
- Create `Job`:
```java
final Job job = new JobBuilder()
    .withApiVersion("batch/v1")
    .withNewMetadata()
    .withName("pi")
    .withLabels(Collections.singletonMap("label1", "maximum-length-of-63-characters"))
    .withAnnotations(Collections.singletonMap("annotation1", "some-very-long-annotation"))
    .endMetadata()
    .withNewSpec()
    .withNewTemplate()
    .withNewSpec()
    .addNewContainer()
    .withName("pi")
    .withImage("perl")
    .withArgs("perl", "-Mbignum=bpi", "-wle", "print bpi(2000)")
    .endContainer()
    .withRestartPolicy("Never")
    .endSpec()
    .endTemplate()
    .endSpec()
    .build();

client.batch().jobs().inNamespace("default").resource(job).create();
```

### Applying a Job
- Apply `Job` onto Kubernetes Cluster:
```java
Job job = client.batch().v1().jobs().inNamespace("default").resource(job1).serverSideApply();
```

### Listing Jobs
- List `Job` in some namespace:
```java
JobList jobList = client.batch().jobs().inNamespace("default").list();
```
- List `Job` in any namespace:
```java
JobList jobList = client.batch().jobs().inAnyNamespace().list();
```
- List `Job` resources in some namespace with some labels:
```java
JobList jobList = client.batch().jobs().inNamespace("default").withLabel("foo", "bar").list();
```

### Deleting and Watching
- Delete `Job`:
```java
client.batch().jobs().inNamespace("default").withName("pi").delete();
```
- Watch `Job`:
```java
client.batch().jobs().inNamespace("default").watch(new Watcher<>() {
    @Override
    public void eventReceived(Action action, Job resource) {
      // Do something depending upon action
    }

    @Override
    public void onClose(WatcherException cause) {

    }
});
```

---

## CronJob

`CronJob` resource is available in Kubernetes Client api via `client.batch().cronjobs()`. Here are some of the examples of its usages:

### Loading from YAML
- Load `CronJob` from yaml:
```java
CronJob cronJob = client.batch().cronjobs().load(new FileInputStream("cronjob.yml")).item();
```

### Getting a CronJob
- Get a `CronJob` from Kubernetes API server:
```java
CronJob aCronJob = client.batch().cronjobs().inNamespace("default").withName("some-cj").get();
```

### Creating a CronJob
- Create `CronJob`:
```java
CronJob cronJob1 = new CronJobBuilder()
    .withApiVersion("batch/v1beta1")
    .withNewMetadata()
    .withName("hello")
    .withLabels(Collections.singletonMap("foo", "bar"))
    .endMetadata()
    .withNewSpec()
    .withSchedule("*/1 * * * *")
    .withNewJobTemplate()
    .withNewSpec()
    .withNewTemplate()
    .withNewSpec()
    .addNewContainer()
    .withName("hello")
    .withImage("busybox")
    .withArgs("/bin/sh", "-c", "date; echo Hello from Kubernetes")
    .endContainer()
    .withRestartPolicy("OnFailure")
    .endSpec()
    .endTemplate()
    .endSpec()
    .endJobTemplate()
    .endSpec()
    .build();

cronJob1 = client.batch().cronjobs().inNamespace("default").resource(cronJob1).create();
```

### Applying a CronJob
- Apply `CronJob` onto Kubernetes Cluster:
```java
CronJob cronJob = client.batch().v1().cronjobs().inNamespace("default").resource(cronJob1).serverSideApply();
```

### Listing CronJobs
- List some `CronJob` objects in some namespace:
```java
CronJobList cronJobList = client.batch().cronjobs().inNamespace("default").list()
```
- List some `CronJob` objects in any namespace:
```java
CronJobList cronJobList = client.batch().cronjobs().inAnyNamespace().list();
```
- List some `CronJob` objects in some namespace with some label:
```java
CronJobList cronJobList = client.batch().cronjobs().inNamespace("default").withLabel("foo", "bar").list();
```

### Editing and Deleting
- Edit/Update `CronJob`:
```java
CronJob cronJob1 = client.batch().cronjobs().inNamespace("default").withName(cronJob1.getMetadata().getName()).edit(
  cj -> new CronJobBuilder(cj).editSpec().withSchedule("*/1 * * * *").endSpec().build()
);
```
- Delete `CronJob`:
```java
client.batch().cronjobs().inNamespace("default").withName("pi").delete();
```
