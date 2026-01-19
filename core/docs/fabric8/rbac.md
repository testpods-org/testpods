# RBAC & Security Resources

Keywords: ClusterRole, ClusterRoleBinding, Role, RoleBinding, rbac(), authorization(), SelfSubjectAccessReview, SubjectAccessReview, LocalSubjectAccessReview, CertificateSigningRequest, PodDisruptionBudget, policy()

## PodDisruptionBudget

`PodDisruptionBudget` is available in Kubernetes Client API via `client.policy().v1().podDisruptionBudget()`. Here are some of the examples of its usage:

### Loading from YAML
- Load `PodDisruptionBudget` from yaml:
```java
PodDisruptionBudget pdb = client.policy().v1().podDisruptionBudget().load(new FileInputStream("/test-pdb.yml")).item();
```

### Getting a PodDisruptionBudget
- Get `PodDisruptionBudget` from Kubernetes API server:
```java
PodDisruptionBudget podDisruptionBudget = client.policy().v1().podDisruptionBudget().inNamespace("default").withName("poddisruptionbudget1").get();
```

### Creating a PodDisruptionBudget
- Create `PodDisruptionBudget`:
```java
PodDisruptionBudget podDisruptionBudget = new PodDisruptionBudgetBuilder()
    .withNewMetadata().withName("zk-pkb").endMetadata()
    .withNewSpec()
    .withMaxUnavailable(new IntOrString("1%"))
    .withNewSelector()
    .withMatchLabels(Collections.singletonMap("app", "zookeeper"))
    .endSelector()
    .endSpec()
    .build();

client.policy().v1().podDisruptionBudget().inNamespace("default").resource(podDisruptionBudget).create();
```

### Listing and Deleting
- List `PodDisruptionBudget` in some namespace:
```java
PodDisruptionBudgetList podDisruptionBudgetList = client.policy().v1().podDisruptionBudget().inNamespace("default").list();
```
- Delete `PodDisruptionBudget`:
```java
client.policy().v1().podDisruptionBudget().inNamespace("default").withName("poddisruptionbudget1").delete();
```

---

## Access Reviews

### SelfSubjectAccessReview
- Create `SelfSubjectAccessReview`(equivalent of `kubectl auth can-i create deployments --namespace dev`):
```java
try (KubernetesClient client = new KubernetesClientBuilder().build()) {
    SelfSubjectAccessReview ssar = new SelfSubjectAccessReviewBuilder()
            .withNewSpec()
            .withNewResourceAttributes()
            .withGroup("apps")
            .withResource("deployments")
            .withVerb("create")
            .withNamespace("dev")
            .endResourceAttributes()
            .endSpec()
            .build();

    ssar = client.authorization().v1().selfSubjectAccessReview().create(ssar);

    System.out.println("Allowed: "+  ssar.getStatus().getAllowed());
}
```

### SubjectAccessReview
- Create `SubjectAccessReview`:
```java
try (KubernetesClient client = new KubernetesClientBuilder().build()) {
    SubjectAccessReview sar = new SubjectAccessReviewBuilder()
            .withNewSpec()
            .withNewResourceAttributes()
            .withGroup("apps")
            .withResource("deployments")
            .withVerb("create")
            .withNamespace("default")
            .endResourceAttributes()
            .withUser("kubeadmin")
            .endSpec()
            .build();

    sar = client.authorization().v1().subjectAccessReview().create(sar);

    System.out.println("Allowed: "+  sar.getStatus().getAllowed());
}
```

### LocalSubjectAccessReview
- Create `LocalSubjectAccessReview`:
```java
try (KubernetesClient client = new KubernetesClientBuilder().build()) {
    LocalSubjectAccessReview lsar = new LocalSubjectAccessReviewBuilder()
            .withNewMetadata().withNamespace("default").endMetadata()
            .withNewSpec()
            .withUser("foo")
            .withNewResourceAttributes()
            .withNamespace("default")
            .withVerb("get")
            .withGroup("apps")
            .withResource("pods")
            .endResourceAttributes()
            .endSpec()
            .build();
     lsar = client.authorization().v1().localSubjectAccessReview().inNamespace("default").create(lsar);
     System.out.println(lsar.getStatus().getAllowed());
}
```

### SelfSubjectRulesReview
- Create `SelfSubjectRulesReview`:
```java
try (KubernetesClient client = new KubernetesClientBuilder().build()) {
    SelfSubjectRulesReview selfSubjectRulesReview = new SelfSubjectRulesReviewBuilder()
            .withNewMetadata().withName("foo").endMetadata()
            .withNewSpec()
            .withNamespace("default")
            .endSpec()
            .build();

    selfSubjectRulesReview = client.authorization().v1().selfSubjectRulesReview().create(selfSubjectRulesReview);
    System.out.println(selfSubjectRulesReview.getStatus().getIncomplete());
    System.out.println("non resource rules: " + selfSubjectRulesReview.getStatus().getNonResourceRules().size());
    System.out.println("resource rules: " + selfSubjectRulesReview.getStatus().getResourceRules().size());
}
```

---

## ClusterRole

`ClusterRole` is available in Kubernetes Client API via `client.rbac().clusterRoles()`. Here are some of the common usages:

- Load `ClusterRole` from yaml:
```java
ClusterRole clusterRole = client.rbac().clusterRoles().load(new FileInputStream("clusterroles-test.yml")).item();
```
- Get `ClusterRole` from Kubernetes API server:
```java
ClusterRole clusterRole = client.rbac().clusterRoles().withName("clusterrole1").get();
```
- List `ClusterRole` objects:
```java
ClusterRoleList clusterRoleList = client.rbac().clusterRoles().list();
```
- Delete `ClusterRole` objects:
```java
client.rbac().clusterRoles().withName("clusterrole1").delete();
```

---

## ClusterRoleBinding

`ClusterRoleBinding` is available in Kubernetes Client API via `client.rbac().clusterRoleBindings()`. Here are some of the common usages:

- Create `ClusterRoleBinding`:
```java
List<Subject> subjects = new ArrayList<>();
Subject subject = new Subject();
subject.setKind("ServiceAccount");
subject.setName("serviceaccountname");
subject.setNamespace("default");
subjects.add(subject);
RoleRef roleRef = new RoleRef();
roleRef.setApiGroup("rbac.authorization.k8s.io");
roleRef.setKind("ClusterRole");
roleRef.setName("clusterrolename");
ClusterRoleBinding clusterRoleBindingCreated = new ClusterRoleBindingBuilder()
        .withNewMetadata().withName("clusterrolebindingname").withNamespace("default").endMetadata()
        .withRoleRef(roleRef)
        .addAllToSubjects(subjects)
        .build();
ClusterRoleBinding clusterRoleBinding = client.rbac().clusterRoleBindings().resource(clusterRoleBindingCreated).create();
```
- Get `ClusterRoleBinding` from Kubernetes API server:
```java
ClusterRoleBinding clusterRoleBinding = client.rbac().clusterRoleBindings().withName("clusterrolebindingname").get();
```
- List and Delete `ClusterRoleBinding` objects:
```java
ClusterRoleBindingList clusterRoleBindingList = client.rbac().clusterRoleBindings().list();
client.rbac().clusterRoleBindings().withName("clusterrolebindingname").delete();
```

---

## Role

`Role` is available in Kubernetes Client API via `client.rbac().roles()`. Here are some of the common usages:

- Create `Role`:
```java
List<PolicyRule> policyRuleList = new ArrayList<>();
PolicyRule endpoints = new PolicyRule();
endpoints.setApiGroups(Arrays.asList(""));
endpoints.setResources(Arrays.asList("endpoints"));
endpoints.setVerbs(Arrays.asList("get", "list", "watch", "create", "update", "patch"));
policyRuleList.add(endpoints);
Role roleCreated = new RoleBuilder()
        .withNewMetadata().withName("rolename").withNamespace("default").endMetadata()
        .addAllToRules(policyRuleList)
        .build();
Role role = client.rbac().roles().resource(roleCreated).create();
```
- Get, List, Delete `Role`:
```java
Role role = client.rbac().roles().inNamespace("default").withName("rolename").get();
RoleList roleList = client.rbac().roles().inNamespace("default").list();
client.rbac().roles().withName("rolename").delete();
```

---

## RoleBinding

`RoleBinding` is available in Kubernetes Client API via `client.rbac().roleBindings()`. Here are some of the common usages:

- Create `RoleBinding`:
```java
List<Subject> subjects = new ArrayList<>();
Subject subject = new Subject();
subject.setNamespace("default");
subject.setKind("ServiceAccount");
subject.setName("servicecccountname");
subjects.add(subject);
RoleRef roleRef = new RoleRef();
roleRef.setName("rolename");
roleRef.setKind("Role");
roleRef.setApiGroup("rbac.authorization.k8s.io");
RoleBinding roleBindingToCreate = new RoleBindingBuilder()
        .withNewMetadata().withName("rolename").withNamespace("default").endMetadata()
        .addAllToSubjects(subjects)
        .withRoleRef(roleRef)
        .build();
RoleBinding roleBinding = client.rbac().roleBindings().resource(roleBindingToCreate).create();
```
- Get, List, Delete `RoleBinding`:
```java
RoleBinding roleBinding = client.rbac().roleBindings().inNamespace("default").withName("rolename").get();
RoleBindingList roleBindingList = client.rbac().roleBindings().inNamespace("default").list();
client.rbac().roleBindings().inNamespace("default").withName("rolename").delete();
```

---

## CertificateSigningRequest

Kubernetes Client provides using `CertificateSigningRequest` via the `client.certificates().v1().certificateSigningRequests()` DSL interface.

- Create `CertificateSigningRequest`:
```java
CertificateSigningRequest csr = new CertificateSigningRequestBuilder()
        .withNewMetadata().withName("test-k8s-csr").endMetadata()
        .withNewSpec()
        .addNewGroup("system:authenticated")
        .withRequest("base64-encoded-csr-here")
        .addNewUsage("client auth")
        .endSpec()
        .build();

client.certificates().v1().certificateSigningRequests().resource(csr).create();
```

- Approve a `CertificateSigningRequest`:
```java
CertificateSigningRequestCondition csrCondition = new CertificateSigningRequestConditionBuilder()
        .withType("Approved")
        .withStatus("True")
        .withReason("Approved ViaRESTApi")
        .withMessage("Approved by REST API /approval endpoint.")
        .build();
client.certificates().v1().certificateSigningRequests().withName("test-k8s-csr").approve(csrCondition);
```

- Deny a `CertificateSigningRequest`:
```java
CertificateSigningRequestCondition csrCondition = new CertificateSigningRequestConditionBuilder()
        .withType("Denied")
        .withStatus("True")
        .withReason("Denied ViaRESTApi")
        .withMessage("Denied by REST API /approval endpoint.")
        .build();
client.certificates().v1().certificateSigningRequests().withName("test-k8s-csr").deny(csrCondition);
```
