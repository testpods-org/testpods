# Client Initialization

Keywords: KubernetesClient, KubernetesClientBuilder, Config, ConfigBuilder, kubeconfig, autoConfigure, context, minikube, kind

## Initializing Kubernetes Client
Typically, we create Kubernetes Client like this:
```
try (final KubernetesClient client = new KubernetesClientBuilder().build()) {
  // Do stuff with client
}
```
This would pick up default settings, reading your `kubeconfig` file from `~/.kube/config` directory or whatever is defined inside `KUBECONFIG` environment variable. But if you want to customize creation of client, you can also pass a `Config` object inside the builder like this:
```
Config kubeConfig = new ConfigBuilder()
  .withMasterUrl("https://192.168.42.20:8443/")
  .build()
try (final KubernetesClient client = new KubernetesClientBuilder().withConfig(kubeConfig).build()) {
  // Do stuff with client
}
```

## Using a Specific Kubernetes Context

To connect to a specific kubectl context (e.g., minikube profile, kind cluster):
```java
// Auto-configure using a specific context name
Config config = Config.autoConfigure("minikube");
KubernetesClient client = new KubernetesClientBuilder().withConfig(config).build();

// For kind clusters
Config config = Config.autoConfigure("kind-my-cluster");
KubernetesClient client = new KubernetesClientBuilder().withConfig(config).build();
```

## Running Inside a Pod

When running inside a Kubernetes pod:
```java
// reads serviceaccount from mounted volume and gets apiServer url from environment variables itself.
KubernetesClient client = new KubernetesClientBuilder().build();
```
You can also checkout a demo example here: [kubernetes-client-inside-pod](https://github.com/rohanKanojia/kubernetes-client-inside-pod)

## Common Config Options

```java
Config config = new ConfigBuilder()
  .withMasterUrl("https://kubernetes.default.svc")
  .withNamespace("my-namespace")
  .withTrustCerts(true)
  .withOauthToken("my-token")
  .build();
```
