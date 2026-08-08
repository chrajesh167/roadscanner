package com.roadscanner.providerintegrationservice.config;

import com.roadscanner.providerintegrationservice.application.usecase.audit.AuditRecorder;
import com.roadscanner.providerintegrationservice.application.usecase.auth.AuthenticateProviderService;
import com.roadscanner.providerintegrationservice.application.usecase.auth.RefreshSessionService;
import com.roadscanner.providerintegrationservice.application.usecase.booking.CancelBookingService;
import com.roadscanner.providerintegrationservice.application.usecase.booking.ConfirmBookingService;
import com.roadscanner.providerintegrationservice.application.usecase.booking.GetOrderDetailsService;
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
import com.roadscanner.providerintegrationservice.domain.port.in.CancelBooking;
import com.roadscanner.providerintegrationservice.domain.port.in.ConfirmBooking;
import com.roadscanner.providerintegrationservice.domain.port.in.GetOrderDetails;
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
import com.roadscanner.providerintegrationservice.domain.port.out.ProviderBookingRepository;
import com.roadscanner.providerintegrationservice.domain.port.out.ProviderReservationRepository;
import com.roadscanner.providerintegrationservice.domain.port.out.SessionRepository;
import com.roadscanner.providerintegrationservice.domain.port.out.TokenCache;
import com.roadscanner.providerintegrationservice.domain.service.ProviderClientRegistry;
import com.roadscanner.providerintegrationservice.execution.BackoffStrategy;
import com.roadscanner.providerintegrationservice.execution.ExecutionPolicyProviderClient;
import com.roadscanner.providerintegrationservice.execution.ProviderExecutionExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.roadscanner.providerintegrationservice.application.usecase.registry.ManageProviderCredentialsService;
import com.roadscanner.providerintegrationservice.application.usecase.registry.ManageProvidersService;
import com.roadscanner.providerintegrationservice.application.usecase.registry.RefreshProviderSessionService;
import com.roadscanner.providerintegrationservice.application.usecase.registry.TestProviderConnectionService;
import com.roadscanner.providerintegrationservice.domain.port.in.ManageProviderCredentials;
import com.roadscanner.providerintegrationservice.application.usecase.search.SearchProviderTripsService;
import com.roadscanner.providerintegrationservice.domain.port.in.ManageProviders;
import com.roadscanner.providerintegrationservice.domain.port.in.SearchProviderTrips;
import com.roadscanner.providerintegrationservice.domain.port.in.RefreshProviderSession;
import com.roadscanner.providerintegrationservice.domain.port.in.TestProviderConnection;
import com.roadscanner.providerintegrationservice.domain.port.out.ProviderCredentialsRepository;

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
    public ProviderClientRegistry providerClientRegistry(List<ProviderClient> providerClients,
                                                        ProviderConfigurationRepository configurationRepository,
                                                        ProviderExecutionExecutor executionExecutor,
                                                        BackoffStrategy providerBackoffStrategy) {
        // Every client is wrapped, so timeout/retry/backoff/metrics/correlation-id apply to all
        // providers identically and a new adapter inherits them by existing. Wrapping here rather
        // than in each adapter is what stops a provider from quietly opting out.
        List<ProviderClient> managed = providerClients.stream()
                .map(client -> (ProviderClient) new ExecutionPolicyProviderClient(
                        client, configurationRepository, executionExecutor, providerBackoffStrategy))
                .toList();
        return new ProviderClientRegistry(managed);
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
    public SearchTrips searchTrips(ActiveSessionResolver sessionResolver, ProviderClientRegistry registry, ProviderConfigurationRepository configurationRepository) {
        return new SearchTripsService(sessionResolver, registry, configurationRepository);
    }

    @Bean
    public GetSeatMap getSeatMap(ActiveSessionResolver sessionResolver, ProviderClientRegistry registry,
                                  ProviderCache providerCache) {
        return new GetSeatMapService(sessionResolver, registry, providerCache);
    }

    @Bean
    public BlockSeat blockSeat(ActiveSessionResolver sessionResolver, ProviderClientRegistry registry,
                               ProviderReservationRepository reservationRepository) {
        return new BlockSeatService(sessionResolver, registry, reservationRepository);
    }

    @Bean
    public ReleaseSeat releaseSeat(ActiveSessionResolver sessionResolver, ProviderClientRegistry registry) {
        return new ReleaseSeatService(sessionResolver, registry);
    }

    @Bean
    public ConfirmBooking confirmBooking(ActiveSessionResolver sessionResolver, ProviderClientRegistry registry,
                                         ProviderReservationRepository reservationRepository,
                                         ProviderBookingRepository bookingRepository) {
        return new ConfirmBookingService(sessionResolver, registry, reservationRepository, bookingRepository);
    }

    @Bean
    public CancelBooking cancelBooking(ActiveSessionResolver sessionResolver, ProviderClientRegistry registry,
                                       ProviderBookingRepository bookingRepository, Clock clock) {
        return new CancelBookingService(sessionResolver, registry, bookingRepository, clock);
    }

    @Bean
    public GetOrderDetails getOrderDetails(ActiveSessionResolver sessionResolver, ProviderClientRegistry registry) {
        return new GetOrderDetailsService(sessionResolver, registry);
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

    // --- Sprint 2: provider registry administration. ---
    //
    // TestProviderConnection and RefreshProviderSession take the existing CheckProviderHealth and
    // AuthenticateProvider ports rather than their own collaborators: the admin API is a second
    // trigger for behaviour that already exists, never a second implementation of it.

    @Bean
    public ManageProviders manageProviders(ProviderConfigurationRepository configurationRepository, Clock clock) {
        return new ManageProvidersService(configurationRepository, clock);
    }

    @Bean
    public ManageProviderCredentials manageProviderCredentials(ProviderCredentialsRepository credentialsRepository,
                                                               ProviderConfigurationRepository configurationRepository,
                                                               Clock clock) {
        return new ManageProviderCredentialsService(credentialsRepository, configurationRepository, clock);
    }

    @Bean
    public TestProviderConnection testProviderConnection(ProviderConfigurationRepository configurationRepository,
                                                         CheckProviderHealth checkProviderHealth) {
        return new TestProviderConnectionService(configurationRepository, checkProviderHealth);
    }

    @Bean
    public RefreshProviderSession refreshProviderSession(ProviderConfigurationRepository configurationRepository,
                                                         AuthenticateProvider authenticateProvider) {
        return new RefreshProviderSessionService(configurationRepository, authenticateProvider);
    }

    /**
     * The generic, session-less provider search. Takes the registry repository so a disabled
     * provider is refused before any call is made, and the client registry so every provider is
     * reached through the same execution-policy-wrapped path.
     */
    @Bean
    public SearchProviderTrips searchProviderTrips(ProviderConfigurationRepository configurationRepository,
                                                   ProviderClientRegistry providerClientRegistry) {
        return new SearchProviderTripsService(configurationRepository, providerClientRegistry);
    }
}
