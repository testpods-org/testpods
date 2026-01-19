package org.testpods.core.wait;

import org.testpods.core.pods.TestPod;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;

/**
 * Waits for a TCP port to be open and accepting connections.
 * <p>
 * This strategy attempts to open a TCP socket connection to the pod's
 * external endpoint. It succeeds when the connection is accepted.
 * <p>
 * Example:
 * <pre>{@code
 * .waitingFor(WaitStrategy.forPort(8080))
 * .waitingFor(WaitStrategy.forPort(5432).withTimeout(Duration.ofSeconds(30)))
 * }</pre>
 */
public class PortWaitStrategy implements WaitStrategy {

    private final int port;
    private Duration timeout = Duration.ofMinutes(1);
    private Duration pollInterval = Duration.ofMillis(500);
    private Duration connectTimeout = Duration.ofSeconds(5);

    /**
     * Create a wait strategy for a specific port.
     *
     * @param port The port to check
     */
    public PortWaitStrategy(int port) {
        this.port = port;
    }

    @Override
    public void waitUntilReady(TestPod<?> pod) {
        long startTime = System.currentTimeMillis();
        long timeoutMillis = timeout.toMillis();

        String host = pod.getExternalHost();
        int externalPort = pod.getExternalPort();

        while (System.currentTimeMillis() - startTime < timeoutMillis) {
            if (isPortOpen(host, externalPort)) {
                return; // Success!
            }

            try {
                Thread.sleep(pollInterval.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for port", e);
            }
        }

        throw new IllegalStateException(
            "Port " + port + " on pod '" + pod.getName() + "' (" + host + ":" + externalPort + 
            ") did not become available within " + timeout);
    }

    private boolean isPortOpen(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(
                new InetSocketAddress(host, port), 
                (int) connectTimeout.toMillis()
            );
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public WaitStrategy withTimeout(Duration timeout) {
        PortWaitStrategy copy = new PortWaitStrategy(port);
        copy.timeout = timeout;
        copy.pollInterval = this.pollInterval;
        copy.connectTimeout = this.connectTimeout;
        return copy;
    }

    @Override
    public WaitStrategy withPollInterval(Duration interval) {
        PortWaitStrategy copy = new PortWaitStrategy(port);
        copy.timeout = this.timeout;
        copy.pollInterval = interval;
        copy.connectTimeout = this.connectTimeout;
        return copy;
    }

    /**
     * Set the connection timeout for each attempt.
     */
    public PortWaitStrategy withConnectTimeout(Duration connectTimeout) {
        PortWaitStrategy copy = new PortWaitStrategy(port);
        copy.timeout = this.timeout;
        copy.pollInterval = this.pollInterval;
        copy.connectTimeout = connectTimeout;
        return copy;
    }
}
