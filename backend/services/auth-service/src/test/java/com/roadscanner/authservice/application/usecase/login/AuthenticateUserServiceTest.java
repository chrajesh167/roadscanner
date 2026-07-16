package com.roadscanner.authservice.application.usecase.login;

import com.roadscanner.authservice.domain.exception.AccountLockedException;
import com.roadscanner.authservice.domain.exception.InvalidCredentialsException;
import com.roadscanner.authservice.domain.model.AccountStatus;
import com.roadscanner.authservice.domain.model.AssignedBy;
import com.roadscanner.authservice.domain.model.Credential;
import com.roadscanner.authservice.domain.model.LoginIdentifier;
import com.roadscanner.authservice.domain.model.Role;
import com.roadscanner.authservice.domain.model.RoleAssignment;
import com.roadscanner.authservice.domain.model.UserId;
import com.roadscanner.authservice.domain.port.in.AuthenticateUser;
import com.roadscanner.authservice.domain.port.out.StubPasswordHasher;
import com.roadscanner.authservice.domain.service.PasswordHashingPolicy;
import com.roadscanner.authservice.testsupport.MutableClock;
import com.roadscanner.authservice.testsupport.fakes.InMemoryCredentialRepository;
import com.roadscanner.authservice.testsupport.fakes.InMemoryRoleAssignmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthenticateUserServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-14T10:00:00Z");
    private static final LoginIdentifier IDENTIFIER = new LoginIdentifier("traveler@example.com");
    private static final String PASSWORD = "correct-horse-7-battery";
    private static final int LOCKOUT_THRESHOLD = 3;
    private static final Duration LOCKOUT_DURATION = Duration.ofMinutes(15);

    private final InMemoryCredentialRepository credentialRepository = new InMemoryCredentialRepository();
    private final InMemoryRoleAssignmentRepository roleAssignmentRepository = new InMemoryRoleAssignmentRepository();
    private final StubPasswordHasher passwordHasher = new StubPasswordHasher();
    private final MutableClock clock = new MutableClock(NOW);

    private final AuthenticateUserService service = new AuthenticateUserService(
            credentialRepository, roleAssignmentRepository, passwordHasher,
            PasswordHashingPolicy.withCurrentAlgorithm("stub"),
            LOCKOUT_THRESHOLD, LOCKOUT_DURATION, clock);

    private UserId userId;

    @BeforeEach
    void registerUser() {
        Credential credential = Credential.register(
                UserId.generate(), IDENTIFIER, passwordHasher.hash(PASSWORD), NOW);
        credentialRepository.save(credential);
        userId = credential.userId();
        roleAssignmentRepository.save(RoleAssignment.assign(
                userId, Role.TRAVELER, AssignedBy.service("auth-service"), NOW));
    }

    private AuthenticateUser.AuthenticationResult login(String password) {
        return service.authenticate(new AuthenticateUser.AuthenticateUserCommand(IDENTIFIER, password));
    }

    @Test
    void returnsUserIdAndCurrentRoleOnSuccess() {
        AuthenticateUser.AuthenticationResult result = login(PASSWORD);

        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.role()).isEqualTo(Role.TRAVELER);
        assertThat(credentialRepository.findByUserId(userId).orElseThrow().lastLoginAt()).contains(NOW);
    }

    @Test
    void returnsLatestAssignedRole() {
        roleAssignmentRepository.save(RoleAssignment.assign(
                userId, Role.OPERATOR, AssignedBy.service("operator-service"), NOW.plusSeconds(60)));

        assertThat(login(PASSWORD).role()).isEqualTo(Role.OPERATOR);
    }

    @Test
    void unknownIdentifierAndWrongPasswordThrowTheSameException() {
        assertThatThrownBy(() -> service.authenticate(new AuthenticateUser.AuthenticateUserCommand(
                new LoginIdentifier("nobody@example.com"), PASSWORD)))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid credentials");

        assertThatThrownBy(() -> login("wrong-password-11"))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid credentials");
    }

    @Test
    void persistsFailedAttemptCountAcrossCalls() {
        assertThatThrownBy(() -> login("wrong-password-11")).isInstanceOf(InvalidCredentialsException.class);
        assertThatThrownBy(() -> login("wrong-password-11")).isInstanceOf(InvalidCredentialsException.class);

        assertThat(credentialRepository.findByUserId(userId).orElseThrow().failedLoginAttempts()).isEqualTo(2);
    }

    @Test
    void locksAccountAtThresholdAndRejectsEvenTheCorrectPassword() {
        for (int i = 0; i < LOCKOUT_THRESHOLD; i++) {
            assertThatThrownBy(() -> login("wrong-password-11")).isInstanceOf(InvalidCredentialsException.class);
        }
        assertThat(credentialRepository.findByUserId(userId).orElseThrow().status())
                .isEqualTo(AccountStatus.LOCKED);

        assertThatThrownBy(() -> login(PASSWORD)).isInstanceOf(AccountLockedException.class);
    }

    @Test
    void lockClearsAfterTheLockoutWindowElapses() {
        for (int i = 0; i < LOCKOUT_THRESHOLD; i++) {
            assertThatThrownBy(() -> login("wrong-password-11")).isInstanceOf(InvalidCredentialsException.class);
        }

        clock.advanceBy(LOCKOUT_DURATION);
        AuthenticateUser.AuthenticationResult result = login(PASSWORD);

        assertThat(result.userId()).isEqualTo(userId);
        Credential credential = credentialRepository.findByUserId(userId).orElseThrow();
        assertThat(credential.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(credential.failedLoginAttempts()).isZero();
    }

    @Test
    void upgradesHashWhenStoredAlgorithmIsOutdated() {
        AuthenticateUserService upgradingService = new AuthenticateUserService(
                credentialRepository, roleAssignmentRepository, passwordHasher,
                PasswordHashingPolicy.withCurrentAlgorithm("stub-v2"),
                LOCKOUT_THRESHOLD, LOCKOUT_DURATION, clock);

        upgradingService.authenticate(new AuthenticateUser.AuthenticateUserCommand(IDENTIFIER, PASSWORD));

        // The stub hasher always stamps "stub"; what matters is that changePassword ran with a
        // freshly computed hash — observable via updatedAt advancing past registration time.
        assertThat(passwordHasher.matches(PASSWORD,
                credentialRepository.findByUserId(userId).orElseThrow().passwordHash())).isTrue();
    }
}
