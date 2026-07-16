package com.roadscanner.authservice.config;

import com.roadscanner.authservice.adapter.out.security.BCryptPasswordHasherAdapter;
import com.roadscanner.authservice.application.usecase.login.AuthenticateUserService;
import com.roadscanner.authservice.application.usecase.passwordreset.ConfirmPasswordResetService;
import com.roadscanner.authservice.application.usecase.passwordreset.PasswordResetConfirmer;
import com.roadscanner.authservice.application.usecase.passwordreset.RequestPasswordResetService;
import com.roadscanner.authservice.application.usecase.registration.RegisterUserService;
import com.roadscanner.authservice.application.usecase.role.AssignRoleService;
import com.roadscanner.authservice.application.usecase.token.RefreshAccessTokenService;
import com.roadscanner.authservice.application.usecase.token.RevokeAllSessionsService;
import com.roadscanner.authservice.application.usecase.token.RevokeSessionService;
import com.roadscanner.authservice.application.usecase.token.SessionRevoker;
import com.roadscanner.authservice.application.usecase.token.TokenIssuer;
import com.roadscanner.authservice.application.usecase.token.TokenRefresher;
import com.roadscanner.authservice.domain.port.in.AssignRole;
import com.roadscanner.authservice.domain.port.in.AuthenticateUser;
import com.roadscanner.authservice.domain.port.in.ConfirmPasswordReset;
import com.roadscanner.authservice.domain.port.in.RefreshAccessToken;
import com.roadscanner.authservice.domain.port.in.RegisterUser;
import com.roadscanner.authservice.domain.port.in.RequestPasswordReset;
import com.roadscanner.authservice.domain.port.in.RevokeAllSessions;
import com.roadscanner.authservice.domain.port.in.RevokeSession;
import com.roadscanner.authservice.domain.port.out.CredentialRepository;
import com.roadscanner.authservice.domain.port.out.PasswordResetRepository;
import com.roadscanner.authservice.domain.port.out.RefreshTokenRepository;
import com.roadscanner.authservice.domain.port.out.RevocationCache;
import com.roadscanner.authservice.domain.port.out.RoleAssignmentRepository;
import com.roadscanner.authservice.domain.port.out.TokenGenerator;
import com.roadscanner.authservice.domain.port.out.TokenSigner;
import com.roadscanner.authservice.domain.service.PasswordComplexityPolicy;
import com.roadscanner.authservice.domain.service.PasswordHashingPolicy;
import com.roadscanner.authservice.domain.service.RefreshTokenFamilyPolicy;
import com.roadscanner.authservice.domain.service.TokenExpiryPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Explicit bean wiring for the domain policies and every application-layer use case. The
 * application classes carry no Spring stereotype annotations — they are plain constructors
 * wired here, keeping that layer "framework-light" per implementation-roadmap.md step 3 and
 * making every dependency of every use case visible in one place. ({@code @Transactional} on
 * those classes still applies: Spring proxies beans regardless of how they were registered.)
 */
@Configuration
public class UseCaseConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public PasswordComplexityPolicy passwordComplexityPolicy(AuthProperties properties) {
        return PasswordComplexityPolicy.of(properties.passwordMinLength());
    }

    @Bean
    public PasswordHashingPolicy passwordHashingPolicy(BCryptPasswordHasherAdapter passwordHasher) {
        return PasswordHashingPolicy.withCurrentAlgorithm(passwordHasher.algorithmId());
    }

    @Bean
    public TokenExpiryPolicy tokenExpiryPolicy(JwtProperties properties) {
        return TokenExpiryPolicy.of(properties.accessTokenTtl(), properties.refreshTokenTtl());
    }

    @Bean
    public RefreshTokenFamilyPolicy refreshTokenFamilyPolicy() {
        return new RefreshTokenFamilyPolicy();
    }

    @Bean
    public RegisterUser registerUser(CredentialRepository credentialRepository,
                                     RoleAssignmentRepository roleAssignmentRepository,
                                     BCryptPasswordHasherAdapter passwordHasher,
                                     PasswordComplexityPolicy passwordComplexityPolicy,
                                     Clock clock) {
        return new RegisterUserService(credentialRepository, roleAssignmentRepository,
                passwordHasher, passwordComplexityPolicy, clock);
    }

    @Bean
    public AuthenticateUser authenticateUser(CredentialRepository credentialRepository,
                                             RoleAssignmentRepository roleAssignmentRepository,
                                             BCryptPasswordHasherAdapter passwordHasher,
                                             PasswordHashingPolicy passwordHashingPolicy,
                                             AuthProperties properties,
                                             Clock clock) {
        return new AuthenticateUserService(credentialRepository, roleAssignmentRepository, passwordHasher,
                passwordHashingPolicy, properties.lockoutThreshold(), properties.lockoutDuration(), clock);
    }

    @Bean
    public TokenIssuer tokenIssuer(RefreshTokenRepository refreshTokenRepository,
                                   TokenGenerator tokenGenerator,
                                   TokenSigner tokenSigner,
                                   TokenExpiryPolicy tokenExpiryPolicy,
                                   Clock clock) {
        return new TokenIssuer(refreshTokenRepository, tokenGenerator, tokenSigner, tokenExpiryPolicy, clock);
    }

    @Bean
    public RefreshAccessToken refreshAccessToken(RefreshTokenRepository refreshTokenRepository,
                                                 RevocationCache revocationCache) {
        return new RefreshAccessTokenService(refreshTokenRepository, revocationCache);
    }

    @Bean
    public TokenRefresher tokenRefresher(RefreshAccessToken refreshAccessToken,
                                         RefreshTokenRepository refreshTokenRepository,
                                         RoleAssignmentRepository roleAssignmentRepository,
                                         RefreshTokenFamilyPolicy refreshTokenFamilyPolicy,
                                         RevocationCache revocationCache,
                                         TokenGenerator tokenGenerator,
                                         TokenSigner tokenSigner,
                                         TokenExpiryPolicy tokenExpiryPolicy,
                                         Clock clock) {
        return new TokenRefresher(refreshAccessToken, refreshTokenRepository, roleAssignmentRepository,
                refreshTokenFamilyPolicy, revocationCache, tokenGenerator, tokenSigner, tokenExpiryPolicy, clock);
    }

    @Bean
    public RevokeSession revokeSession(RefreshTokenRepository refreshTokenRepository,
                                       RevocationCache revocationCache) {
        return new RevokeSessionService(refreshTokenRepository, revocationCache);
    }

    @Bean
    public RevokeAllSessions revokeAllSessions(RefreshTokenRepository refreshTokenRepository,
                                               RevocationCache revocationCache,
                                               RefreshTokenFamilyPolicy refreshTokenFamilyPolicy) {
        return new RevokeAllSessionsService(refreshTokenRepository, revocationCache, refreshTokenFamilyPolicy);
    }

    @Bean
    public SessionRevoker sessionRevoker(RevokeSession revokeSession,
                                         RevokeAllSessions revokeAllSessions,
                                         RefreshTokenRepository refreshTokenRepository,
                                         TokenGenerator tokenGenerator,
                                         Clock clock) {
        return new SessionRevoker(revokeSession, revokeAllSessions, refreshTokenRepository, tokenGenerator, clock);
    }

    @Bean
    public RequestPasswordReset requestPasswordReset(CredentialRepository credentialRepository,
                                                     PasswordResetRepository passwordResetRepository,
                                                     TokenGenerator tokenGenerator,
                                                     AuthProperties properties,
                                                     Clock clock) {
        return new RequestPasswordResetService(credentialRepository, passwordResetRepository,
                tokenGenerator, properties.resetTokenTtl(), clock);
    }

    @Bean
    public ConfirmPasswordReset confirmPasswordReset(CredentialRepository credentialRepository,
                                                     PasswordResetRepository passwordResetRepository,
                                                     RefreshTokenRepository refreshTokenRepository,
                                                     RevocationCache revocationCache,
                                                     BCryptPasswordHasherAdapter passwordHasher,
                                                     PasswordComplexityPolicy passwordComplexityPolicy,
                                                     RefreshTokenFamilyPolicy refreshTokenFamilyPolicy) {
        return new ConfirmPasswordResetService(credentialRepository, passwordResetRepository,
                refreshTokenRepository, revocationCache, passwordHasher, passwordComplexityPolicy,
                refreshTokenFamilyPolicy);
    }

    @Bean
    public PasswordResetConfirmer passwordResetConfirmer(ConfirmPasswordReset confirmPasswordReset,
                                                         PasswordResetRepository passwordResetRepository,
                                                         TokenGenerator tokenGenerator,
                                                         Clock clock) {
        return new PasswordResetConfirmer(confirmPasswordReset, passwordResetRepository, tokenGenerator, clock);
    }

    @Bean
    public AssignRole assignRole(CredentialRepository credentialRepository,
                                 RoleAssignmentRepository roleAssignmentRepository) {
        return new AssignRoleService(credentialRepository, roleAssignmentRepository);
    }
}
