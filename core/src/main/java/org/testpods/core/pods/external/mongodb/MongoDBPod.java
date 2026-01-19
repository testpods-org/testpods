package org.testpods.core.pods.external.mongodb;

import org.testpods.core.pods.PropertyContext;
import org.testpods.core.pods.StatefulSetPod;


import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.ContainerPortBuilder;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import org.testpods.core.wait.WaitStrategy;

import java.util.ArrayList;
import java.util.List;

/**
 * A MongoDB instance for testing, backed by a Kubernetes StatefulSet.
 * <p>
 * Features:
 * <ul>
 *   <li>Sensible defaults - just {@code new MongoDBPod()} works</li>
 *   <li>Version selection via {@link #withVersion(String)}</li>
 *   <li>Authentication via {@link #withCredentials(String, String)}</li>
 *   <li>Database selection via {@link #withDatabase(String)}</li>
 *   <li>Connection string helpers for both internal and external access</li>
 *   <li>Client factory for convenience</li>
 * </ul>
 * <p>
 * Example:
 * <pre>{@code
 * MongoDBPod mongo = new MongoDBPod()
 *     .withVersion("6.0")
 *     .withCredentials("admin", "secret")
 *     .withDatabase("testdb")
 *     .inNamespace(namespace);
 * 
 * mongo.start();
 * 
 * // For test code (external)
 * MongoClient client = mongo.createClient();
 * 
 * // For other pods (internal)
 * String internalUri = mongo.getInternalConnectionString();
 * }</pre>
 */
public class MongoDBPod extends StatefulSetPod<MongoDBPod> {

    private static final String DEFAULT_IMAGE = "mongo:6.0";
    private static final int DEFAULT_PORT = 27017;

    private String image = DEFAULT_IMAGE;
    private String username;
    private String password;
    private String database = "test";
    private String replicaSetName;

    /**
     * Create a MongoDB pod with default settings.
     */
    public MongoDBPod() {
        this.name = "mongodb";
        this.labels.put("app", "mongodb");
    }

    /**
     * Create a MongoDB pod with a custom image.
     *
     * @param image Full image reference (e.g., "mongo:5.0", "myregistry/mongo:custom")
     */
    public MongoDBPod(String image) {
        this();
        this.image = image;
    }

    // =============================================================
    // MongoDB-specific fluent API
    // =============================================================

    /**
     * Set the MongoDB version.
     *
     * @param version Version tag (e.g., "6.0", "5.0.18", "latest")
     */
    public MongoDBPod withVersion(String version) {
        this.image = "mongo:" + version;
        return this;
    }

    /**
     * Configure authentication credentials.
     * Sets MONGO_INITDB_ROOT_USERNAME and MONGO_INITDB_ROOT_PASSWORD.
     */
    public MongoDBPod withCredentials(String username, String password) {
        this.username = username;
        this.password = password;
        return this;
    }

    /**
     * Set the default database name.
     */
    public MongoDBPod withDatabase(String database) {
        this.database = database;
        return this;
    }

    /**
     * Configure as a replica set member.
     * Note: For testing, single-node replica sets are supported.
     */
    public MongoDBPod withReplicaSet(String name) {
        this.replicaSetName = name;
        return this;
    }

    // =============================================================
    // Connection information
    // =============================================================

    @Override
    public MongoDBPod waitingFor(WaitStrategy strategy) {
        return null;
    }

    @Override
    public int getInternalPort() {
        return DEFAULT_PORT;
    }

    /**
     * Get the MongoDB connection string for test code (external access).
     */
    public String getConnectionString() {
        return buildConnectionString(getExternalHost(), getExternalPort());
    }

    /**
     * Get the MongoDB connection string for other pods (internal access).
     */
    public String getInternalConnectionString() {
        return buildConnectionString(getInternalHost(), getInternalPort());
    }

    private String buildConnectionString(String host, int port) {
        StringBuilder uri = new StringBuilder("mongodb://");

        if (username != null && password != null) {
            uri.append(username).append(":").append(password).append("@");
        }

        uri.append(host).append(":").append(port);
        uri.append("/").append(database);

        List<String> options = new ArrayList<>();
        if (username != null) {
            options.add("authSource=admin");
        }
        if (replicaSetName != null) {
            options.add("replicaSet=" + replicaSetName);
        }

        if (!options.isEmpty()) {
            uri.append("?").append(String.join("&", options));
        }

        return uri.toString();
    }

    /**
     * Create a MongoDB client connected to this instance.
     * Uses external connection string (for test code).
     */
//    public MongoClient createClient() {
//        return MongoClients.create(getConnectionString());
//    }

    // =============================================================
    // Property publishing
    // =============================================================

    @Override
    public void publishProperties(PropertyContext ctx) {
        String prefix = getName();

        // Internal (for pods in cluster)
        ctx.publish(prefix + ".internal.host", this::getInternalHost);
        ctx.publish(prefix + ".internal.port", () -> String.valueOf(getInternalPort()));
        ctx.publish(prefix + ".internal.uri", this::getInternalConnectionString);

        // External (for test code)
        ctx.publish(prefix + ".external.host", this::getExternalHost);
        ctx.publish(prefix + ".external.port", () -> String.valueOf(getExternalPort()));
        ctx.publish(prefix + ".external.uri", this::getConnectionString);

        // Convenience aliases
        ctx.publish(prefix + ".uri", this::getConnectionString);
        ctx.publish(prefix + ".database", () -> database);
    }

    // =============================================================
    // Container building
    // =============================================================

    @Override
    protected Container buildMainContainer() {
        ContainerBuilder builder = new ContainerBuilder()
            .withName("mongodb")
            .withImage(image)
            .withPorts(new ContainerPortBuilder()
                .withName("mongodb")
                .withContainerPort(DEFAULT_PORT)
                .build())
            .withNewReadinessProbe()
                .withNewExec()
                    .withCommand("mongosh", "--eval", "db.adminCommand('ping')")
                .endExec()
                .withInitialDelaySeconds(5)
                .withPeriodSeconds(10)
                .withTimeoutSeconds(5)
            .endReadinessProbe()
            .withNewLivenessProbe()
                .withNewExec()
                    .withCommand("mongosh", "--eval", "db.adminCommand('ping')")
                .endExec()
                .withInitialDelaySeconds(30)
                .withPeriodSeconds(20)
                .withTimeoutSeconds(5)
            .endLivenessProbe();

        // Add authentication environment variables
        if (username != null && password != null) {
            builder.addToEnv(new EnvVarBuilder()
                    .withName("MONGO_INITDB_ROOT_USERNAME")
                    .withValue(username)
                    .build())
                .addToEnv(new EnvVarBuilder()
                    .withName("MONGO_INITDB_ROOT_PASSWORD")
                    .withValue(password)
                    .build());
        }

        // Add database environment variable
        builder.addToEnv(new EnvVarBuilder()
            .withName("MONGO_INITDB_DATABASE")
            .withValue(database)
            .build());

        // Add replica set configuration
        if (replicaSetName != null) {
            builder.withArgs("--replSet", replicaSetName);
        }

        return builder.build();
    }
}
