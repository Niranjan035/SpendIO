package com.spendio.users.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.spendio.support.AbstractIntegrationTest;
import com.spendio.users.domain.Theme;
import com.spendio.users.domain.User;
import com.spendio.users.domain.UserPreference;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repository contract against real MySQL (ARCHITECTURE.md Phase 1): the soft-delete
 * filters, the unique email index and the 1:1 preference row behave as written.
 */
@EnabledIf(value = "com.spendio.support.MySqlTestSupport#available",
        disabledReason = "no test database: set SPENDIO_TEST_DB_URL, start Docker, or run MySQL on port 3307")
@Transactional
class UserRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserPreferenceRepository userPreferenceRepository;

    @Test
    void persistsUserWithPreferenceRow() {
        User saved = userRepository.saveAndFlush(
                User.create("Ana@Example.com", "{bcrypt}", "Europe/Lisbon", "en-GB", "EUR"));
        userPreferenceRepository.flush();

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getEmail()).isEqualTo("ana@example.com");
        assertThat(saved.getDisplayName()).isEqualTo("ana");
        assertThat(saved.getDefaultCurrencyCode()).isEqualTo("EUR");
        assertThat(saved.isDeleted()).isFalse();

        UserPreference preference = UserPreference.defaultsFor(saved.getId());
        preference.setTheme(Theme.DARK);
        userPreferenceRepository.saveAndFlush(preference);

        Optional<UserPreference> loaded = userPreferenceRepository.findByUserId(saved.getId());
        assertThat(loaded).isPresent();
        assertThat(loaded.get().getTheme()).isEqualTo(Theme.DARK);
        assertThat(loaded.get().getBudgetAlertThresholdPct()).isEqualTo(80);
    }

    @Test
    void readsByEmailUntilTheAccountIsSoftDeleted() {
        User saved = userRepository.saveAndFlush(
                User.create("ana@example.com", "{bcrypt}", "UTC", "en-GB", "USD"));

        assertThat(userRepository.findByEmailAndDeletedAtIsNull("ana@example.com")).contains(saved);

        saved.softDelete();
        userRepository.saveAndFlush(saved);

        assertThat(userRepository.findByEmailAndDeletedAtIsNull("ana@example.com")).isEmpty();
        assertThat(userRepository.findByIdAndDeletedAtIsNull(saved.getId())).isEmpty();
        assertThat(userRepository.existsByEmail("ana@example.com"))
                .as("the email stays taken so the address cannot be re-registered")
                .isTrue();
    }

    @Test
    void unknownIdReadsAsEmpty() {
        assertThat(userRepository.findByIdAndDeletedAtIsNull(Long.MAX_VALUE)).isEmpty();
    }
}
