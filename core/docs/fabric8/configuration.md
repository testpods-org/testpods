# Configuration Resources

Keywords: ConfigMap, ConfigMapBuilder, Secret, SecretBuilder, Namespace, NamespaceBuilder, ServiceAccount, ServiceAccountBuilder, configMaps(), secrets(), namespaces(), serviceAccounts()

## ConfigMap

`ConfigMap` resource is available in Kubernetes Client api via the `client.configMaps()`. Here are some examples of common usage:

### Loading from YAML
- Load `ConfigMap` object from yaml:
```java
ConfigMap configMap = client.configMaps().load(new FileInputStream("configmap1.yml")).item();
```

### Getting a ConfigMap
- Get `ConfigMap` from API server:
```java
ConfigMap configMap = client.configMaps().inNamespace("default").withName("configmap1").get();
```

### Creating a ConfigMap
- Create `ConfigMap`:
```java
ConfigMap configMap1 = new ConfigMapBuilder()
      .withNewMetadata().withName("configmap1").endMetadata()
      .addToData("1", "one")
      .addToData("2", "two")
      .addToData("3", "three")
      .build();
ConfigMap configMap = client.configMaps().inNamespace("default").resource(configMap1).create();
```

### Applying a ConfigMap
- Apply a `ConfigMap` object onto Kubernetes Cluster:
```java
ConfigMap configMap = client.configMaps().inNamespace("default").resource(configMap1).serverSideApply();
```

### Listing ConfigMaps
- List `ConfigMap` objects in some namespace:
```java
ConfigMapList configMapList = client.configMaps().inNamespace("default").list();
```
- List `ConfigMap` objects in any namespace:
```java
ConfigMapList configMapList = client.configMaps().inAnyNamespace().list();
```
- List `ConfigMap` objects in some namespace with some labels:
```java
ConfigMapList configMapList = client.configMaps().inNamespace("default").withLabel("foo", "bar").list();
```

### Deleting a ConfigMap
- Delete `ConfigMap`:
```java
client.configMaps().inNamespace("default").withName("configmap1").delete();
```

### Watching ConfigMaps
- Watch `ConfigMap`:
```java
client.configMaps().inNamespace("default").watch(new Watcher<>() {
  @Override
  public void eventReceived(Action action, ConfigMap resource) {
    // Do something depending upon action type
  }

  @Override
  public void onClose(WatcherException cause) {

  }
});
```

### Updating a ConfigMap
- Update `ConfigMap`:
```java
ConfigMap configMap1 = client.configMaps().inNamespace(currentNamespace).withName("configmap1").edit(
  c -> new ConfigMapBuilder(c).addToData("4", "four").build()
);
```

---

## Secret

`Secret` resource is available in Kubernetes Client api via `client.secrets()`. Here are some of the examples of it's common usages:

### Loading from YAML
- Load `Secret` from yaml:
```java
Secret aSecret = client.secrets().inNamespace("default").load(new FileInputStream("test-secret.yml")).item();
```

### Getting a Secret
- Get a `Secret` from API server:
```java
Secret secret = client.secrets().inNamespace("default").withName("secret1").get()
```

### Creating a Secret
- Create a `Secret`:
```java
Secret secret1 = new SecretBuilder()
      .withNewMetadata().withName("secret1").endMetadata()
      .addToData("username", "guccifer")
      .addToData("password", "shadowgovernment")
      .build();
Secret secretCreated = client.secrets().inNamespace("default").resource(secret1).create();
```

### Applying a Secret
- Apply a `Secret` onto Kubernetes Cluster:
```java
Secret createdSecret = client.secrets().inNamespace("default").resource(secret1).serverSideApply();
```

### Listing Secrets
- List `Secret` resources in some namespace:
```java
SecretList secretList = client.secrets().inNamespace("default").list();
```
- List `Secret` resources in any namespace:
```java
SecretList secretList = client.secrets().inAnyNamespace().list();
```
- List `Secret` resources in some namespace with some label:
```java
SecretList secretList = client.secrets().inNamespace("default").withLabel("foo", "bar").list();
```

### Editing a Secret
- Edit `Secret`:
```java
Secret secret1 = client.secrets().inNamespace(currentNamespace).withName("secret1").edit(
  s -> new SecretBuilder(s).withType("Opaque").build()
);
```

### Deleting a Secret
- Delete `Secret`:
```java
client.secrets().inNamespace("default").withName("secret1").delete();
```

### Watching Secrets
- Watch `Secret`:
```java
client.secrets().inNamespace("default").watch(new Watcher<>() {
  @Override
  public void eventReceived(Action action, Secret resource) {
    // Do something depending upon action type
  }

  @Override
  public void onClose(WatcherException cause) {

  }
});
```

---

## Namespace

`Namespace` is available in Kubernetes Client API via `client.namespaces()`. Here are some of the common usages:

### Loading from YAML
- Load `Namespace` from yaml:
```java
Namespace namespace = client.namespaces().load(new FileInputStream("namespace-test.yml")).item();
```

### Getting a Namespace
- Get `Namespace` from Kubernetes API server:
```java
Namespace namespace = client.namespaces().withName("namespace1").get();
```

### Creating a Namespace
- Create a `Namespace`:
```java
Namespace ns = new NamespaceBuilder()
    .withNewMetadata()
    .withName("my-namespace")
    .endMetadata()
    .build();
client.namespaces().resource(ns).create();
```

### Listing Namespaces
- List `Namespace` objects:
```java
NamespaceList namespaceList = client.namespaces().list();
```
- List `Namespace` objects with some labels:
```java
NamespaceList namespaceList = client.namespaces().withLabel("key1", "value1").list();
```

### Deleting a Namespace
- Delete `Namespace` objects:
```java
client.namespaces().withName("ns1").delete();
```

---

## ServiceAccount

`ServiceAccount` resource is available in Kubernetes Client API via `client.serviceAccounts()`. Here are some examples of it's usage:

### Loading from YAML
- Load `ServiceAccount` from yaml:
```java
ServiceAccount svcAccount = client.serviceAccounts().inNamespace("default")
  .load(new FileInputStream("sa.yml")).item();
```

### Getting a ServiceAccount
- Get `ServiceAccount` from Kubernetes API server:
```java
ServiceAccount sa = client.serviceAccounts().inNamespace("default").withName("sa-ribbon").get();
```

### Creating a ServiceAccount
- Create `ServiceAccount`:
```java
ServiceAccount serviceAccount1 = new ServiceAccountBuilder()
  .withNewMetadata().withName("serviceaccount1").endMetadata()
  .withAutomountServiceAccountToken(false)
  .build();

client.serviceAccounts().inNamespace("default").resource(serviceAccount1).create();
```

### Applying a ServiceAccount
- Apply `ServiceAccount` onto Kubernetes cluster:
```java
ServiceAccount serviceAccount = client.serviceAccounts().inNamespace("default").resource(serviceAccount1).serverSideApply();
```

### Listing ServiceAccounts
- List `ServiceAccount` in some specific namespace:
```java
ServiceAccountList svcAccountList = client.serviceAccounts().inNamespace("default").list();
```
- List `ServiceAccount` in some namespace with labels:
```java
ServiceAccountList saList = client.serviceAccounts().inNamespace("default").withLabel("foo", "bar").list();
```

### Updating a ServiceAccount
- Update/Edit `ServiceAccount`:
```java
ServiceAccount serviceAccount1 = client.serviceAccounts().inNamespace("default").withName("serviceaccount1").edit(
  sa -> new ServiceAccountBuilder(sa).addNewSecret().withName("default-token-uudp").endSecret()
  .addNewImagePullSecret().withName("myregistrykey").endImagePullSecret()
  .build();
);
```

### Deleting a ServiceAccount
- Delete `ServiceAccount`:
```java
client.serviceAccounts().inNamespace("default").withName("serviceaccount1").delete();
```
