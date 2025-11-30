package com.smartletter.settings.firestore;

/**
 * Raised when an upsert attempts to write with a stale configuration version.
 */
public class ConfigVersionConflictException extends RuntimeException {

    private final long expected;
    private final long actual;

    public ConfigVersionConflictException(long actual, long expected) {
        super("Delivery configuration version mismatch. Expected %d but found %d.".formatted(expected, actual));
        this.actual = actual;
        this.expected = expected;
    }

    public long getActual() {
        return actual;
    }

    public long getExpected() {
        return expected;
    }
}
