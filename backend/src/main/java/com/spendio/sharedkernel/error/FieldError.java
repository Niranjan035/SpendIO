package com.spendio.sharedkernel.error;

/**
 * A single field-level problem surfaced as {@code fieldErrors[]} in the ProblemDetail body.
 *
 * <p>Shape matches the frontend contract: {@code fieldErrors[].field} / {@code fieldErrors[].message}.
 */
public record FieldError(String field, String message) {

    public static FieldError of(String field, String message) {
        return new FieldError(field, message);
    }
}
