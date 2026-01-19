# Fabric8 Kubernetes Client - Documentation Index

This index helps you find the right documentation file for your needs.

## Quick Lookup

| Topic | File | Keywords |
|-------|------|----------|
| Client setup | [client-initialization.md](client-initialization.md) | KubernetesClient, KubernetesClientBuilder, Config, ConfigBuilder, kubeconfig, autoConfigure, context |
| Pods | [pods.md](pods.md) | Pod, PodBuilder, pods(), logs, exec, portForward, watch, ephemeral containers, file upload/download |
| Services | [services.md](services.md) | Service, ServiceBuilder, services(), NodePort, ClusterIP, LoadBalancer |
| Deployments | [deployments.md](deployments.md) | Deployment, DeploymentBuilder, apps().deployments(), rolling update, scale, replicas |
| Workloads | [workloads.md](workloads.md) | ReplicaSet, ReplicationController, StatefulSet, DaemonSet, Job, CronJob, batch jobs |
| Configuration | [configuration.md](configuration.md) | ConfigMap, Secret, Namespace, ServiceAccount, NamespaceBuilder, configMaps(), secrets(), namespaces() |
| Networking | [networking.md](networking.md) | Ingress, NetworkPolicy, EndpointSlice, network policies, ingress rules |
| Storage | [storage.md](storage.md) | PersistentVolume, PersistentVolumeClaim, PV, PVC, storage class, volume |
| RBAC & Security | [rbac.md](rbac.md) | ClusterRole, ClusterRoleBinding, Role, RoleBinding, ServiceAccount, AccessReview, CertificateSigningRequest |
| Custom Resources | [custom-resources.md](custom-resources.md) | CustomResourceDefinition, CRD, Resource API, GenericKubernetesResource, typed, typeless |
| Advanced Features | [advanced.md](advanced.md) | SharedInformer, watch, list options, delete options, log options, metrics, serialization, server side apply |
| OpenShift | [openshift.md](openshift.md) | OpenShift, DeploymentConfig, BuildConfig, Route, Project, ImageStream, CatalogSource |
| Extensions | [extensions.md](extensions.md) | Tekton, Knative, TektonClient, KnativeClient, Pipeline, PipelineRun, logging |

## Common Operations Quick Reference

### Creating a Client
```java
// Default (uses ~/.kube/config)
KubernetesClient client = new KubernetesClientBuilder().build();

// With specific context
Config config = Config.autoConfigure("context-name");
KubernetesClient client = new KubernetesClientBuilder().withConfig(config).build();
```
See: [client-initialization.md](client-initialization.md)

### CRUD Pattern (applies to all resources)
```java
// Create
client.pods().inNamespace("ns").resource(pod).create();

// Read
Pod pod = client.pods().inNamespace("ns").withName("name").get();

// Update
client.pods().inNamespace("ns").withName("name").edit(p -> ...);

// Delete
client.pods().inNamespace("ns").withName("name").delete();

// List
PodList list = client.pods().inNamespace("ns").list();

// Watch
client.pods().withName("name").watch(watcher);
```

### Resource Builders
All resources use the builder pattern:
```java
new PodBuilder().withNewMetadata().withName("name").endMetadata()...build();
new ServiceBuilder().withNewMetadata().withName("name").endMetadata()...build();
new NamespaceBuilder().withNewMetadata().withName("name").endMetadata().build();
```

## File Descriptions

- **client-initialization.md** - How to create and configure the Kubernetes client
- **pods.md** - Pod operations: create, read, update, delete, logs, exec, port-forward, file transfer
- **services.md** - Service operations: create different service types, access endpoints
- **deployments.md** - Deployment operations: create, scale, rolling updates
- **workloads.md** - ReplicaSets, ReplicationControllers, StatefulSets, DaemonSets, Jobs, CronJobs
- **configuration.md** - ConfigMaps, Secrets, Namespaces, ServiceAccounts
- **networking.md** - Ingress, NetworkPolicy, EndpointSlice
- **storage.md** - PersistentVolumes and PersistentVolumeClaims
- **rbac.md** - Role-based access control, access reviews, certificates
- **custom-resources.md** - Custom Resource Definitions and the Resource API
- **advanced.md** - Informers, options (list/delete/watch/log), metrics, utilities
- **openshift.md** - OpenShift-specific resources and client
- **extensions.md** - Tekton and Knative client extensions, logging configuration
