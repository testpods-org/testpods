# New Session Prompt: Create the External Pod Development Skill

Copy the prompt below into a new AI-agent session after the planned TestPods refactoring and
cleanup have been completed.

---

## Prompt

You are working in the TestPods repository. Create a project-owned AI agent skill that helps
developers add support for external infrastructure components such as MySQL, Kafka, PostgreSQL,
MongoDB, Redis, Nginx, RabbitMQ, and similar containerized services.

Do not assume that paths, packages, Maven modules, APIs, or agent-plugin structures described in
older specifications are still correct. The repository has been refactored since this prompt was
written.

### Start by grounding yourself in the current repository

Before proposing or making changes:

1. Read every applicable `AGENTS.md` and all repository instructions relevant to the files you may
   change.
2. Inspect the root Maven build, parent POM, BOM, core library, external pod modules, tests,
   examples, documentation site, and agent/plugin directories.
3. Find every current external pod implementation and compare its production code, unit tests,
   integration tests, component documentation, Maven dependencies, and example usage.
4. Identify the current architectural extension points for:
   - Deployment-backed and StatefulSet-backed pods
   - Workload, service, storage, and lifecycle management
   - Container creation and customization
   - Readiness and wait strategies
   - Internal Kubernetes access and external test-process access
   - Property publication
   - Initialization resources such as ConfigMaps and Secrets
   - PersistentVolumeClaims and restart behavior
5. Inspect the current AI-agent integration and determine where a project-owned skill must live and
   how it is discovered, packaged, documented, and validated.
6. Inspect current unit-test and integration-test conventions, including Maven Surefire/Failsafe
   naming and the prerequisites for tests that use a real Kubernetes cluster.
7. Inspect the example applications and establish how infrastructure components are currently
   demonstrated.
8. Check Git status and preserve unrelated user changes.

Treat the following only as historical context to help locate relevant areas:

- External pod implementations previously lived under
  `core/src/main/java/org/testpods/core/pods/external`.
- PostgreSQL and Kafka were the most complete examples. MongoDB was less complete and must not be
  used as the sole reference implementation.
- Placeholder `modules/kafka` and `modules/postgresql` projects existed, but they were not reliable
  examples of finished modules.
- The long-term direction was to isolate external pods from `testpods-core`.
- A distributable agent project existed under `testpods-agent`.

If the current repository clearly establishes the target skill platform and location, follow it.
If multiple genuinely supported platforms remain and selecting one would materially change the
deliverable, explain the discovered options and ask the user to choose before initializing the
skill.

### Follow the skill-creation guidelines

Use the skill-creation skill or guidelines available in the current agent environment. Read those
instructions completely and follow their required workflow, including:

- Understanding concrete invocation examples
- Selecting reusable references, assets, or deterministic scripts
- Initializing the skill with the prescribed initializer when required
- Keeping `SKILL.md` concise and using progressive disclosure
- Creating required UI or agent metadata
- Testing bundled scripts
- Running the official skill validator
- Forward-testing the skill with realistic, fresh tasks when supported

Use the tentative skill name `create-external-pod` unless current naming conventions require a
different name. Make its trigger description cover creating, extending, reviewing, or completing a
shared TestPods external infrastructure pod from a service name, container image, Docker Hub link,
upstream documentation, or a combination of these inputs.

Do not add auxiliary files inside the skill that its platform guidelines discourage. Put
contributor-facing installation and usage documentation in the repository's normal documentation
area when it does not belong inside the skill package.

### Capabilities the skill must provide

The skill must guide an agent through the complete creation of a production-quality external pod
contribution. It must not be only a Java class generator.

#### 1. Gather and research inputs

Accept inputs such as:

- Infrastructure component name
- Exact container image and tag
- Docker Hub, registry, vendor, or upstream documentation links
- Required feature set
- Expected client library or application framework
- Whether persistence, initialization, authentication, TLS, a management UI, or multiple nodes are
  required

When information is missing, research authoritative sources before generating code. Prefer the
official container-image documentation, upstream service documentation, and official client
documentation. Record source links in the resulting component documentation and final report.
Never invent defaults.

Research at least:

- Supported and appropriate image tags; avoid an unpinned `latest` default
- Image provenance, maintenance status, licensing, and architecture support when relevant
- Container ports and protocols
- Required and optional environment variables and command-line arguments
- Default users, passwords, databases, namespaces, virtual hosts, or authentication behavior
- How credentials are overridden
- Health-check, readiness, and liveness mechanisms actually available in the image
- Startup ordering and log messages
- Initialization directories, scripts, configuration files, and first-start semantics
- Data directories, ownership, permissions, and shutdown behavior
- Persistence requirements and what survives container, pod, workload, namespace, or PVC deletion
- Internal client connection format and externally forwarded connection format
- Image-specific differences that make one implementation incompatible with another image family
- Known single-node testing limitations and production-only features that should not be implied

If supplied documentation is incomplete or contradictory, state the uncertainty and ask for a
decision when it affects the public API or correctness.

#### 2. Design the pod against current TestPods architecture

Select Deployment, StatefulSet, sidecars, init containers, Services, ConfigMaps, Secrets, and
storage based on the component's real semantics.

Follow these design requirements unless the current repository has a documented replacement:

- A minimal constructor must work with sensible, test-focused defaults.
- Allow an explicit full image reference and a convenient version override where appropriate.
- Keep the fluent API type-safe and consistent with current TestPods modules.
- Validate invalid names, ports, quantities, credentials, and mutually incompatible options early.
- Expose domain-specific connection helpers useful to test code.
- Support internal Kubernetes DNS endpoints for in-cluster services.
- Support external endpoints for the test JVM using the repository's current access mechanism.
- Publish properties consistently with existing TestPods conventions.
- Use a readiness strategy that proves the service can perform its intended function when
  practical. A listening TCP port alone is insufficient for services whose protocol offers a
  dependable health operation.
- Add lifecycle hooks or extra Kubernetes resources only when needed and clean them up in the
  correct order.
- Keep component-specific dependencies in the external module. Do not move optional database,
  messaging, or vendor clients into core.
- Avoid logging secrets or placing them in labels, annotations, exception text, example output, or
  documentation.
- Prefer Kubernetes Secrets for sensitive generated resources when supported by the current
  architecture.
- Document deviations from the official image and any intentionally unsupported features.

Keep the implementation isolated so it can remain outside, or later move out of, the core library.
Do not introduce dependencies from core to a component module. Update the module aggregator, BOM,
and dependency management according to the current build layout.

#### 3. Handle state and persistence correctly

For a stateful component, provide an explicit ephemeral default unless current project conventions
say otherwise, plus a persistent-storage option with a configurable Kubernetes quantity.

The implementation and documentation must distinguish:

- A container restart inside a pod
- Pod deletion and recreation by the owning workload
- StatefulSet rollout or restart
- Workload deletion
- PVC deletion
- Namespace deletion

When claiming that data survives a restart, prove the intended lifecycle with an integration test.
For example: write data, cause the owning workload to replace the pod without deleting its PVC,
wait for readiness again, reconnect, and verify the data. Do not describe `emptyDir` as persistent,
and do not claim survival after namespace or PVC deletion.

Document the image's data mount path, PVC access mode, requested capacity, first-start
initialization behavior, and cleanup consequences.

#### 4. Generate production code and documentation

Create all artifacts required by the current repository, which may include:

- Maven module and POM
- Module aggregator and BOM entries
- Component package
- Main `<Component>Pod` Java class
- Component-specific wait strategy, lifecycle hook, support type, or client helper when justified
- Unit tests
- Real-cluster integration tests
- Test resources such as initialization scripts
- Module or website documentation

Do not create empty placeholder classes or tests. Do not copy an existing pod blindly: derive the
implementation from researched image behavior and current framework abstractions.

Component documentation must cover:

- Dependency coordinates
- Supported/default image and source links
- Minimal usage
- Version or image override
- Internal and external connection details
- Default credentials and secure overrides, or a clear statement that no credentials exist
- Initialization
- Ephemeral and persistent storage
- Restart-survival semantics
- Readiness behavior
- Published properties
- Supported customizations and known limitations
- Integration-test prerequisites

#### 5. Add meaningful tests and an extension point for more tests

Create focused unit tests for relevant behavior:

- Default name, image, ports, and workload kind
- Fluent configuration and validation
- Generated container, environment, probes, commands, mounts, and resource references
- Service configuration
- Internal and external connection helpers
- Published properties
- Initialization ConfigMap or Secret creation and cleanup
- Ephemeral versus persistent storage configuration
- Wait-strategy selection and timeout behavior
- Image-family-specific mappings when more than one family is supported

Create integration-test scaffolding that compiles and is a genuine starting point, not a disabled
empty test. Add executable cases appropriate to the component:

- Start and become ready in a real Kubernetes cluster
- Connect using an official or standard client and perform a minimal round trip
- Override credentials and prove authentication works
- Apply initialization data and verify its effect
- Verify internal connectivity when the repository has an established test pattern
- Verify persistent data after a controlled pod replacement for stateful components
- Verify topics, queues, databases, HTTP responses, cache values, or other domain behavior
- Stop cleanly and clean up component-owned resources

Follow current Surefire and Failsafe naming. Keep environment-dependent integration tests out of the
ordinary unit-test phase according to current build conventions. Never copy debug code that waits
forever, holds pods open by default, assumes fixed local ports unnecessarily, or prevents automated
cleanup. Clearly document how to run the integration tests and how they are skipped or fail when
their Kubernetes prerequisite is absent.

#### 6. Assess examples without silently expanding scope

Inspect every relevant example service after implementing the component. Decide whether an example
would demonstrate a realistic use case without duplicating an existing example or forcing an
unrelated application change.

By default, do not modify examples. Instead, include a concrete optional suggestion containing:

- Why the example benefits
- Which example should change
- Expected dependency and configuration changes
- Proposed test or application flow
- Files likely to be affected
- Extra runtime or CI cost

Only implement the example change when the invoking user explicitly authorizes it. If no example
would add meaningful value, say so and explain why.

### Document how developers use the skill

Add contributor-facing documentation in the current canonical documentation area. Explain:

- How the skill is installed or discovered
- How to invoke it
- Required and optional inputs
- What it researches
- What files it may create or update
- Which decisions require user confirmation
- How tests and validation work
- How example suggestions are handled
- Limitations and troubleshooting

Include copy-ready usage examples similar to:

```text
Use $create-external-pod to add MySQL support using the official image documentation:
https://hub.docker.com/_/mysql

Use $create-external-pod to add Nginx from nginx:1.27-alpine. Support custom configuration and
verify it with an HTTP integration test.

Use $create-external-pod to add a database pod from these vendor docs: <URL>. Determine the
documented default user and authentication behavior, add safe credential overrides and an init
script, and support storage that survives StatefulSet pod replacement during a test.

Use $create-external-pod to review the existing <Component>Pod and fill gaps in its persistence,
readiness, tests, and documentation.
```

Adjust invocation syntax to the actual agent platform.

### Validate the finished skill and repository changes

After implementation:

1. Run the official structural validator for the skill.
2. Test every bundled script directly.
3. Run formatting checks without overwriting unrelated user changes.
4. Run the affected Maven unit tests.
5. Compile integration tests and run them when the required Kubernetes environment is safely
   available.
6. Forward-test the skill, when supported, with at least:
   - One stateless HTTP component such as Nginx
   - One stateful database such as MySQL
   Use fresh tasks and review the resulting artifacts rather than feeding the evaluator the
   expected answer.
7. Correct problems found by forward-testing and re-run validation.
8. Review Git diff for accidental generated placeholders, unrelated changes, secrets, stale paths,
   or changes that couple core to an external module.

Finish with a concise report containing:

- Skill location and invocation
- Files created or changed
- Architectural decisions
- Authoritative sources used
- Tests and validators run, with results
- Integration tests not run and their prerequisites
- Assumptions and unsupported features
- Optional example-service suggestion

Do not stop after producing a plan. Implement and validate the skill unless blocked by a decision
that cannot safely be inferred from the current repository or by an unavailable required external
resource.
