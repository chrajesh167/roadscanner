/**
 * Redis implementation of the RevocationCache port
 * ({@link com.roadscanner.authservice.adapter.out.cache.RedisRevocationCacheAdapter}). Per
 * docs/services/auth-service/database-design.md ("Redis vs. Postgres"): a derived, expendable
 * copy of revocation state — every Redis failure degrades to a Postgres-backed answer, never
 * to an incorrect one.
 */
package com.roadscanner.authservice.adapter.out.cache;
