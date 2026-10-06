package com.spendio.sharedkernel.error;

import org.springframework.http.HttpStatus;

/**
 * Stable machine codes returned in the ProblemDetail {@code code} field.
 *
 * <p>This is part of the public API contract: values are never renamed or reused, only added.
 * The frontend switches on {@code code}, never on the human-readable {@code detail}.
 */
public enum ErrorCode {

    // --- 400 -----------------------------------------------------------------
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "Bad request"),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "Malformed request"),
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Validation failed"),

    // --- 401 -----------------------------------------------------------------
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Unauthorized"),
    AUTH_TOKEN_MISSING(HttpStatus.UNAUTHORIZED, "Authentication required"),
    AUTH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "Invalid authentication token"),
    AUTH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Authentication token expired"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid email or password"),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "Invalid refresh token"),
    TOKEN_REUSE_DETECTED(HttpStatus.UNAUTHORIZED, "Refresh token reuse detected"),

    // --- 403 -----------------------------------------------------------------
    FORBIDDEN(HttpStatus.FORBIDDEN, "Forbidden"),
    ACCOUNT_DISABLED(HttpStatus.FORBIDDEN, "Account disabled"),
    CSRF_TOKEN_INVALID(HttpStatus.FORBIDDEN, "CSRF token missing or invalid"),

    // --- 404 -----------------------------------------------------------------
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found"),
    ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "Account not found"),
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "Category not found"),
    TRANSACTION_NOT_FOUND(HttpStatus.NOT_FOUND, "Transaction not found"),
    RECURRING_NOT_FOUND(HttpStatus.NOT_FOUND, "Recurring transaction not found"),
    BUDGET_NOT_FOUND(HttpStatus.NOT_FOUND, "Budget not found"),
    GOAL_NOT_FOUND(HttpStatus.NOT_FOUND, "Goal not found"),
    IMPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "Import not found"),
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "Notification not found"),

    // --- 405 -----------------------------------------------------------------
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed"),

    // --- 406 -----------------------------------------------------------------
    NOT_ACCEPTABLE(HttpStatus.NOT_ACCEPTABLE, "Not acceptable"),

    // --- 409 -----------------------------------------------------------------
    CONFLICT(HttpStatus.CONFLICT, "Conflict"),
    DUPLICATE_RESOURCE(HttpStatus.CONFLICT, "Resource already exists"),
    CATEGORY_IN_USE(HttpStatus.CONFLICT, "Category is still in use"),
    ACCOUNT_NAME_DUPLICATE(HttpStatus.CONFLICT, "Account name already exists"),
    ACCOUNT_PRIMARY_ALREADY_EXISTS(HttpStatus.CONFLICT, "Primary account already exists"),
    ACCOUNT_HAS_TRANSACTIONS(HttpStatus.CONFLICT, "Account has transactions"),
    TRANSACTION_DUPLICATE(HttpStatus.CONFLICT, "Duplicate transaction"),
    TRANSACTION_ALREADY_REVERSED(HttpStatus.CONFLICT, "Transaction already reversed"),
    TRANSACTION_IMMUTABLE(HttpStatus.CONFLICT, "Transaction is immutable"),
    TRANSACTION_NOT_REVERSIBLE(HttpStatus.CONFLICT, "Transaction cannot be reversed"),
    RECURRING_ALREADY_PAUSED(HttpStatus.CONFLICT, "Recurring transaction is already paused"),
    BUDGET_OVERLAPPING_OVERALL(HttpStatus.CONFLICT, "Overlapping overall budget exists"),
    GOAL_ALREADY_COMPLETED(HttpStatus.CONFLICT, "Goal is already completed"),
    IMPORT_NOT_READY(HttpStatus.CONFLICT, "Import is not ready for this action"),
    IMPORT_ROW_NOT_EDITABLE(HttpStatus.CONFLICT, "Import row is no longer editable"),
    IMPORT_ALREADY_EXECUTED(HttpStatus.CONFLICT, "Import was already executed"),
    NOTIFICATION_ALREADY_READ(HttpStatus.CONFLICT, "Notification already read"),

    // --- 413 -----------------------------------------------------------------
    PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "Payload too large"),
    IMPORT_FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "Import file too large"),

    // --- 415 -----------------------------------------------------------------
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported media type"),
    IMPORT_UNSUPPORTED_FORMAT(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported file format"),

    // --- 422 -----------------------------------------------------------------
    UNPROCESSABLE_ENTITY(HttpStatus.UNPROCESSABLE_ENTITY, "Unprocessable entity"),
    ACCOUNT_ARCHIVED(HttpStatus.UNPROCESSABLE_ENTITY, "Account is archived"),
    ACCOUNT_CURRENCY_IMMUTABLE(HttpStatus.UNPROCESSABLE_ENTITY, "Account currency is immutable"),
    CURRENCY_NOT_SUPPORTED(HttpStatus.UNPROCESSABLE_ENTITY, "Currency not supported"),
    PASSWORD_POLICY_VIOLATION(HttpStatus.UNPROCESSABLE_ENTITY, "Password does not meet the policy"),
    TRANSACTION_ACCOUNT_ARCHIVED(HttpStatus.UNPROCESSABLE_ENTITY, "Transaction account is archived"),
    TRANSACTION_CATEGORY_TYPE_MISMATCH(HttpStatus.UNPROCESSABLE_ENTITY, "Category does not match the transaction type"),
    RECURRING_INVALID_SCHEDULE(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid recurrence schedule"),
    RECURRING_NOT_DUE(HttpStatus.UNPROCESSABLE_ENTITY, "Recurring transaction is not due"),
    RECURRING_FREQUENCY_FIELD_MISMATCH(HttpStatus.UNPROCESSABLE_ENTITY, "Schedule fields do not match the frequency"),
    BUDGET_PERIOD_INVALID(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid budget period"),
    BUDGET_CATEGORY_TYPE_MISMATCH(HttpStatus.UNPROCESSABLE_ENTITY, "Budget category does not match the budget type"),
    GOAL_CONTRIBUTION_EXCEEDS_TARGET(HttpStatus.UNPROCESSABLE_ENTITY, "Contribution exceeds the goal target"),
    GOAL_TARGET_DATE_IN_PAST(HttpStatus.UNPROCESSABLE_ENTITY, "Goal target date is in the past"),
    IMPORT_ROW_LIMIT_EXCEEDED(HttpStatus.UNPROCESSABLE_ENTITY, "Import row limit exceeded"),
    IMPORT_MAPPING_INVALID(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid column mapping"),

    // --- 423 -----------------------------------------------------------------
    ACCOUNT_LOCKED(HttpStatus.LOCKED, "Account locked"),

    // --- 429 -----------------------------------------------------------------
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Too many requests"),

    // --- 5xx -----------------------------------------------------------------
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error"),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Service unavailable"),
    FX_RATE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "FX rate unavailable");

    private static final String TYPE_PREFIX = "https://docs.spendio.app/errors/";

    private final HttpStatus status;
    private final String title;

    ErrorCode(HttpStatus status, String title) {
        this.status = status;
        this.title = title;
    }

    public HttpStatus status() {
        return status;
    }

    public int statusCode() {
        return status.value();
    }

    public String title() {
        return title;
    }

    /** RFC-9457 {@code type} URI - stable, documentation-resolvable. */
    public String type() {
        return TYPE_PREFIX + name();
    }
}
