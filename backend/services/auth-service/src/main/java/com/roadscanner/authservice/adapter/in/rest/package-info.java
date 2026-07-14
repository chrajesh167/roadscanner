/**
 * REST controllers and request/response DTOs — the only layer aware this is an HTTP service.
 * See docs/services/auth-service/package-structure.md.
 *
 * No controllers exist yet as of this bootstrap (registration/login/token/password-reset
 * endpoints are explicitly out of scope today). The exception and filter subpackages contain
 * the generic, non-business-specific HTTP foundation: global exception mapping and the
 * correlation-id filter.
 */
package com.roadscanner.authservice.adapter.in.rest;
