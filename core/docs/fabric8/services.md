# Services

Keywords: Service, ServiceBuilder, services(), NodePort, ClusterIP, LoadBalancer, ServicePort, expose

## Overview
`Service` is available in Kubernetes client API via `client.services()`. Here are some common usages of `Service`:

## Loading from YAML
- Loading a `Service` from yaml:
```java
Service aService = client.services().load(new FileInputStream("service.yml")).item();
```

## Getting a Service
- Get a `Service` from API server:
```java
Service service = client.services().inNamespace("default").withName("some-service").get();
```

## Creating a Service
- Create a `Service`:
```java
Service createdSvc = client.services().inNamespace("default").resource(svc).create();
```

## Applying a Service
- Apply a `Service` object onto Kubernetes Cluster:
```java
Service createdSvc = client.services().inNamespace("default").resource(svc).serverSideApply();
```

## Listing Services
- List all `Service` objects in some specific namespace:
```java
ServiceList svcList = client.services().inNamespace("default").list();
```
- List all `Service` objects in any namespace:
```java
ServiceList svcList = client.services().inAnyNamespace().list();
```
- List `Service` objects with some specific labels:
```java
ServiceList svcList = client.services().inNamespace("default").withLabel("foo", "bar").list();
```

## Deleting a Service
- Delete a `Service`:
```java
client.services().inNamespace("default").withName("some-svc").delete();
```

## Watching Services
- Watching a `Service`:
```java
client.services().inNamespace("default").watch(new Watcher<>() {
	@Override
	public void eventReceived(Action action, Service resource) {
		// Perform something depending upon action
	}

	@Override
	public void onClose(WatcherException cause) {

	}
});
```
