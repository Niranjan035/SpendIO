package com.spendio.accounts.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import com.spendio.sharedkernel.domain.enums.AccountType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Bean-validation contract for account creation (ARCHITECTURE.md D.6). The optional
 * string fields are nullable on the wire; the anchored {@code @Pattern} rules reject an
 * empty string, which is exactly what a frontend that sends "" for untouched inputs
 * would trip over. Keeps that contract pinned without needing a database.
 */
class CreateAccountRequestTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    /** The values from the reported creation, with nullable optionals passed as null. */
    private static CreateAccountRequest reported(String last4, String color) {
        return new CreateAccountRequest(
                "Check-in", AccountType.CASH, "INR", 450_000, LocalDate.of(2026, 10, 6),
                null, last4, null, null, null, null, color, "checkin daily", true, null, false);
    }

    @Test
    void acceptsTheReportedAccountWithNullOptionalCodes() {
        assertThat(validator.validate(reported(null, "#12AB34"))).isEmpty();
    }

    @Test
    void rejectsAnEmptyLast4BecauseThePatternIsAnchored() {
        Set<ConstraintViolation<CreateAccountRequest>> violations =
                validator.validate(reported("", "#12AB34"));

        assertThat(violations)
                .anySatisfy(violation -> {
                    assertThat(violation.getPropertyPath().toString()).isEqualTo("last4");
                    assertThat(violation.getMessage()).isEqualTo("last4 must be exactly 4 digits");
                });
    }

    @Test
    void rejectsAnEmptyColor() {
        Set<ConstraintViolation<CreateAccountRequest>> violations =
                validator.validate(reported(null, ""));

        assertThat(violations)
                .anySatisfy(violation -> assertThat(violation.getPropertyPath().toString())
                        .isEqualTo("color"));
    }

    @Test
    void rejectsANonZeroOpeningBalanceWithoutADate() {
        CreateAccountRequest request = new CreateAccountRequest(
                "Check-in", AccountType.CASH, "INR", 450_000, null,
                null, null, null, null, null, null, "#12AB34", "checkin daily", true, null, false);

        assertThat(validator.validate(request))
                .anySatisfy(violation -> assertThat(violation.getMessage())
                        .isEqualTo("openingBalanceDate is required when openingBalanceMinor is non-zero"));
    }
}
