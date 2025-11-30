package com.smartletter.support.firestore;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.NoCredentials;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared base class for Firestore integration tests backed by the official emulator.
 */
public abstract class FirestoreEmulatorTestBase {

    private static final Logger log = LoggerFactory.getLogger(FirestoreEmulatorTestBase.class);
    private static final int EMULATOR_PORT = 8080;
    private static final String PROJECT_ID = "smart-letter-test";
    private static final DockerImageName FIRESTORE_IMAGE =
            DockerImageName.parse("gcr.io/google.com/cloudsdktool/cloud-sdk:emulators");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

        private static final boolean DOCKER_AVAILABLE = isDockerAvailable();

        @SuppressWarnings("resource")
        private static GenericContainer<?> firestoreEmulator;

    private static Firestore firestore;

    @BeforeAll
    static void connectToEmulator() {
        Assumptions.assumeTrue(DOCKER_AVAILABLE, "Docker is required for Firestore emulator tests");
        ensureEmulatorRunning();
        String hostAndPort = emulatorHostAndPort();
        firestore = FirestoreOptions.newBuilder()
                .setProjectId(PROJECT_ID)
                .setEmulatorHost(hostAndPort)
                .setCredentials(NoCredentials.getInstance())
                .build()
                .getService();

        System.setProperty("FIRESTORE_EMULATOR_HOST", hostAndPort);
        System.setProperty("SPRING_CLOUD_GCP_FIRESTORE_EMULATOR_HOST", hostAndPort);
        System.setProperty("SPRING_CLOUD_GCP_PROJECT_ID", PROJECT_ID);
        System.setProperty("SPRING_CLOUD_GCP_FIRESTORE_PROJECT_ID", PROJECT_ID);
        log.info("Firestore emulator ready at {}", hostAndPort);
    }

    protected Firestore firestore() {
        return firestore;
    }

    protected void seedFixture(String resourcePath) {
        Map<String, Object> fixture = readFixture(resourcePath);
        Object path = fixture.get("path");
        Object data = fixture.get("data");
        if (!(path instanceof String documentPath)) {
            throw new IllegalArgumentException("Fixture missing 'path': " + resourcePath);
        }
        if (!(data instanceof Map<?, ?> mapData)) {
            throw new IllegalArgumentException("Fixture missing 'data': " + resourcePath);
        }
        writeDocument(documentPath, cast(mapData));
    }

    @AfterEach
    void cleanEmulator() {
        if (firestore == null) {
            return;
        }
        try {
            for (CollectionReference collection : firestore.listCollections()) {
                deleteCollection(collection);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while cleaning Firestore emulator", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed cleaning Firestore emulator", e.getCause());
        }
    }

    @AfterAll
    static void shutdownEmulator() {
        if (firestore != null) {
            try {
                firestore.close();
            } catch (Exception e) {
                log.warn("Failed closing Firestore client", e);
            }
            firestore = null;
        }
        if (firestoreEmulator != null && firestoreEmulator.isRunning()) {
            firestoreEmulator.stop();
        }
    }

    @SuppressWarnings("resource")
    private static void ensureEmulatorRunning() {
        if (firestoreEmulator == null) {
            firestoreEmulator = new GenericContainer<>(FIRESTORE_IMAGE)
                    .withExposedPorts(EMULATOR_PORT)
                    .withCommand(
                            "/bin/sh",
                            "-c",
                            "gcloud config set auth/disable_credentials true >/dev/null 2>&1 && "
                                    + "gcloud config set project "
                                    + PROJECT_ID
                                    + " >/dev/null 2>&1 && "
                                    + "gcloud beta emulators firestore start --host-port=0.0.0.0:"
                                    + EMULATOR_PORT
                                    + " --quiet")
                    .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(45)));
        }
        if (!firestoreEmulator.isRunning()) {
            firestoreEmulator.start();
        }
    }

    private static String emulatorHostAndPort() {
        return firestoreEmulator.getHost() + ":" + firestoreEmulator.getMappedPort(EMULATOR_PORT);
    }

    private Map<String, Object> readFixture(String resourcePath) {
        String normalizedPath = resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath;
        try (InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(normalizedPath)) {
            if (inputStream == null) {
                throw new IllegalArgumentException("Fixture not found: " + resourcePath);
            }
            return OBJECT_MAPPER.readValue(inputStream, new TypeReference<Map<String, Object>>() {});
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read fixture " + resourcePath, e);
        }
    }

    private void writeDocument(String documentPath, Map<String, Object> payload) {
        try {
            firestore.document(documentPath).set(payload).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while writing fixture to Firestore", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Unable to write fixture to Firestore", e.getCause());
        }
    }

    private void deleteCollection(CollectionReference collection)
            throws ExecutionException, InterruptedException {
        for (DocumentReference document : collection.listDocuments()) {
            for (CollectionReference nested : document.listCollections()) {
                deleteCollection(nested);
            }
            document.delete().get();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> cast(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }

    private static boolean isDockerAvailable() {
        try {
            DockerClientFactory.instance().client();
            return true;
        } catch (Throwable ex) {
            log.warn("Docker not available for Firestore emulator tests: {}", ex.getMessage());
            return false;
        }
    }
}
