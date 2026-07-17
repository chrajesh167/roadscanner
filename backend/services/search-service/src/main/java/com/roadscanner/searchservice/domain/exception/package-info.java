/**
 * Domain exception hierarchy, rooted at
 * {@link com.roadscanner.searchservice.domain.exception.SearchServiceException}:
 * {@link com.roadscanner.searchservice.domain.exception.TripNotFoundException}. See that
 * class's Javadoc for why this hierarchy is intentionally narrower than {@code auth-service}'s.
 *
 * Translation to an HTTP response happens exclusively in
 * {@link com.roadscanner.searchservice.adapter.in.rest.exception.GlobalExceptionHandler} — this
 * type is not aware of HTTP.
 */
package com.roadscanner.searchservice.domain.exception;
