package com.smartletter.settings.firestore;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable representation of the single delivery configuration stored in Firestore.
 */
public record DeliveryConfigurationDocument(
        String recipientEmail,
        String llmPrompt,
        String promptSha256,
        long version,
        String updatedBy,
        Instant updatedAt) {

    public DeliveryConfigurationDocument {
        Objects.requireNonNull(recipientEmail, "recipientEmail must not be null");
        Objects.requireNonNull(llmPrompt, "llmPrompt must not be null");
        Objects.requireNonNull(promptSha256, "promptSha256 must not be null");
        Objects.requireNonNull(updatedBy, "updatedBy must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }
}
