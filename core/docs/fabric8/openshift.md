# OpenShift Client

Keywords: OpenShift, OpenShiftClient, DeploymentConfig, BuildConfig, Route, Project, ImageStream, CatalogSource, PrometheusRule, ServiceMonitor, ClusterResourceQuota, ClusterVersion, EgressNetworkPolicy, adapt()

## Overview

Fabric8 Kubernetes Client also has an extension for OpenShift. It is pretty much the same as Kubernetes Client but has support for some additional OpenShift resources.

## Initializing OpenShift Client

```java
try (final OpenShiftClient client = new KubernetesClientBuilder().build().adapt(OpenShiftClient.class)) {
  // Do stuff with client
}
```

With custom config:
```java
Config kubeConfig = new ConfigBuilder()
            .withMasterUrl("https://api.ci-ln-3sbdl1b-d5d6b.origin-ci-int-aws.dev.examplecloud.com:6443")
            .withOauthToken("xxxxxxxx-41oafKI6iU637-xxxxxxxxxxxxx")
            .build();
try (final OpenShiftClient client = new KubernetesClientBuilder().withConfig(kubeConfig).build().adapt(OpenShiftClient.class)) {
  // Do stuff with client
}
```

---

## DeploymentConfig

`DeploymentConfig` is available in OpenShift client via `client.deploymentConfigs()`.

```java
// Load from yaml
DeploymentConfig deploymentConfig = client.deploymentConfigs().inNamespace(currentNamespace)
  .load(new FileInputStream("test-deploymentconfig.yml")).item();

// Get
DeploymentConfig dc = client.deploymentConfigs().inNamespace(currentNamespace).withName("deploymentconfig1").get();

// Create
DeploymentConfig dc = new DeploymentConfigBuilder()
      .withNewMetadata().withName("deploymentconfig1").endMetadata()
      .withNewSpec()
      .withReplicas(2)
      .withNewTemplate()
      .withNewMetadata()
      .addToLabels("app", "database")
      .endMetadata()
      .withNewSpec()
      .addNewContainer()
      .withName("mysql")
      .withImage("openshift/mysql-55-centos7")
      .endContainer()
      .endSpec()
      .endTemplate()
      .endSpec()
      .build();
DeploymentConfig dcCreated = client.deploymentConfigs().inNamespace("default").resource(dc).create();

// List
DeploymentConfigList aDeploymentConfigList = client.deploymentConfigs().inNamespace("default").list();

// Delete
client.deploymentConfigs().inNamespace("default").withName("deploymentconfig1").delete();
```

---

## BuildConfig

`BuildConfig` resource is available in OpenShift Client via `client.buildConfigs()`.

```java
// Load from yaml
BuildConfig aBuildConfig = client.buildConfigs().inNamespace(currentNamespace)
  .load(new FileInputStream("/test-buildconfig.yml")).item();

// Get
BuildConfig bc = client.buildConfigs().inNamespace(currentNamespace).withName("bc1").get();

// Create
BuildConfig buildConfig1 = new BuildConfigBuilder()
  .withNewMetadata().withName("bc1").endMetadata()
  .withNewSpec()
  .addNewTrigger()
  .withType("GitHub")
  .withNewGithub()
  .withSecret("secret101")
  .endGithub()
  .endTrigger()
  .withNewSource()
  .withType("Git")
  .withNewGit()
  .withUri("https://github.com/openshift/ruby-hello-world")
  .endGit()
  .endSource()
  .withNewStrategy()
  .withType("Source")
  .withNewSourceStrategy()
  .withNewFrom()
  .withKind("ImageStreamTag")
  .withName("origin-ruby-sample:latest")
  .endFrom()
  .endSourceStrategy()
  .endStrategy()
  .endSpec()
  .build();
client.buildConfigs().inNamespace("default").resource(buildConfig1).create();

// List
BuildConfigList bcList = client.buildConfigs().inNamespace("default").list();

// Delete
client.buildConfigs().inNamespace("default").withName("bc1").delete();
```

---

## Route

`Route` resource is available in OpenShift client API via `client.routes()`.

```java
// Load from yaml
Route aRoute = client.routes().inNamespace("default").load(new FileInputStream("test-route.yml")).item();

// Get
Route route1 = client.routes().inNamespace("default").withName("route1").get();

// Create
Route route1 = new RouteBuilder()
      .withNewMetadata().withName("route1").endMetadata()
      .withNewSpec().withHost("www.example.com").withNewTo().withKind("Service").withName("service-name1").endTo().endSpec()
      .build();
client.routes().inNamespace("default").resource(route1).create();

// Apply
Route route = client.routes().inNamespace("default").resource(route1).serverSideApply();

// List
RouteList aRouteList = client.routes().inNamespace("default").list();

// Delete
client.routes().inNamespace("default").withName("route1").delete();
```

---

## Project

OpenShift `Project` resource can be found in OpenShift Client API via `client.projects()`.

```java
// Get
Project myProject = client.projects().withName("default").get();

// Create
ProjectRequest request = client.projectrequests().create(
  new ProjectRequestBuilder().withNewMetadata().withName("thisisatest").endMetadata()
  .withDescription("Fabric8").withDisplayName("Fabric8").build()
);

// List
ProjectList projectList = client.projects().list();

// Delete
client.projects().withName("default").delete();
```

---

## ImageStream

`ImageStream` resource is available in OpenShift client via `client.imageStreams()`.

```java
// Load from yaml
ImageStream aImageStream = client.imageStreams()
  .load(new FileInputStream("test-imagestream.yml")).item();

// Get
ImageStream is = client.imageStreams().inNamespace("default").withName("example-camel-cdi").get();

// Create
ImageStream imageStream1 = new ImageStreamBuilder()
      .withNewMetadata()
      .withName("example-camel-cdi")
      .endMetadata()
      .withNewSpec()
      .addNewTag()
      .withName("latest")
      .endTag()
      .withDockerImageRepository("fabric8/example-camel-cdi")
      .endSpec()
      .withNewStatus().withDockerImageRepository("").endStatus()
      .build();
client.imageStreams().inNamespace("default").resource(imageStream1).create();

// List
ImageStreamList aImageStreamList = client.imageStreams().inNamespace("default").list();

// Delete
client.imageStreams().inNamespace("default").withName("example-camel-cdi").delete();
```

---

## CatalogSource

`CatalogSource` is available for usage in OpenShift Client via `client.operatorHub().catalogSources()`.

```java
// Create
CatalogSource cs = new CatalogSourceBuilder()
  .withNewMetadata().withName("foo").endMetadata()
  .withNewSpec()
  .withSourceType("Foo")
  .withImage("nginx:latest")
  .withDisplayName("Foo Bar")
  .withPublisher("Fabric8")
  .endSpec()
  .build();
client.operatorHub().catalogSources().inNamespace("default").resource(cs).create();

// List
CatalogSourceList csList = client.operatorHub().catalogSources().inNamespace("ns1").list();

// Delete
client.operatorHub().catalogSources().inNamespace("default").withName("foo").delete();
```

---

## PrometheusRule

`PrometheusRule` is available for usage in OpenShift Client via `client.monitoring().prometheusRules()`.

```java
// Create
PrometheusRule prometheusRule = new PrometheusRuleBuilder()
    .withNewMetadata().withName("foo").endMetadata()
    .withNewSpec()
    .addNewGroup()
    .withName("./example-rules")
    .addNewRule()
    .withAlert("ExampleAlert")
    .withNewExpr().withStrVal("vector(1)").endExpr()
    .endRule()
    .endGroup()
    .endSpec()
    .build();
client.monitoring().prometheusRules().inNamespace("default").resource(prometheusRule).create();

// List
PrometheusRuleList prList = client.monitoring().prometheusRules().inNamespace("ns1").list();

// Delete
client.monitoring().prometheusRules().inNamespace("default").withName("foo").delete();
```

---

## ServiceMonitor

`ServiceMonitor` is available for usage in OpenShift Client via `client.monitoring().serviceMonitors()`.

```java
// Create
ServiceMonitor serviceMonitor = new ServiceMonitorBuilder()
    .withNewMetadata()
    .withName("foo")
    .addToLabels("prometheus", "frontend")
    .endMetadata()
    .withNewSpec()
    .withNewNamespaceSelector().withAny(true).endNamespaceSelector()
    .withNewSelector()
    .addToMatchLabels("prometheus", "frontend")
    .endSelector()
    .addNewEndpoint()
    .withPort("http-metric")
    .withInterval("15s")
    .endEndpoint()
    .endSpec()
    .build();
client.monitoring().serviceMonitors().inNamespace("default").resource(serviceMonitor).create();

// List
ServiceMonitorList serviceMonitorList = client.monitoring().serviceMonitors().inNamespace("ns1").list();

// Delete
client.monitoring().serviceMonitors().inNamespace("default").withName("foo").delete();
```

---

## ClusterResourceQuota

```java
try (OpenShiftClient client = new KubernetesClientBuilder().build().adapt(OpenShiftClient.class)) {
    Map<String, Quantity> hard = new HashMap<>();
    hard.put("pods", new Quantity("10"));
    hard.put("secrets", new Quantity("20"));
    ClusterResourceQuota acrq = new ClusterResourceQuotaBuilder()
            .withNewMetadata().withName("foo").endMetadata()
            .withNewSpec()
            .withNewSelector()
            .addToAnnotations("openshift.io/requester", "foo-user")
            .endSelector()
            .withQuota(new ResourceQuotaSpecBuilder()
                    .withHard(hard)
                    .build())
            .endSpec()
            .build();

    client.quotas().clusterResourceQuotas().resource(acrq).create();
}

// List
ClusterResourceQuotaList clusterResourceQuotaList = client.quotas().clusterResourceQuotas().list();

// Delete
client.quotas().clusterResourceQuotas().withName("foo").delete();
```

---

## ClusterVersion

```java
try (OpenShiftClient client = new KubernetesClientBuilder().build().adapt(OpenShiftClient.class)) {
    ClusterVersion clusterVersion = client.config().clusterVersions().withName("version").get();
    System.out.println("Cluster Version: " + clusterVersion.getStatus().getDesired().getVersion());
}
```

---

## EgressNetworkPolicy

`EgressNetworkPolicy` is available for usage in OpenShift Client via `client.egressNetworkPolicies()`.

```java
// Create
EgressNetworkPolicy enp = new EgressNetworkPolicyBuilder()
        .withNewMetadata()
        .withName("foo")
        .withNamespace("default")
        .endMetadata()
        .withNewSpec()
        .addNewEgress()
        .withType("Allow")
        .withNewTo()
        .withCidrSelector("1.2.3.0/24")
        .endTo()
        .endEgress()
        .addNewEgress()
        .withType("Allow")
        .withNewTo()
        .withDnsName("www.foo.com")
        .endTo()
        .endEgress()
        .endSpec()
        .build();
client.egressNetworkPolicies().inNamespace("default").resource(enp).create();

// List
EgressNetworkPolicyList egressNetworkPolicyList = client.egressNetworkPolicies().inNamespace("default").list();

// Delete
client.egressNetworkPolicies().inNamespace("default").withName("foo").delete();
```
