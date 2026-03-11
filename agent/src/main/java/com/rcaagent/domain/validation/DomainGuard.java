package com.rcaagent.domain.validation;

public final class DomainGuard {

    private DomainGuard() {}

    public static void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException(field + " must not be blank");
    }

    public static void requireNonNull(Object value, String field) {
        if (value == null)
            throw new IllegalArgumentException(field + " must not be null");
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

    public static void requireMin(double value, double min, String field) {
        if (value < min)
            throw new IllegalArgumentException(
                field + " must be >= " + min + ", got: " + value
            );
    }
}
