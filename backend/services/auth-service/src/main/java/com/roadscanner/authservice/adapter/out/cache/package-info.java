/**
 * Redis implementation of the RevocationCache port. See
 * docs/services/auth-service/database-design.md ("Redis vs. Postgres").
 *
 * Intentionally empty as of this bootstrap — the generic Redis client wiring lives in
 * config.RedisConfig; the revocation-specific cache adapter is business logic, added per
 * docs/services/auth-service/implementation-roadmap.md step 6.
 */
package com.roadscanner.authservice.adapter.out.cache;
