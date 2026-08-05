package com.roadscanner.searchservice.location.adapter.in.rest;

import com.roadscanner.searchservice.config.CorsConfig;
import com.roadscanner.searchservice.config.SecurityConfig;
import com.roadscanner.searchservice.location.domain.port.in.GetLocation;
import com.roadscanner.searchservice.location.domain.port.in.ManageProviderMappings;
import com.roadscanner.searchservice.location.domain.port.in.SearchProviderMappings;
import com.roadscanner.searchservice.testsupport.security.NoOpJwtDecoderConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The browser preflight for the administrative routes.
 *
 * <p>Exists because no other test can fail for this reason. {@code MockMvc} invokes handlers
 * directly, so every functional test stays green whether or not {@code PUT} and {@code DELETE}
 * appear in the allowed-method list — while a browser, which asks first, is refused before the
 * request is ever sent. The admin console is a browser application, so a missing method makes an
 * otherwise correct endpoint unreachable in exactly the environment that uses it.
 *
 * <p>The preflight itself carries no credentials by design: {@code OPTIONS} is answered before
 * authentication, which is why {@link SecurityConfig} is imported here rather than mocked away —
 * the assertion is worthless if the security chain would have rejected the preflight in production.
 */
@WebMvcTest(ProviderMappingController.class)
@Import({CorsConfig.class, SecurityConfig.class, NoOpJwtDecoderConfig.class})
@ActiveProfiles("test")
class ProviderMappingCorsTest {

    private static final String ORIGIN = "http://localhost:5173";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SearchProviderMappings searchProviderMappings;

    @MockBean
    private ManageProviderMappings manageProviderMappings;

    @MockBean
    private GetLocation getLocation;

    @ParameterizedTest
    @ValueSource(strings = {"GET", "PUT", "DELETE"})
    void theAdminConsoleCanPreflightEveryMethodItNeedsOnAMapping(String method) throws Exception {
        mockMvc.perform(options("/api/v1/provider-mappings/{id}", UUID.randomUUID())
                        .header("Origin", ORIGIN)
                        .header("Access-Control-Request-Method", method))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ORIGIN))
                .andExpect(header().string("Access-Control-Allow-Methods",
                        org.hamcrest.Matchers.containsString(method)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "POST"})
    void theAdminConsoleCanPreflightTheCollection(String method) throws Exception {
        mockMvc.perform(options("/api/v1/provider-mappings")
                        .header("Origin", ORIGIN)
                        .header("Access-Control-Request-Method", method))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ORIGIN));
    }

    @Test
    void anUnknownOriginIsStillRefused() throws Exception {
        // Widening the method list must not have widened the origin list — the two are
        // independent, and a permissive origin would hand any site an admin-shaped request.
        mockMvc.perform(options("/api/v1/provider-mappings")
                        .header("Origin", "https://not-our-console.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }
}
