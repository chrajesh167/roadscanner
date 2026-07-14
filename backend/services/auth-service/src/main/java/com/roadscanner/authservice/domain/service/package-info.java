/**
 * Domain-level policies: {@link com.roadscanner.authservice.domain.service.PasswordComplexityPolicy},
 * {@link com.roadscanner.authservice.domain.service.PasswordHashingPolicy},
 * {@link com.roadscanner.authservice.domain.service.TokenExpiryPolicy}, and
 * {@link com.roadscanner.authservice.domain.service.RefreshTokenFamilyPolicy}. Pure business
 * rules with no framework dependency — see docs/services/auth-service/security-design.md and
 * validation-strategy.md for the rules each one implements.
 */
package com.roadscanner.authservice.domain.service;
