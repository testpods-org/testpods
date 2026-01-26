package org.testpods.core.wait;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import org.testpods.core.pods.TestPod;

/**
 * Waits for an HTTP endpoint to return a successful status code.
 *
 * <p>By default, considers 2xx status codes as success. This can be customized.
 *
 * <p>Example:
 *
 * <pre>{@code
 * .waitingFor(WaitStrategy.forHttp("/health", 8080))
 * .waitingFor(WaitStrategy.forHttp("/actuator/health", 8080)
 *     .withTimeout(Duration.ofSeconds(60))
 *     .forStatusCode(200))
 * }</pre>
 */
public class HttpWaitStrategy implements WaitStrategy {

  private final String path;
  private final int port;
  private Duration timeout = Duration.ofMinutes(1);
  private Duration pollInterval = Duration.ofSeconds(1);
  private Duration readTimeout = Duration.ofSeconds(5);
  private Set<Integer> expectedStatusCodes = new HashSet<>(Set.of(200, 201, 202, 204));
  private String method = "GET";
  private boolean tlsEnabled = false;

  /**
   * Create a wait strategy for an HTTP endpoint.
   *
   * @param path The path to request (e.g., "/health")
   * @param port The port the HTTP server listens on
   */
  public HttpWaitStrategy(String path, int port) {
    this.path = path.startsWith("/") ? path : "/" + path;
    this.port = port;
  }

  @Override
  public void waitUntilReady(TestPod<?> pod) {
    long startTime = System.currentTimeMillis();
    long timeoutMillis = timeout.toMillis();

    String host = pod.getExternalHost();
    int externalPort = pod.getExternalPort();
    String protocol = tlsEnabled ? "https" : "http";
    String url = protocol + "://" + host + ":" + externalPort + path;

    HttpClient client = HttpClient.newBuilder().connectTimeout(readTimeout).build();

    while (System.currentTimeMillis() - startTime < timeoutMillis) {
      try {
        HttpRequest request =
            HttpRequest.newBuilder()
                .uri(URI.create(url))
                .method(method, HttpRequest.BodyPublishers.noBody())
                .timeout(readTimeout)
                .build();

        HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());

        if (expectedStatusCodes.contains(response.statusCode())) {
          return; // Success!
        }

      } catch (IOException | InterruptedException e) {
        // Connection failed, continue waiting
        if (e instanceof InterruptedException) {
          Thread.currentThread().interrupt();
          throw new IllegalStateException("Interrupted while waiting for HTTP endpoint", e);
        }
      }

      try {
        Thread.sleep(pollInterval.toMillis());
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Interrupted while waiting for HTTP endpoint", e);
      }
    }

    throw new IllegalStateException(
        "HTTP endpoint "
            + url
            + " on pod '"
            + pod.getName()
            + "' did not return expected status within "
            + timeout);
  }

  @Override
  public WaitStrategy withTimeout(Duration timeout) {
    HttpWaitStrategy copy = new HttpWaitStrategy(path, port);
    copy.timeout = timeout;
    copy.pollInterval = this.pollInterval;
    copy.readTimeout = this.readTimeout;
    copy.expectedStatusCodes = new HashSet<>(this.expectedStatusCodes);
    copy.method = this.method;
    copy.tlsEnabled = this.tlsEnabled;
    return copy;
  }

  @Override
  public WaitStrategy withPollInterval(Duration interval) {
    HttpWaitStrategy copy = new HttpWaitStrategy(path, port);
    copy.timeout = this.timeout;
    copy.pollInterval = interval;
    copy.readTimeout = this.readTimeout;
    copy.expectedStatusCodes = new HashSet<>(this.expectedStatusCodes);
    copy.method = this.method;
    copy.tlsEnabled = this.tlsEnabled;
    return copy;
  }

  /** Set the read timeout for HTTP requests. */
  public HttpWaitStrategy withReadTimeout(Duration readTimeout) {
    HttpWaitStrategy copy = new HttpWaitStrategy(path, port);
    copy.timeout = this.timeout;
    copy.pollInterval = this.pollInterval;
    copy.readTimeout = readTimeout;
    copy.expectedStatusCodes = new HashSet<>(this.expectedStatusCodes);
    copy.method = this.method;
    copy.tlsEnabled = this.tlsEnabled;
    return copy;
  }

  /** Expect only a specific status code. */
  public HttpWaitStrategy forStatusCode(int statusCode) {
    HttpWaitStrategy copy = new HttpWaitStrategy(path, port);
    copy.timeout = this.timeout;
    copy.pollInterval = this.pollInterval;
    copy.readTimeout = this.readTimeout;
    copy.expectedStatusCodes = new HashSet<>(Set.of(statusCode));
    copy.method = this.method;
    copy.tlsEnabled = this.tlsEnabled;
    return copy;
  }

  /** Expect any of the specified status codes. */
  public HttpWaitStrategy forStatusCodes(Integer... statusCodes) {
    HttpWaitStrategy copy = new HttpWaitStrategy(path, port);
    copy.timeout = this.timeout;
    copy.pollInterval = this.pollInterval;
    copy.readTimeout = this.readTimeout;
    copy.expectedStatusCodes = new HashSet<>(Set.of(statusCodes));
    copy.method = this.method;
    copy.tlsEnabled = this.tlsEnabled;
    return copy;
  }

  /** Use HTTPS instead of HTTP. */
  public HttpWaitStrategy usingTls() {
    HttpWaitStrategy copy = new HttpWaitStrategy(path, port);
    copy.timeout = this.timeout;
    copy.pollInterval = this.pollInterval;
    copy.readTimeout = this.readTimeout;
    copy.expectedStatusCodes = new HashSet<>(this.expectedStatusCodes);
    copy.method = this.method;
    copy.tlsEnabled = true;
    return copy;
  }

  /** Use a different HTTP method (default is GET). */
  public HttpWaitStrategy withMethod(String method) {
    HttpWaitStrategy copy = new HttpWaitStrategy(path, port);
    copy.timeout = this.timeout;
    copy.pollInterval = this.pollInterval;
    copy.readTimeout = this.readTimeout;
    copy.expectedStatusCodes = new HashSet<>(this.expectedStatusCodes);
    copy.method = method;
    copy.tlsEnabled = this.tlsEnabled;
    return copy;
  }
}
