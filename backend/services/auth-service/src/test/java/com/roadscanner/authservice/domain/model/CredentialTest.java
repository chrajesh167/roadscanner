package com.roadscanner.authservice.domain.model;

import com.roadscanner.authservice.domain.exception.AccountLockedException;
import com.roadscanner.authservice.domain.exception.InvalidCredentialsException;
import com.roadscanner.authservice.domain.port.out.StubPasswordHasher;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CredentialTest {

    private static final int LOCKOUT_THRESHOLD = 3;
    private static final StubPasswordHasher HASHER = new StubPasswordHasher();
    private static final Instant NOW = Instant.parse("2026-07-14T10:00:00Z");

    private Credential activeCredential(String rawPassword) {
        UserId userId = new UserId(UUID.randomUUID());
        LoginIdentifier identifier = new LoginIdentifier("traveler@example.com");
        PasswordHash hash = HASHER.hash(rawPassword);
        return Credential.register(userId, identifier, hash, NOW);
    }

    @Test
    void registerStartsActiveWithZeroFailedAttempts() {
        Credential credential = activeCredential("correct-horse-battery");
        assertThat(credential.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(credential.failedLoginAttempts()).isZero();
        assertThat(credential.lastLoginAt()).isEmpty();
    }

    @Test
    void authenticateSucceedsWithCorrectPassword() {
        Credential credential = activeCredential("correct-horse-battery");
        Instant loginTime = NOW.plusSeconds(60);

        credential.authenticate("correct-horse-battery", HASHER, loginTime, LOCKOUT_THRESHOLD);

        assertThat(credential.lastLoginAt()).contains(loginTime);
        assertThat(credential.failedLoginAttempts()).isZero();
        assertThat(credential.status()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void authenticateFailsWithWrongPasswordAndIncrementsAttempts() {
        Credential credential = activeCredential("correct-horse-battery");

        assertThatThrownBy(() -> credential.authenticate("wrong-password", HASHER, NOW, LOCKOUT_THRESHOLD))
                .isInstanceOf(InvalidCredentialsException.class);

        assertThat(credential.failedLoginAttempts()).isEqualTo(1);
        assertThat(credential.status()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void invalidCredentialsExceptionCarriesNoIdentifyingContext() {
        // Deliberately asserts the exception has no fields to inspect — see
        // InvalidCredentialsException's Javadoc. A future change adding a field here would be
        // a regression of the enumeration-protection guarantee.
        Credential credential = activeCredential("correct-horse-battery");

        assertThatThrownBy(() -> credential.authenticate("wrong-password", HASHER, NOW, LOCKOUT_THRESHOLD))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid credentials");
    }

    @Test
    void reachingLockoutThresholdLocksTheAccount() {
        Credential credential = activeCredential("correct-horse-battery");

        for (int attempt = 1; attempt < LOCKOUT_THRESHOLD; attempt++) {
            assertThatThrownBy(() -> credential.authenticate("wrong-password", HASHER, NOW, LOCKOUT_THRESHOLD))
                    .isInstanceOf(InvalidCredentialsException.class);
            assertThat(credential.status()).isEqualTo(AccountStatus.ACTIVE);
        }

        // The attempt that reaches the threshold.
        assertThatThrownBy(() -> credential.authenticate("wrong-password", HASHER, NOW, LOCKOUT_THRESHOLD))
                .isInstanceOf(InvalidCredentialsException.class);

        assertThat(credential.status()).isEqualTo(AccountStatus.LOCKED);
        assertThat(credential.failedLoginAttempts()).isEqualTo(LOCKOUT_THRESHOLD);
    }

    @Test
    void lockedAccountRejectsLoginBeforeCheckingPassword() {
        Credential credential = activeCredential("correct-horse-battery");
        credential.suspend(NOW); // any non-ACTIVE status demonstrates the short-circuit
        int attemptsBefore = credential.failedLoginAttempts();

        assertThatThrownBy(() -> credential.authenticate("correct-horse-battery", HASHER, NOW, LOCKOUT_THRESHOLD))
                .isInstanceOf(AccountLockedException.class);

        // Failed-attempt counter must be untouched — the password was never actually checked.
        assertThat(credential.failedLoginAttempts()).isEqualTo(attemptsBefore);
    }

    @Test
    void suspendedAccountRejectsLoginEvenWithCorrectPassword() {
        Credential credential = activeCredential("correct-horse-battery");
        credential.suspend(NOW);

        assertThatThrownBy(() -> credential.authenticate("correct-horse-battery", HASHER, NOW, LOCKOUT_THRESHOLD))
                .isInstanceOf(AccountLockedException.class);
    }

    @Test
    void unlockClearsALockedAccountAndResetsAttempts() {
        Credential credential = activeCredential("correct-horse-battery");
        lockAccount(credential);

        credential.unlock(NOW);

        assertThat(credential.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(credential.failedLoginAttempts()).isZero();
    }

    @Test
    void unlockDoesNotAffectASuspendedAccount() {
        Credential credential = activeCredential("correct-horse-battery");
        credential.suspend(NOW);

        credential.unlock(NOW);

        assertThat(credential.status()).isEqualTo(AccountStatus.SUSPENDED);
    }

    @Test
    void unlockOnAlreadyActiveAccountIsANoOp() {
        Credential credential = activeCredential("correct-horse-battery");

        credential.unlock(NOW);

        assertThat(credential.status()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void suspendAppliesRegardlessOfCurrentStatus() {
        Credential credential = activeCredential("correct-horse-battery");
        lockAccount(credential);

        credential.suspend(NOW);

        assertThat(credential.status()).isEqualTo(AccountStatus.SUSPENDED);
    }

    @Test
    void reinstateClearsASuspendedAccountAndResetsAttempts() {
        Credential credential = activeCredential("correct-horse-battery");
        credential.suspend(NOW);

        credential.reinstate(NOW);

        assertThat(credential.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(credential.failedLoginAttempts()).isZero();
    }

    @Test
    void reinstateDoesNotAffectALockedAccount() {
        Credential credential = activeCredential("correct-horse-battery");
        lockAccount(credential);

        credential.reinstate(NOW);

        assertThat(credential.status()).isEqualTo(AccountStatus.LOCKED);
    }

    @Test
    void changePasswordReplacesTheHash() {
        Credential credential = activeCredential("correct-horse-battery");
        PasswordHash newHash = HASHER.hash("new-correct-horse");

        credential.changePassword(newHash, NOW);

        assertThat(credential.passwordHash()).isEqualTo(newHash);
        credential.authenticate("new-correct-horse", HASHER, NOW, LOCKOUT_THRESHOLD);
    }

    @Test
    void equalityIsByUserIdOnly() {
        UserId userId = new UserId(UUID.randomUUID());
        Credential first = Credential.register(userId, new LoginIdentifier("a@example.com"), HASHER.hash("pw"), NOW);
        Credential second = Credential.reconstitute(userId, new LoginIdentifier("b@example.com"),
                HASHER.hash("different"), AccountStatus.LOCKED, 5, NOW, NOW, null);

        assertThat(first).isEqualTo(second);
    }

    private void lockAccount(Credential credential) {
        for (int i = 0; i < LOCKOUT_THRESHOLD; i++) {
            try {
                credential.authenticate("wrong-password", HASHER, NOW, LOCKOUT_THRESHOLD);
            } catch (InvalidCredentialsException expected) {
                // expected on every iteration
            }
        }
    }
}
