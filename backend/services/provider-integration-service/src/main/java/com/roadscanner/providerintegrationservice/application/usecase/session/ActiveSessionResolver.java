package com.roadscanner.providerintegrationservice.application.usecase.session;

import com.roadscanner.providerintegrationservice.domain.exception.SessionExpiredException;
import com.roadscanner.providerintegrationservice.domain.model.ProviderSession;
import com.roadscanner.providerintegrationservice.domain.model.ProviderSessionId;
import com.roadscanner.providerintegrationservice.domain.port.out.SessionRepository;

import java.time.Clock;

/**
 * Shared by every use case that requires an already-open session ({@code SearchTripsService},
 * {@code GetSeatMapService}, {@code BlockSeatService}, {@code ReleaseSeatService},
 * {@code ConfirmBookingService}, {@code DownloadTicketService}) — the
 * "not found, not active, or token expired all mean the same thing to the caller"
 * rule lives in exactly one place.
 *
 * Always reads {@link SessionRepository} (the source of truth) rather than
 * {@code TokenCache}: validity depends on {@code providerType} and {@code status}, which live on
 * the full aggregate, not the cached token alone. {@code TokenCache} remains populated on the
 * authenticate/refresh write path per the platform's caching requirement and is available to any
 * future lighter-weight consumer that only needs the token itself.
 */
public class ActiveSessionResolver {

    private final SessionRepository sessionRepository;
    private final Clock clock;

    public ActiveSessionResolver(SessionRepository sessionRepository, Clock clock) {
        this.sessionRepository = sessionRepository;
        this.clock = clock;
    }

    public ProviderSession resolveActive(ProviderSessionId sessionId) {
        ProviderSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new SessionExpiredException(sessionId));
        if (!session.isActive() || session.isTokenExpired(clock.instant())) {
            throw new SessionExpiredException(sessionId);
        }
        return session;
    }
}
