package com.rcaagent.domain.validation;

/**
 * Domain validation utilities.
 * Single Responsibility: encapsulates all domain invariant checks.
 * Open/Closed: new validations are added here without touching existing Records.
 */
public final class DomainGuard {

    private DomainGuard() {}

    public static void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException(field + " must not be blank");
    }

    public static void requirePositive(long value, String field) {
        if (value <= 0)
            throw new IllegalArgumentException(field + " must be positive, got: " + value);
    }

    public static void requireBetween(double value, double min, double max, String field) {
        if (value < min || value > max)
            throw new IllegalArgumentException(
                field + " must be between " + min + " and " + max + ", got: " + value
            );
    }
}
