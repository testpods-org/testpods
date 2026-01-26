package org.testpods.core.service;

import io.fabric8.kubernetes.api.model.Service;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Combines multiple service managers for pods needing multiple service types.
 *
 * <p>StatefulSets typically need both:
 *
 * <ul>
 *   <li>A Headless service for stable DNS names
 *   <li>A NodePort or ClusterIP service for external/internal access
 * </ul>
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * ServiceManager manager = new CompositeServiceManager(
 *     new HeadlessServiceManager(),
 *     new NodePortServiceManager()
 * ).withSuffixes("-headless", "");
 * }</pre>
 */
public class CompositeServiceManager implements ServiceManager {

  private static final Logger LOG = LoggerFactory.getLogger(CompositeServiceManager.class);

  private final List<ServiceManager> managers;
  private final List<String> nameSuffixes;
  private ServiceConfig baseConfig;

  /**
   * Create a composite manager from multiple service managers.
   *
   * <p>The first manager is considered the "primary" and its service is returned by {@link
   * #getService()}.
   *
   * @param managers the service managers to compose
   */
  public CompositeServiceManager(ServiceManager... managers) {
    this.managers = List.of(managers);
    this.nameSuffixes = new ArrayList<>();
    // Default suffixes: first has no suffix, subsequent get -1, -2, etc.
    for (int i = 0; i < managers.length; i++) {
      nameSuffixes.add(i == 0 ? "" : "-" + i);
    }
  }

  /**
   * Set custom name suffixes for each service.
   *
   * <p>The number of suffixes should match the number of managers.
   *
   * @param suffixes the name suffixes (e.g., "-headless", "")
   * @return this manager for chaining
   */
  public CompositeServiceManager withSuffixes(String... suffixes) {
    nameSuffixes.clear();
    for (String suffix : suffixes) {
      nameSuffixes.add(suffix != null ? suffix : "");
    }
    return this;
  }

  @Override
  public Service create(ServiceConfig config) {
    this.baseConfig = config;
    Service primary = null;

    for (int i = 0; i < managers.size(); i++) {
      ServiceManager manager = managers.get(i);
      String suffix = i < nameSuffixes.size() ? nameSuffixes.get(i) : "";

      // Create adjusted config with suffixed name
      ServiceConfig adjustedConfig =
          new ServiceConfig(
              config.name() + suffix,
              config.namespace(),
              config.port(),
              config.labels(),
              config.selector(),
              config.customizers(),
              config.client());

      Service svc = manager.create(adjustedConfig);
      if (primary == null) {
        primary = svc;
      }
    }

    LOG.debug(
        "Created {} services for composite: {}/{}",
        managers.size(),
        config.namespace(),
        config.name());
    return primary;
  }

  @Override
  public void delete() {
    // Delete in reverse order
    for (int i = managers.size() - 1; i >= 0; i--) {
      try {
        managers.get(i).delete();
      } catch (Exception e) {
        LOG.debug("Failed to delete service from manager {}: {}", i, e.getMessage());
        // Continue deleting other services
      }
    }
  }

  @Override
  public Service getService() {
    return managers.isEmpty() ? null : managers.get(0).getService();
  }

  /**
   * Get a specific service by index.
   *
   * @param index the index of the manager (0-based)
   * @return the service from that manager, or null
   */
  public Service getService(int index) {
    if (index < 0 || index >= managers.size()) {
      return null;
    }
    return managers.get(index).getService();
  }

  /**
   * Get a specific service manager by index.
   *
   * @param index the index of the manager (0-based)
   * @return the service manager, or null
   */
  public ServiceManager getManager(int index) {
    if (index < 0 || index >= managers.size()) {
      return null;
    }
    return managers.get(index);
  }

  @Override
  public String getName() {
    return baseConfig != null ? baseConfig.name() : null;
  }

  @Override
  public String getServiceType() {
    return "Composite";
  }

  /**
   * Get the number of service managers in this composite.
   *
   * @return the number of managers
   */
  public int size() {
    return managers.size();
  }
}
