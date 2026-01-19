# Deployments

Keywords: Deployment, DeploymentBuilder, apps().deployments(), rolling update, scale, replicas, rollout, restart, pause, resume, undo, updateImage

## Overview
`Deployment` is available in Kubernetes-Client API via `client.apps().deployment()`. Here are some of the common usages of `Deployment`:

## Loading from YAML
- Loading a `Deployment` from yaml into object:
```java
Deployment aDeployment = client.apps().deployments().load(new FileInputStream("test-deployments.yml")).item();
```

## Getting a Deployment
- Get a `Deployment` from API server:
```java
Deployment deploy = client.apps().deployments().inNamespace("default").withName("deploy-1").get();
```

## Creating a Deployment
- Create a `Deployment`:
```java
Deployment deployment1 = new DeploymentBuilder()
   .withNewMetadata()
      .withName("deployment1")
      .addToLabels("test", "deployment")
   .endMetadata()
   .withNewSpec()
      .withReplicas(1)
      .withNewTemplate()
        .withNewMetadata()
        .addToLabels("app", "httpd")
        .endMetadata()
        .withNewSpec()
          .addNewContainer()
             .withName("busybox")
             .withImage("busybox")
             .withCommand("sleep","36000")
          .endContainer()
        .endSpec()
      .endTemplate()
      .withNewSelector()
        .addToMatchLabels("app","httpd")
      .endSelector()
   .endSpec()
 .build();

client.apps().deployments().inNamespace("default").resource(deployment1).create();
```

## Applying a Deployment
- Apply a `Deployment` object onto Kubernetes Cluster:
```java
Deployment createdDeployment = client.apps().deployments().inNamespace("default").resource(deployObj).serverSideApply();
```

## Listing Deployments
- List `Deployment` objects in some specific namespace:
```java
DeploymentList aDeploymentList = client.apps().deployments().inNamespace("default").list();
```
- List `Deployment` objects in any namespace:
```java
DeploymentList aDeploymentList = client.apps().deployments().inAnyNamespace().list();
```
- List `Deployment` objects with some specific labels:
```java
DeploymentList aDeployList = client.apps().deployments().inNamespace("default").withLabel("foo", "bar").list();
```

## Editing a Deployment
- Editing a `Deployment`:
```java
// Scales Deployment to 2 replicas
Deployment updatedDeploy = client.apps().deployments().inNamespace("default")
  .withName("deployment1").edit(
    d -> new DeploymentBuilder(d).editSpec().withReplicas(2).endSpec().build()
  );
```

## Updating Container Images
- Update single container image inside `Deployment`:
```java
Deployment updatedDeployment = client.apps()
  .deployments()
  .inNamespace("default")
  .withName("ngix-controller")
  .updateImage("docker.io/nginx:latest");
```
- Update multiple container images inside `Deployment`:
```java
Map<String, String> containerToImageMap = new HashMap<>();
containerToImageMap.put("nginx", "nginx:perl");
containerToImageMap.put("sidecar", "someImage:someVersion");
Deployment updatedDeployment = client.apps().deployments()
      .inNamespace("default")
      .withName("nginx-deployment")
      .updateImage(containerToImageMap);

```

## Rolling Updates
- Rollout restart a `Deployment`:
```java
Deployment deployment = client.apps().deployments()
      .inNamespace("default")
      .withName("nginx-deployment")
      .rolling()
      .restart();
```
- Pause Rollout of a `Deployment`:
```java
Deployment deployment = client.apps().deployments()
      .inNamespace("default")
      .withName("nginx-deployment")
      .rolling()
      .pause();
```
- Resume Rollout of a `Deployment`:
```java
Deployment deployment = client.apps().deployments()
      .inNamespace("default")
      .withName("nginx-deployment")
      .rolling()
      .resume();
```
- Undo Rollout of a `Deployment`:
```java
Deployment deployment = client.apps().deployments()
      .inNamespace("default")
      .withName("nginx-deployment")
      .rolling()
      .undo();
```

## Deleting a Deployment
- Deleting a `Deployment`:
```java
client.apps().deployments().inNamespace("default").withName("foo").delete();
```

## Watching Deployments
- Watching a `Deployment`:
```java
client.apps().deployments().inNamespace("default").watch(new Watcher<>() {
	@Override
	public void eventReceived(Action action, Deployment resource) {
		// Do stuff depending upon action
	}

	@Override
	public void onClose(WatcherException cause) {

	}
});
```

## Scaling
- Scale a `Deployment`:
```java
client.apps().deployments().inNamespace("default").withName("nginx-deployment").scale(1);
```

## Getting Logs
- Get `Deployment` logs:
```java
client.apps().deployments().inNamespace("default").withName("nginx").watchLog(System.out);
```
