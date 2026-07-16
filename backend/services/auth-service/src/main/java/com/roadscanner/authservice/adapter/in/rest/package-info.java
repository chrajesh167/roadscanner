/**
 * REST controllers and request/response DTOs — the only layer aware this is an HTTP service.
 * Organized by feature (registration, login, token, passwordreset, role) per
 * docs/services/auth-service/package-structure.md, alongside the generic HTTP foundation:
 * global exception mapping ({@code exception}) and correlation-id propagation ({@code filter}).
 *
 * DTOs perform structural validation only (presence, length — see validation-strategy.md);
 * business validation lives in the use cases, and domain value objects re-validate their own
 * invariants regardless. Controllers map DTOs to port commands and never contain business
 * logic; the one composition they own is register/login + token issuance, per
 * {@link com.roadscanner.authservice.domain.port.in.RegisterUser}'s Javadoc.
 */
package com.roadscanner.authservice.adapter.in.rest;
