package com.roadscanner.providerintegrationservice.adapter.in.rest.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roadscanner.providerintegrationservice.adapter.in.rest.exception.GlobalExceptionHandler;
import com.roadscanner.providerintegrationservice.adapter.in.rest.filter.CorrelationIdFilter;
import com.roadscanner.providerintegrationservice.config.SecurityConfig;
import com.roadscanner.providerintegrationservice.domain.exception.DuplicateProviderException;
import com.roadscanner.providerintegrationservice.domain.exception.ProviderNotFoundException;
import com.roadscanner.providerintegrationservice.domain.model.HealthState;
import com.roadscanner.providerintegrationservice.domain.model.Provider;
import com.roadscanner.providerintegrationservice.domain.model.ProviderCapability;
import com.roadscanner.providerintegrationservice.domain.model.ProviderCategory;
import com.roadscanner.providerintegrationservice.domain.model.ProviderHealth;
import com.roadscanner.providerintegrationservice.domain.model.ProviderId;
import com.roadscanner.providerintegrationservice.domain.model.ProviderSessionId;
import com.roadscanner.providerintegrationservice.domain.model.ProviderType;
import com.roadscanner.providerintegrationservice.domain.port.in.ManageProviderCredentials;
import com.roadscanner.providerintegrationservice.domain.port.in.ManageProviders;
import com.roadscanner.providerintegrationservice.domain.port.in.RefreshProviderSession;
import com.roadscanner.providerintegrationservice.domain.port.in.TestProviderConnection;
import com.roadscanner.providerintegrationservice.testsupport.security.NoOpJwtDecoderConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The admin registry contract: authorization, status mapping, and — the part that matters most —
 * that no response ever carries a partner secret.
 */
@WebMvcTest(ProviderAdminController.class)
@Import({GlobalExceptionHandler.class, CorrelationIdFilter.class, SecurityConfig.class,
        NoOpJwtDecoderConfig.class})
class ProviderAdminControllerTest {

    private static final Instant NOW = Instant.parse("2026-07-29T12:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ManageProviders manageProviders;

    @MockBean
    private ManageProviderCredentials manageCredentials;

    @MockBean
    private TestProviderConnection testProviderConnection;

    @MockBean
    private RefreshProviderSession refreshProviderSession;

    private static RequestPostProcessor admin() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_" + SecurityConfig.ADMIN_ROLE));
    }

    private static RequestPostProcessor traveler() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_TRAVELER"));
    }

    private static Provider flixbus() {
        return Provider.reconstitute(ProviderId.generate(), ProviderType.FLIXBUS, ProviderCategory.BUS, "FlixBus",
                false, Set.of(ProviderCapability.SEARCH, ProviderCapability.SEAT_MAP),
                "https://partner.flixbus.com", 8_000, 2, NOW, NOW);
    }

    private String json(Map<String, Object> body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    private Map<String, Object> validRequest() {
        return Map.of("code", "FLIXBUS", "category", "BUS", "displayName", "FlixBus",
                "capabilities", List.of("SEARCH"), "baseUrl", "https://partner.flixbus.com",
                "timeoutMs", 8000, "retryCount", 2);
    }

    // ---------- Authorization ----------

    @Test
    void everyRouteRequiresAuthentication() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/providers")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/providers/{id}", id)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/providers").contentType(MediaType.APPLICATION_JSON)
                .content(json(validRequest()))).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/providers/{id}/enable", id)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/providers/{id}/test", id)).andExpect(status().isUnauthorized());

        verify(manageProviders, never()).list(anyBoolean());
    }

    @Test
    void everyRouteIsForbiddenForANonAdmin() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/providers").with(traveler())).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/providers/{id}", id).with(traveler())).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/providers").with(traveler()).contentType(MediaType.APPLICATION_JSON)
                .content(json(validRequest()))).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/providers/{id}", id).with(traveler()).contentType(MediaType.APPLICATION_JSON)
                .content(json(validRequest()))).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/providers/{id}/enable", id).with(traveler())).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/providers/{id}/disable", id).with(traveler())).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/providers/{id}/test", id).with(traveler())).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/providers/{id}/refresh-session", id).with(traveler()))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/providers/{id}/credentials", id).with(traveler())
                .contentType(MediaType.APPLICATION_JSON).content("{\"partnerPassword\":\"x\"}"))
                .andExpect(status().isForbidden());

        // Authorization is decided before anything reaches a use case.
        verify(manageProviders, never()).register(any());
        verify(manageProviders, never()).update(any());
        verify(manageCredentials, never()).store(any());
    }

    // ---------- Reads ----------

    @Test
    void listsProvidersForAnAdmin() throws Exception {
        Provider provider = flixbus();
        when(manageProviders.list(false)).thenReturn(List.of(provider));

        mockMvc.perform(get("/api/v1/providers").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(provider.id().toString()))
                .andExpect(jsonPath("$[0].code").value("FLIXBUS"))
                .andExpect(jsonPath("$[0].category").value("BUS"))
                .andExpect(jsonPath("$[0].enabled").value(false))
                .andExpect(jsonPath("$[0].timeoutMs").value(8000))
                .andExpect(jsonPath("$[0].retryCount").value(2))
                // Sorted, so the payload is stable between calls.
                .andExpect(jsonPath("$[0].capabilities[0]").value("SEARCH"))
                .andExpect(jsonPath("$[0].capabilities[1]").value("SEAT_MAP"));
    }

    @Test
    void passesTheEnabledOnlyFilterThrough() throws Exception {
        when(manageProviders.list(true)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/providers").param("enabledOnly", "true").with(admin()))
                .andExpect(status().isOk());

        verify(manageProviders).list(true);
    }

    @Test
    void getsAProviderById() throws Exception {
        Provider provider = flixbus();
        when(manageProviders.get(any())).thenReturn(provider);

        mockMvc.perform(get("/api/v1/providers/{id}", provider.id().value()).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("FLIXBUS"));
    }

    @Test
    void returnsNotFoundForAnUnknownProvider() throws Exception {
        when(manageProviders.get(any())).thenThrow(new ProviderNotFoundException(ProviderId.generate()));

        mockMvc.perform(get("/api/v1/providers/{id}", UUID.randomUUID()).with(admin()))
                .andExpect(status().isNotFound());
    }

    @Test
    void neverExposesCredentialsOnARegistryResponse() throws Exception {
        when(manageProviders.get(any())).thenReturn(flixbus());

        String body = mockMvc.perform(get("/api/v1/providers/{id}", UUID.randomUUID()).with(admin()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("password", "partnerPassword", "token", "partnerToken", "credential");
    }

    // ---------- Writes ----------

    @Test
    void registersAProvider() throws Exception {
        Provider provider = flixbus();
        when(manageProviders.register(any())).thenReturn(provider);

        mockMvc.perform(post("/api/v1/providers").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(json(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/providers/" + provider.id()))
                .andExpect(jsonPath("$.enabled").value(false));

        verify(manageProviders).register(argThat(command ->
                command.type().equals(ProviderType.FLIXBUS)
                        && command.category().equals(ProviderCategory.BUS)
                        && command.timeoutMillis() == 8_000));
    }

    @Test
    void defaultsResilienceSettingsWhenOmitted() throws Exception {
        when(manageProviders.register(any())).thenReturn(flixbus());

        mockMvc.perform(post("/api/v1/providers").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", "REDBUS", "category", "BUS", "displayName", "RedBus",
                                "capabilities", List.of("SEARCH")))))
                .andExpect(status().isCreated());

        // Matching V6's column defaults, so an omitted field means the same thing whether the row
        // arrives through this API or a migration.
        verify(manageProviders).register(argThat(command ->
                command.timeoutMillis() == 5_000 && command.retryCount() == 2));
    }

    @Test
    void rejectsARegistrationWithNoCode() throws Exception {
        mockMvc.perform(post("/api/v1/providers").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("category", "BUS", "displayName", "RedBus",
                                "capabilities", List.of("SEARCH")))))
                .andExpect(status().isBadRequest());

        verify(manageProviders, never()).register(any());
    }

    @Test
    void rejectsAMissingDisplayNameOrCapabilities() throws Exception {
        mockMvc.perform(post("/api/v1/providers").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", "REDBUS", "category", "BUS", "capabilities", List.of("SEARCH")))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/providers").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", "REDBUS", "category", "BUS", "displayName", "RedBus",
                                "capabilities", List.of()))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsAnOutOfRangeRetryCount() throws Exception {
        mockMvc.perform(post("/api/v1/providers").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", "REDBUS", "category", "BUS", "displayName", "RedBus",
                                "capabilities", List.of("SEARCH"), "retryCount", 99))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsConflictForADuplicateCode() throws Exception {
        when(manageProviders.register(any())).thenThrow(new DuplicateProviderException(ProviderType.FLIXBUS));

        mockMvc.perform(post("/api/v1/providers").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(json(validRequest())))
                .andExpect(status().isConflict());
    }

    @Test
    void updatesAProviderTargetingThePathId() throws Exception {
        Provider provider = flixbus();
        UUID pathId = UUID.randomUUID();
        when(manageProviders.update(any())).thenReturn(provider);

        mockMvc.perform(put("/api/v1/providers/{id}", pathId).with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(json(validRequest())))
                .andExpect(status().isOk());

        verify(manageProviders).update(argThat(command -> command.providerId().value().equals(pathId)));
    }

    // ---------- Lifecycle and delegation ----------

    @Test
    void enablesAndDisablesAProvider() throws Exception {
        Provider provider = flixbus();
        when(manageProviders.enable(any())).thenReturn(new ManageProviders.ToggleResult(provider, true));
        when(manageProviders.disable(any())).thenReturn(new ManageProviders.ToggleResult(provider, true));

        mockMvc.perform(post("/api/v1/providers/{id}/enable", provider.id().value()).with(admin()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/providers/{id}/disable", provider.id().value()).with(admin()))
                .andExpect(status().isOk());
    }

    @Test
    void aRepeatedToggleStillSucceeds() throws Exception {
        Provider provider = flixbus();
        when(manageProviders.enable(any())).thenReturn(new ManageProviders.ToggleResult(provider, false));

        // Idempotent: a retried enable must not surface as an error.
        mockMvc.perform(post("/api/v1/providers/{id}/enable", provider.id().value()).with(admin()))
                .andExpect(status().isOk());
    }

    @Test
    void testsAProviderConnection() throws Exception {
        when(testProviderConnection.test(any())).thenReturn(new TestProviderConnection.Result(
                ProviderHealth.unknown(ProviderType.FLIXBUS, NOW)));

        mockMvc.perform(post("/api/v1/providers/{id}/test", UUID.randomUUID()).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providerType").value("FLIXBUS"))
                .andExpect(jsonPath("$.currentState").value(HealthState.UNKNOWN.name()));
    }

    @Test
    void refreshesASessionWithoutReturningItsToken() throws Exception {
        ProviderSessionId sessionId = ProviderSessionId.generate();
        when(refreshProviderSession.refresh(any()))
                .thenReturn(new RefreshProviderSession.Result(sessionId, NOW.plusSeconds(3600)));

        String body = mockMvc.perform(post("/api/v1/providers/{id}/refresh-session", UUID.randomUUID()).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(sessionId.toString()))
                .andReturn().getResponse().getContentAsString();

        // Handing back the access token would put a live provider credential in a browser and in
        // every proxy log along the way.
        assertThat(body).doesNotContain("accessToken", "access_token", "token\":");
    }

    // ---------- Credentials ----------

    @Test
    void storesCredentialsAndReportsOnlyTheirPresence() throws Exception {
        when(manageCredentials.summarise(any())).thenReturn(Optional.of(
                new ManageProviderCredentials.CredentialsSummary(true, false, true, NOW)));

        String body = mockMvc.perform(put("/api/v1/providers/{id}/credentials", UUID.randomUUID()).with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("partnerEmail", "partner@roadscanner.com",
                                "partnerPassword", "s3cret-value"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasPassword").value(true))
                .andExpect(jsonPath("$.hasToken").value(false))
                .andExpect(jsonPath("$.encrypted").value(true))
                .andReturn().getResponse().getContentAsString();

        // Write-only: an admin can replace credentials but never read them back, so a compromised
        // admin session cannot exfiltrate every partner secret in the platform.
        assertThat(body).doesNotContain("s3cret-value", "partner@roadscanner.com");
    }

    @Test
    void reportsCredentialStateWithoutTheValues() throws Exception {
        when(manageCredentials.summarise(any())).thenReturn(Optional.of(
                new ManageProviderCredentials.CredentialsSummary(false, true, false, NOW)));

        mockMvc.perform(get("/api/v1/providers/{id}/credentials", UUID.randomUUID()).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasToken").value(true))
                .andExpect(jsonPath("$.partnerPassword").doesNotExist())
                .andExpect(jsonPath("$.partnerToken").doesNotExist());
    }

    @Test
    void returnsNotFoundWhenNoCredentialsAreStored() throws Exception {
        when(manageCredentials.summarise(any())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/providers/{id}/credentials", UUID.randomUUID()).with(admin()))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsCredentialsThatCannotAuthenticateAnything() throws Exception {
        when(manageCredentials.store(any()))
                .thenThrow(new IllegalArgumentException("credentials must carry at least a partnerPassword"));

        mockMvc.perform(put("/api/v1/providers/{id}/credentials", UUID.randomUUID()).with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("partnerEmail", "partner@roadscanner.com"))))
                .andExpect(status().isBadRequest());
    }
}
