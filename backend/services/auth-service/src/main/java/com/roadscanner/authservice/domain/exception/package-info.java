/**
 * Domain exception hierarchy, rooted at
 * {@link com.roadscanner.authservice.domain.exception.AuthServiceException}: seven concrete
 * business-failure types matching docs/services/auth-service/exception-strategy.md exactly —
 * {@link com.roadscanner.authservice.domain.exception.InvalidCredentialsException},
 * {@link com.roadscanner.authservice.domain.exception.AccountLockedException},
 * {@link com.roadscanner.authservice.domain.exception.TokenExpiredException},
 * {@link com.roadscanner.authservice.domain.exception.TokenReusedException},
 * {@link com.roadscanner.authservice.domain.exception.ResetTokenInvalidException},
 * {@link com.roadscanner.authservice.domain.exception.PasswordPolicyViolationException}, and
 * {@link com.roadscanner.authservice.domain.exception.IdentifierAlreadyRegisteredException}.
 *
 * Translation to an HTTP response happens exclusively in
 * {@link com.roadscanner.authservice.adapter.in.rest.exception.GlobalExceptionHandler} — none
 * of these types are aware of HTTP.
 */
package com.roadscanner.authservice.domain.exception;
