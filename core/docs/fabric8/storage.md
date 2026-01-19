# Storage Resources

Keywords: PersistentVolume, PersistentVolumeClaim, PV, PVC, PersistentVolumeBuilder, PersistentVolumeClaimBuilder, persistentVolumes(), persistentVolumeClaims(), storage class, volume, capacity

## PersistentVolumeClaim

`PersistentVolumeClaim` is available in Kubernetes Client API via `client.persistentVolumeClaims()`. Here are some examples of it's common usage:

### Loading from YAML
- Load a `PersistentVolumeClaim` from yaml:
```java
PersistentVolumeClaim pvc = client.persistentVolumeClaims().load(new FileInputStream("pvc.yaml")).item();
```

### Getting a PersistentVolumeClaim
- Get a `PersistentVolumeClaim` object from Kubernetes API server:
```java
PersistentVolumeClaim pvc = client.persistentVolumeClaims().inNamespace("default").withName("test-pv-claim").get();
```

### Creating a PersistentVolumeClaim
- Create `PersistentVolumeClaim`:
```java
PersistentVolumeClaim persistentVolumeClaim = new PersistentVolumeClaimBuilder()
  .withNewMetadata().withName("test-pv-claim").endMetadata()
  .withNewSpec()
  .withStorageClassName("my-local-storage")
  .withAccessModes("ReadWriteOnce")
  .withNewResources()
  .addToRequests("storage", new Quantity("500Gi"))
  .endResources()
  .endSpec()
  .build();

client.persistentVolumeClaims().inNamespace("default").resource(persistentVolumeClaim).item();
```

### Applying a PersistentVolumeClaim
- Apply `PersistentVolumeClaim` onto Kubernetes Cluster:
```java
PersistentVolumeClaim pvc = client.persistentVolumeClaims().inNamespace("default").resource(pvcToCreate).serverSideApply();
```

### Listing PersistentVolumeClaims
- List `PersistentVolumeClaim` objects in a particular namespace:
```java
PersistentVolumeClaimList pvcList = client.persistentVolumeClaims().inNamespace("default").list();
```
- List `PersistentVolumeClaim` objects in any namespace:
```java
PersistentVolumeClaimList pvcList = client.persistentVolumeClaims().inAnyNamespace().list();
```
- List `PersistentVolumeClaim` objects in some namespace with some labels:
```java
PersistentVolumeClaimList pvcList = client.persistentVolumeClaims().inNamespace("default").withLabel("foo", "bar").list();
```

### Deleting a PersistentVolumeClaim
- Delete `PersistentVolumeClaim`:
```java
client.persistentVolumeClaims().inNamespace("default").withName("test-pv-claim").delete();
```

---

## PersistentVolume

`PersistentVolume` resource is available in Kubernetes Client API via `client.persistentVolumes()`. Here are some of the examples of it's common usage:

### Loading from YAML
- Load a `PersistentVolume` from yaml:
```java
PersistentVolume pv = client.persistentVolumes().load(new FileInputStream("pv.yaml")).item();
```

### Getting a PersistentVolume
- Get a `PersistentVolume` from Kubernetes API server:
```java
PersistentVolume pv = client.persistentVolumes().withName("test-local-pv").get();
```

### Creating a PersistentVolume
- Create `PersistentVolume`:
```java
PersistentVolume pv = new PersistentVolumeBuilder()
  .withNewMetadata().withName("test-local-pv").endMetadata()
  .withNewSpec()
  .addToCapacity(Collections.singletonMap("storage", new Quantity("500Gi")))
  .withAccessModes("ReadWriteOnce")
  .withPersistentVolumeReclaimPolicy("Retain")
  .withStorageClassName("my-local-storage")
  .withNewLocal()
  .withPath("/mnt/disks/vol1")
  .endLocal()
  .withNewNodeAffinity()
  .withNewRequired()
  .addNewNodeSelectorTerm()
  .withMatchExpressions(Arrays.asList(new NodeSelectorRequirementBuilder()
    .withKey("kubernetes.io/hostname")
    .withOperator("In")
    .withValues("my-node")
    .build()
  ))
  .endNodeSelectorTerm()
  .endRequired()
  .endNodeAffinity()
  .endSpec()
  .build();

PersistentVolume pvCreated = client.persistentVolumes().resource(pv).create();
```

### Applying a PersistentVolume
- Apply `PersistentVolume` onto Kubernetes Cluster:
```java
PersistentVolume pv = client.persistentVolumes().resource(pvToCreate).serverSideApply();
```

### Listing PersistentVolumes
- List `PersistentVolume`:
```java
PersistentVolumeList pvList = client.persistentVolumes().list();
```
- List `PersistentVolume` with some labels:
```java
PersistentVolumeList pvList = client.persistentVolumes().withLabel("foo", "bar").list();
```

### Deleting a PersistentVolume
- Delete `PersistentVolume`:
```java
client.persistentVolumes().withName("test-local-pv").delete();
```
