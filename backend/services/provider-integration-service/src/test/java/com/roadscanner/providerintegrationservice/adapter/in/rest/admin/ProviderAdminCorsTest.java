package com.roadscanner.providerintegrationservice.adapter.in.rest.admin;

import com.roadscanner.providerintegrationservice.config.CorsConfig;
import com.roadscanner.providerintegrationservice.config.SecurityConfig;
import com.roadscanner.providerintegrationservice.domain.port.in.ManageProviderCredentials;
import com.roadscanner.providerintegrationservice.domain.port.in.ManageProviders;
import com.roadscanner.providerintegrationservice.domain.port.in.RefreshProviderSession;
import com.roadscanner.providerintegrationservice.domain.port.in.TestProviderConnection;
import com.roadscanner.providerintegrationservice.testsupport.security.NoOpJwtDecoderConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The cross-origin half of the admin registry contract.
 *
 * <p>Unlike every other test in this package, which asserts what a handler does once a request
 * reaches it, this one asserts that a browser is allowed to send the request at all. The two are
 * independent: {@code ProviderAdminControllerTest} passes whether or not {@code PUT} survives the
 * preflight, because {@code MockMvc} calls the handler directly.
 *
 * <p>That gap is exactly how {@code CorsConfig} came to omit {@code PUT} while the admin API grew
 * two {@code PUT} routes — update a provider, and replace its credentials. Both were unreachable
 * from any browser until the admin console was written against them.
 */
@WebMvcTest(ProviderAdminController.class)
@Import({SecurityConfig.class, CorsConfig.class, NoOpJwtDecoderConfig.class})
@TestPropertySource(properties = "roadscanner.cors.allowed-origins=http://localhost:5173,http://localhost:5174")
class ProviderAdminCorsTest {

    private static final String ADMIN_ORIGIN = "http://localhost:5174";
    private static final String PROVIDER_ID = "0b6b5a1e-6f0e-4a1c-9a3f-2f1a5c8d7e4b";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ManageProviders manageProviders;

    @MockBean
    private ManageProviderCredentials manageCredentials;

    @MockBean
    private TestProviderConnection testProviderConnection;

    @MockBean
    private RefreshProviderSession refreshProviderSession;

    @ParameterizedTest
    @ValueSource(strings = {"GET", "PUT"})
    void preflightAllowsEveryMethodTheProviderResourceExposes(String method) throws Exception {
        mockMvc.perform(options("/api/v1/providers/{id}", PROVIDER_ID)
                        .header("Origin", ADMIN_ORIGIN)
                        .header("Access-Control-Request-Method", method))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ADMIN_ORIGIN));
    }

    /** The credential write is the costliest route to lose: without it an admin can inspect
     * credential state but can never rotate a partner secret. */
    @Test
    void preflightAllowsTheCredentialWrite() throws Exception {
        mockMvc.perform(options("/api/v1/providers/{id}/credentials", PROVIDER_ID)
                        .header("Origin", ADMIN_ORIGIN)
                        .header("Access-Control-Request-Method", "PUT"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ADMIN_ORIGIN));
    }

    @Test
    void preflightAllowsTheLifecycleActions() throws Exception {
        mockMvc.perform(options("/api/v1/providers/{id}/enable", PROVIDER_ID)
                        .header("Origin", ADMIN_ORIGIN)
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ADMIN_ORIGIN));
    }

    @Test
    void preflightFromAnUnknownOriginIsRejected() throws Exception {
        mockMvc.perform(options("/api/v1/providers")
                        .header("Origin", "https://attacker.example")
                        .header("Access-Control-Request-Method", "PUT"))
                .andExpect(status().isForbidden());
    }
}
