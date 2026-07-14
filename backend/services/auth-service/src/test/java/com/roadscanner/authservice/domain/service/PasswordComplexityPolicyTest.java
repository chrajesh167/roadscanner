package com.roadscanner.authservice.domain.service;

import com.roadscanner.authservice.domain.exception.PasswordPolicyViolationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordComplexityPolicyTest {

    private final PasswordComplexityPolicy policy = PasswordComplexityPolicy.standard();

    @Test
    void acceptsAPasswordMeetingTheBaseline() {
        assertThatCode(() -> policy.validate("correct-horse-battery-1"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsTooShortPassword() {
        assertThatThrownBy(() -> policy.validate("short1"))
                .isInstanceOf(PasswordPolicyViolationException.class)
                .hasMessageContaining("at least 12 characters");
    }

    @Test
    void rejectsPasswordWithNoDigit() {
        assertThatThrownBy(() -> policy.validate("no-digits-here"))
                .isInstanceOf(PasswordPolicyViolationException.class)
                .hasMessageContaining("letter and one digit");
    }

    @Test
    void rejectsPasswordWithNoLetter() {
        assertThatThrownBy(() -> policy.validate("123456789012"))
                .isInstanceOf(PasswordPolicyViolationException.class)
                .hasMessageContaining("letter and one digit");
    }

    @Test
    void rejectsBlankPassword() {
        assertThatThrownBy(() -> policy.validate("   "))
                .isInstanceOf(PasswordPolicyViolationException.class);
    }

    @Test
    void customMinLengthIsRespected() {
        PasswordComplexityPolicy lenient = PasswordComplexityPolicy.of(6);
        assertThatCode(() -> lenient.validate("abc123")).doesNotThrowAnyException();
    }

    @Test
    void rejectsNonPositiveMinLength() {
        assertThatThrownBy(() -> PasswordComplexityPolicy.of(0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
