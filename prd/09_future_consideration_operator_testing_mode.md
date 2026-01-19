# 9. Future Consideration: Operator Testing Mode

<!-- TOC -->
- [9. Future Consideration: Operator Testing Mode](#9-future-consideration-operator-testing-mode)
  - [9.1. Overview](#91-overview)
  - [9.2. Operator Testing vs Service Testing](#92-operator-testing-vs-service-testing)
  - [9.3. Conceptual API for Operator Testing](#93-conceptual-api-for-operator-testing)
  - [9.4. Requirements for Operator Testing (Future)](#94-requirements-for-operator-testing-future)
  - [9.5. Scope Decision](#95-scope-decision)
<!-- /TOC -->

## 9.1. Overview

A future enhancement to Testpods could support testing custom Kubernetes Operators as the "service under test" rather than traditional Spring Boot applications. This is a distinct but related use case.

## 9.2. Operator Testing vs Service Testing

| Aspect | Service Testing (Current) | Operator Testing (Future) |
|--------|---------------------------|---------------------------|
| Service Under Test | Spring Boot application | Kubernetes Controller/Operator |
| Deployment | Container image → Pod | Controller + CRDs + RBAC |
| Test Interaction | HTTP/REST, messaging, database | Kubernetes API (create/watch CRs) |
| Verification | Response bodies, state, events | Reconciliation, created resources |
| Build Step | Maven/Gradle → JAR → Image | operator-sdk/kubebuilder → Image |
| Runtime | JVM in container | Controller watching K8s API |

## 9.3. Conceptual API for Operator Testing

```java
@Testpods
class OrderProcessorOperatorTest {

    static ECommerceInfrastructure infra = new ECommerceInfrastructure();

    // Operator under test - built and deployed to cluster
    @OperatorUnderTest
    static TestpodsOperator operator = TestpodsOperator.builder()
        .name("order-processor-operator")
        .fromLocalSource("./operator")
            .buildTool(BuildTool.OPERATOR_SDK)
        .withCRDs("classpath:crds/*.yaml")
        .withRBAC("classpath:rbac/")
        .namespace("order-system")
        .dependsOn(infra)
        .build();

    @Autowired
    KubernetesClient k8sClient;

    @Test
    void shouldReconcileOrderRequest() {
        // Create Custom Resource
        OrderRequest order = new OrderRequestBuilder()
            .withNewMetadata()
                .withName("test-order-001")
                .withNamespace("order-system")
            .endMetadata()
            .withNewSpec()
                .withCustomerId("CUST-123")
                .withItems(List.of(
                    new OrderItem("SKU-001", 2),
                    new OrderItem("SKU-002", 1)
                ))
            .endSpec()
            .build();

        k8sClient.resource(order).create();

        // Verify operator reconciliation
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            OrderRequest result = k8sClient
                .resources(OrderRequest.class)
                .inNamespace("order-system")
                .withName("test-order-001")
                .get();

            assertThat(result.getStatus().getPhase()).isEqualTo("Processed");
        });

        // Verify operator created downstream resources
        List<Job> jobs = k8sClient.batch().v1().jobs()
            .inNamespace("order-system")
            .withLabel("order-id", "test-order-001")
            .list().getItems();

        assertThat(jobs).hasSize(1);
    }
}
```

## 9.4. Requirements for Operator Testing (Future)

1. **Build Integration**: Support for operator-sdk, kubebuilder, ko, jib
2. **CRD Management**: Install CRDs before operator deployment
3. **RBAC Setup**: Create ServiceAccounts, Roles, RoleBindings
4. **Kubernetes Client Integration**: Inject Fabric8 or official K8s client
5. **CR Type Generation**: Generate Java classes from CRD schemas
6. **Reconciliation Verification**: Tools to verify operator behavior

## 9.5. Scope Decision

Operator testing is **deferred to a future phase** because:
- Core service testing use case must be solid first
- Operator testing has additional complexity (build tools, CRDs, RBAC)
- Different target audience (platform engineers vs application developers)
- Can be added as a separate module (`testpods-operator-testing`)
