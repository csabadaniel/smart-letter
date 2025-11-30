package com.smartletter.settings.firestore;

import java.util.Objects;

/**
 * Command object detailing how the delivery configuration should be updated.
 */
public record DeliveryConfigurationWriteModel(
        String recipientEmail,
        String llmPrompt,
        String updatedBy,
        Long expectedVersion) {

    public DeliveryConfigurationWriteModel {
        Objects.requireNonNull(recipientEmail, "recipientEmail must not be null");
        Objects.requireNonNull(llmPrompt, "llmPrompt must not be null");
        Objects.requireNonNull(updatedBy, "updatedBy must not be null");
    }
}
