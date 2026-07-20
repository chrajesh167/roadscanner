package com.roadscanner.providerintegrationservice.config;

import com.roadscanner.providerintegrationservice.application.usecase.audit.AuditRecorder;
import com.roadscanner.providerintegrationservice.application.usecase.auth.AuthenticateProviderService;
import com.roadscanner.providerintegrationservice.application.usecase.auth.RefreshSessionService;
import com.roadscanner.providerintegrationservice.application.usecase.booking.ConfirmBookingService;
import com.roadscanner.providerintegrationservice.application.usecase.capability.GetProviderCapabilitiesService;
import com.roadscanner.providerintegrationservice.application.usecase.health.CheckProviderHealthService;
import com.roadscanner.providerintegrationservice.application.usecase.health.ProviderHealthMonitor;
import com.roadscanner.providerintegrationservice.application.usecase.search.SearchTripsService;
import com.roadscanner.providerintegrationservice.application.usecase.seatblock.BlockSeatService;
import com.roadscanner.providerintegrationservice.application.usecase.seatblock.ReleaseSeatService;
import com.roadscanner.providerintegrationservice.application.usecase.seatmap.GetSeatMapService;
import com.roadscanner.providerintegrationservice.application.usecase.session.ActiveSessionResolver;
import com.roadscanner.providerintegrationservice.application.usecase.session.SessionExpirySweeper;
import com.roadscanner.providerintegrationservice.application.usecase.ticket.DownloadTicketService;
import com.roadscanner.providerintegrationservice.domain.port.in.AuthenticateProvider;
import com.roadscanner.providerintegrationservice.domain.port.in.BlockSeat;
import com.roadscanner.providerintegrationservice.domain.port.in.CheckProviderHealth;
import com.roadscanner.providerintegrationservice.domain.port.in.ConfirmBooking;
import com.roadscanner.providerintegrationservice.domain.port.in.DownloadTicket;
import com.roadscanner.providerintegrationservice.domain.port.in.GetProviderCapabilities;
import com.roadscanner.providerintegrationservice.domain.port.in.GetSeatMap;
import com.roadscanner.providerintegrationservice.domain.port.in.RefreshSession;
import com.roadscanner.providerintegrationservice.domain.port.in.ReleaseSeat;
import com.roadscanner.providerintegrationservice.domain.port.in.SearchTrips;
import com.roadscanner.providerintegrationservice.domain.port.out.AuditPublisher;
import com.roadscanner.providerintegrationservice.domain.port.out.AuditRecordRepository;
import com.roadscanner.providerintegrationservice.domain.port.out.ProviderCache;
import com.roadscanner.providerintegrationservice.domain.port.out.ProviderClient;
import com.roadscanner.providerintegrationservice.domain.port.out.ProviderConfigurationRepository;
import com.roadscanner.providerintegrationservice.domain.port.out.ProviderHealthRepository;
import com.roadscanner.providerintegrationservice.domain.port.out.SessionRepository;
import com.roadscanner.providerintegrationservice.domain.port.out.TokenCache;
import com.roadscanner.providerintegrationservice.domain.service.ProviderClientRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.List;

/**
 * Explicit bean wiring for the domain registry and every application-layer use case. The
 * application classes carry no Spring stereotype annotations — plain constructors wired here,
 * keeping that layer framework-light and every dependency of every use case visible in one
 * place, matching {@code auth-service}/{@code search-service}'s identical {@code UseCaseConfig}.
 *
 * {@code List<ProviderClient> providerClients} is populated by Spring collecting every
 * {@code @Component}-annotated {@code ProviderClient} implementation on the classpath
 * ({@code FlixBusProviderClientAdapter}, {@code MockProviderClientAdapter}, and any future
 * provider's own adapter) — this is the concrete mechanism behind
 * {@link ProviderClientRegistry}'s "add a provider without changing business logic" claim.
 */
@Configuration
public class UseCaseConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public ProviderClientRegistry providerClientRegistry(List<ProviderClient> providerClients) {
        return new ProviderClientRegistry(providerClients);
    }

    @Bean
    public ActiveSessionResolver activeSessionResolver(SessionRepository sessionRepository, Clock clock) {
        return new ActiveSessionResolver(sessionRepository, clock);
    }

    @Bean
    public AuditRecorder auditRecorder(AuditRecordRepository auditRecordRepository, AuditPublisher auditPublisher,
                                        Clock clock) {
        return new AuditRecorder(auditRecordRepository, auditPublisher, clock);
    }

    @Bean
    public AuthenticateProvider authenticateProvider(ProviderConfigurationRepository configurationRepository,
                                                       ProviderClientRegistry registry, SessionRepository sessionRepository,
                                                       TokenCache tokenCache, Clock clock) {
        return new AuthenticateProviderService(configurationRepository, registry, sessionRepository, tokenCache, clock);
    }

    @Bean
    public RefreshSession refreshSession(SessionRepository sessionRepository,
                                          ProviderConfigurationRepository configurationRepository,
                                          ProviderClientRegistry registry, TokenCache tokenCache, Clock clock) {
        return new RefreshSessionService(sessionRepository, configurationRepository, registry, tokenCache, clock);
    }

    @Bean
    public SearchTrips searchTrips(ActiveSessionResolver sessionResolver, ProviderClientRegistry registry) {
        return new SearchTripsService(sessionResolver, registry);
    }

    @Bean
    public GetSeatMap getSeatMap(ActiveSessionResolver sessionResolver, ProviderClientRegistry registry,
                                  ProviderCache providerCache) {
        return new GetSeatMapService(sessionResolver, registry, providerCache);
    }

    @Bean
    public BlockSeat blockSeat(ActiveSessionResolver sessionResolver, ProviderClientRegistry registry) {
        return new BlockSeatService(sessionResolver, registry);
    }

    @Bean
    public ReleaseSeat releaseSeat(ActiveSessionResolver sessionResolver, ProviderClientRegistry registry) {
        return new ReleaseSeatService(sessionResolver, registry);
    }

    @Bean
    public ConfirmBooking confirmBooking(ActiveSessionResolver sessionResolver, ProviderClientRegistry registry) {
        return new ConfirmBookingService(sessionResolver, registry);
    }

    @Bean
    public DownloadTicket downloadTicket(ActiveSessionResolver sessionResolver, ProviderClientRegistry registry) {
        return new DownloadTicketService(sessionResolver, registry);
    }

    @Bean
    public GetProviderCapabilities getProviderCapabilities(ProviderConfigurationRepository configurationRepository,
                                                             ProviderClientRegistry registry, ProviderCache providerCache) {
        return new GetProviderCapabilitiesService(configurationRepository, registry, providerCache);
    }

    @Bean
    public CheckProviderHealth checkProviderHealth(ProviderConfigurationRepository configurationRepository,
                                                     ProviderClientRegistry registry, ProviderHealthRepository healthRepository,
                                                     AuditRecorder auditRecorder, Clock clock) {
        return new CheckProviderHealthService(configurationRepository, registry, healthRepository, auditRecorder, clock);
    }

    @Bean
    public ProviderHealthMonitor providerHealthMonitor(ProviderConfigurationRepository configurationRepository,
                                                         CheckProviderHealth checkProviderHealth) {
        return new ProviderHealthMonitor(configurationRepository, checkProviderHealth);
    }

    @Bean
    public SessionExpirySweeper sessionExpirySweeper(SessionRepository sessionRepository, TokenCache tokenCache,
                                                       AuditRecorder auditRecorder, Clock clock) {
        return new SessionExpirySweeper(sessionRepository, tokenCache, auditRecorder, clock);
    }
}
