package org.testpods.core.cluster;

/**
 * A host and port combination for network access. Immutable value object representing a network
 * endpoint.
 */
public record HostAndPort(String host, int port) {

  /**
   * Create a HostAndPort from a host:port string.
   *
   * @param hostPort String in format "host:port"
   * @return HostAndPort instance
   * @throws IllegalArgumentException if format is invalid
   */
  public static HostAndPort parse(String hostPort) {
    if (hostPort == null || !hostPort.contains(":")) {
      throw new IllegalArgumentException("Invalid host:port format: " + hostPort);
    }
    int colonIndex = hostPort.lastIndexOf(':');
    String host = hostPort.substring(0, colonIndex);
    int port = Integer.parseInt(hostPort.substring(colonIndex + 1));
    return new HostAndPort(host, port);
  }

  /** Create a localhost endpoint with the given port. */
  public static HostAndPort localhost(int port) {
    return new HostAndPort("127.0.0.1", port);
  }

  /** Get the endpoint as a URL-safe string. */
  @Override
  public String toString() {
    // Handle IPv6 addresses
    if (host.contains(":")) {
      return "[" + host + "]:" + port;
    }
    return host + ":" + port;
  }

  /** Get the endpoint as a URL (http://host:port). */
  public String toHttpUrl() {
    return "http://" + this;
  }

  /** Get the endpoint as an HTTPS URL. */
  public String toHttpsUrl() {
    return "https://" + this;
  }
}
