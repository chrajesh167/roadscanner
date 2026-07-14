package com.roadscanner.authservice.adapter.in.rest.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Propagates the platform-wide correlation id (docs/architecture/high-level-design.md §9,
 * docs/services/auth-service/logging-observability.md) through every log line for a request.
 *
 * Reads an inbound X-Correlation-Id header (set by api-gateway); generates one if absent, so
 * the service is still traceable when called directly (e.g. locally, or in a test). The id is
 * echoed back on the response and exposed via MDC so the logging pattern
 * (see logback-spring.xml) and GlobalExceptionHandler can include it without either needing to
 * know how it got there.
 *
 * This is generic cross-cutting infrastructure, not business logic — it carries no
 * authentication or authorization meaning.
 */
@Component
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        MDC.put(MDC_KEY, correlationId);
        response.setHeader(CORRELATION_ID_HEADER, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
