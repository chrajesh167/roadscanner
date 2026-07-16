package com.roadscanner.authservice.application.usecase.registration;

import com.roadscanner.authservice.domain.exception.IdentifierAlreadyRegisteredException;
import com.roadscanner.authservice.domain.exception.PasswordPolicyViolationException;
import com.roadscanner.authservice.domain.model.AccountStatus;
import com.roadscanner.authservice.domain.model.Credential;
import com.roadscanner.authservice.domain.model.LoginIdentifier;
import com.roadscanner.authservice.domain.model.Role;
import com.roadscanner.authservice.domain.port.in.RegisterUser;
import com.roadscanner.authservice.domain.port.out.StubPasswordHasher;
import com.roadscanner.authservice.domain.service.PasswordComplexityPolicy;
import com.roadscanner.authservice.testsupport.MutableClock;
import com.roadscanner.authservice.testsupport.fakes.InMemoryCredentialRepository;
import com.roadscanner.authservice.testsupport.fakes.InMemoryRoleAssignmentRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegisterUserServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-14T10:00:00Z");
    private static final LoginIdentifier IDENTIFIER = new LoginIdentifier("traveler@example.com");
    private static final String STRONG_PASSWORD = "correct-horse-7-battery";

    private final InMemoryCredentialRepository credentialRepository = new InMemoryCredentialRepository();
    private final InMemoryRoleAssignmentRepository roleAssignmentRepository = new InMemoryRoleAssignmentRepository();
    private final StubPasswordHasher passwordHasher = new StubPasswordHasher();

    private final RegisterUserService service = new RegisterUserService(
            credentialRepository, roleAssignmentRepository, passwordHasher,
            PasswordComplexityPolicy.standard(), new MutableClock(NOW));

    @Test
    void registersActiveCredentialWithHashedPasswordAndDefaultTravelerRole() {
        RegisterUser.RegistrationResult result = service.register(
                new RegisterUser.RegisterUserCommand(IDENTIFIER, STRONG_PASSWORD));

        Credential saved = credentialRepository.findByUserId(result.userId()).orElseThrow();
        assertThat(saved.loginIdentifier()).isEqualTo(IDENTIFIER);
        assertThat(saved.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(saved.passwordHash().value()).isNotEqualTo(STRONG_PASSWORD);
        assertThat(passwordHasher.matches(STRONG_PASSWORD, saved.passwordHash())).isTrue();

        assertThat(roleAssignmentRepository.findLatestByUserId(result.userId()).orElseThrow().role())
                .isEqualTo(Role.TRAVELER);
    }

    @Test
    void rejectsTakenIdentifier() {
        service.register(new RegisterUser.RegisterUserCommand(IDENTIFIER, STRONG_PASSWORD));

        assertThatThrownBy(() -> service.register(
                new RegisterUser.RegisterUserCommand(IDENTIFIER, "another-pass-42x")))
                .isInstanceOf(IdentifierAlreadyRegisteredException.class);
        assertThat(roleAssignmentRepository.all()).hasSize(1);
    }

    @Test
    void rejectsWeakPasswordBeforeTouchingAnyStore() {
        assertThatThrownBy(() -> service.register(
                new RegisterUser.RegisterUserCommand(IDENTIFIER, "short1")))
                .isInstanceOf(PasswordPolicyViolationException.class);

        assertThat(credentialRepository.existsByLoginIdentifier(IDENTIFIER)).isFalse();
        assertThat(roleAssignmentRepository.all()).isEmpty();
    }
}
