/**
 * Domain exception hierarchy, rooted at
 * {@link com.roadscanner.authservice.domain.exception.AuthServiceException}: the seven
 * business-failure types from docs/services/auth-service/exception-strategy.md —
 * {@link com.roadscanner.authservice.domain.exception.InvalidCredentialsException},
 * {@link com.roadscanner.authservice.domain.exception.AccountLockedException},
 * {@link com.roadscanner.authservice.domain.exception.TokenExpiredException},
 * {@link com.roadscanner.authservice.domain.exception.TokenReusedException},
 * {@link com.roadscanner.authservice.domain.exception.ResetTokenInvalidException},
 * {@link com.roadscanner.authservice.domain.exception.PasswordPolicyViolationException},
 * {@link com.roadscanner.authservice.domain.exception.IdentifierAlreadyRegisteredException} —
 * plus {@link com.roadscanner.authservice.domain.exception.UserNotFoundException}, added with
 * the AssignRole use case for its internal-only "no such user" failure (that document's list
 * is conceptual, and this addition follows its "specific, meaningfully-named subtypes" rule).
 *
 * Translation to an HTTP response happens exclusively in
 * {@link com.roadscanner.authservice.adapter.in.rest.exception.GlobalExceptionHandler} — none
 * of these types are aware of HTTP.
 */
package com.roadscanner.authservice.domain.exception;
