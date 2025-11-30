package com.smartletter.settings.firestore;

/**
 * Wraps low-level Firestore interaction failures so higher layers can react uniformly.
 */
public class DeliveryConfigurationRepositoryException extends RuntimeException {

    public DeliveryConfigurationRepositoryException(String message, Throwable cause) {
        super(message, cause);
    }
}
