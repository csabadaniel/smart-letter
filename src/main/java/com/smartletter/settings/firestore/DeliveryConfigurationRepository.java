package com.smartletter.settings.firestore;

import com.google.api.core.ApiFuture;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.SetOptions;
import com.google.cloud.firestore.Transaction;
import com.smartletter.settings.config.DeliveryConfigurationProperties;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Repository;
import org.springframework.util.Assert;

/**
 * Firestore repository responsible for reading and writing the singleton delivery configuration document.
 */
@Repository
public class DeliveryConfigurationRepository {

    private static final String FIELD_RECIPIENT = "recipientEmail";
    private static final String FIELD_PROMPT = "llmPrompt";
    private static final String FIELD_PROMPT_HASH = "promptSha256";
    private static final String FIELD_VERSION = "version";
    private static final String FIELD_UPDATED_BY = "updatedBy";
    private static final String FIELD_UPDATED_AT = "updatedAt";

    private final Firestore firestore;
    private final String documentPath;

    public DeliveryConfigurationRepository(Firestore firestore, DeliveryConfigurationProperties properties) {
        this.firestore = Objects.requireNonNull(firestore, "firestore must not be null");
        Assert.hasText(properties.getCollectionPath(), "delivery-config.collection-path must be configured");
        this.documentPath = properties.getCollectionPath();
    }

    public Optional<DeliveryConfigurationDocument> findConfiguration() {
        return fetchDocument();
    }

    public DeliveryConfigurationDocument upsert(DeliveryConfigurationWriteModel writeModel) {
        Objects.requireNonNull(writeModel, "writeModel must not be null");
        runTransaction(writeModel);
        return fetchDocument()
                .orElseThrow(() -> new DeliveryConfigurationRepositoryException(
                        "Delivery configuration missing after upsert",
                        null));
    }

    private void runTransaction(DeliveryConfigurationWriteModel writeModel) {
        ApiFuture<Void> future = firestore.runTransaction((Transaction transaction) -> {
            DocumentReference documentReference = documentReference();
            DocumentSnapshot snapshot = transaction.get(documentReference).get();
            long currentVersion = snapshot.exists() ? valueOrZero(snapshot.getLong(FIELD_VERSION)) : 0L;
            Long expectedVersion = writeModel.expectedVersion();
            if (expectedVersion != null && expectedVersion.longValue() != currentVersion) {
                throw new ConfigVersionConflictException(currentVersion, expectedVersion);
            }

            Map<String, Object> payload = new HashMap<>();
            payload.put(FIELD_RECIPIENT, writeModel.recipientEmail());
            payload.put(FIELD_PROMPT, writeModel.llmPrompt());
            payload.put(FIELD_PROMPT_HASH, DigestUtils.sha256Hex(writeModel.llmPrompt()));
            payload.put(FIELD_UPDATED_BY, writeModel.updatedBy());
            payload.put(FIELD_VERSION, FieldValue.increment(1));
            payload.put(FIELD_UPDATED_AT, FieldValue.serverTimestamp());

            transaction.set(documentReference, payload, SetOptions.merge());
            return null;
        });

        try {
            future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DeliveryConfigurationRepositoryException(
                    "Interrupted while upserting delivery configuration", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof ConfigVersionConflictException conflict) {
                throw conflict;
            }
            throw new DeliveryConfigurationRepositoryException(
                    "Failed to upsert delivery configuration", cause);
        }
    }

    private Optional<DeliveryConfigurationDocument> fetchDocument() {
        try {
            DocumentSnapshot snapshot = documentReference().get().get();
            return mapSnapshot(snapshot);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DeliveryConfigurationRepositoryException(
                    "Interrupted while fetching delivery configuration", e);
        } catch (ExecutionException e) {
            throw new DeliveryConfigurationRepositoryException(
                    "Failed to fetch delivery configuration", e.getCause());
        }
    }

    private Optional<DeliveryConfigurationDocument> mapSnapshot(DocumentSnapshot snapshot) {
        if (snapshot == null || !snapshot.exists()) {
            return Optional.empty();
        }

        String recipientEmail = snapshot.getString(FIELD_RECIPIENT);
        String prompt = snapshot.getString(FIELD_PROMPT);
        String promptHash = snapshot.getString(FIELD_PROMPT_HASH);
        Long version = snapshot.getLong(FIELD_VERSION);
        String updatedBy = snapshot.getString(FIELD_UPDATED_BY);
        Instant updatedAt = resolveTimestamp(snapshot);

        if (recipientEmail == null || prompt == null || promptHash == null || version == null || updatedBy == null
                || updatedAt == null) {
            return Optional.empty();
        }

        return Optional.of(new DeliveryConfigurationDocument(
                recipientEmail,
                prompt,
                promptHash,
                version,
                updatedBy,
                updatedAt));
    }

    private Instant resolveTimestamp(DocumentSnapshot snapshot) {
        Timestamp stored = snapshot.getTimestamp(FIELD_UPDATED_AT);
        if (stored != null) {
            return toInstant(stored);
        }
        Timestamp updateTime = snapshot.getUpdateTime();
        return updateTime != null ? toInstant(updateTime) : null;
    }

    private Instant toInstant(Timestamp timestamp) {
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }

    private long valueOrZero(Long value) {
        return value != null ? value : 0L;
    }

    private DocumentReference documentReference() {
        return firestore.document(documentPath);
    }
}
