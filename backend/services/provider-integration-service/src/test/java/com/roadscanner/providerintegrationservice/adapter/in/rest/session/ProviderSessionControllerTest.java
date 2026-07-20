package com.roadscanner.providerintegrationservice.adapter.in.rest.session;

import com.roadscanner.providerintegrationservice.adapter.in.rest.exception.GlobalExceptionHandler;
import com.roadscanner.providerintegrationservice.adapter.in.rest.filter.CorrelationIdFilter;
import com.roadscanner.providerintegrationservice.domain.exception.ProviderNotSupportedException;
import com.roadscanner.providerintegrationservice.domain.model.ProviderSessionId;
import com.roadscanner.providerintegrationservice.domain.model.ProviderType;
import com.roadscanner.providerintegrationservice.domain.port.in.AuthenticateProvider;
import com.roadscanner.providerintegrationservice.domain.port.in.RefreshSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProviderSessionController.class)
@Import({GlobalExceptionHandler.class, CorrelationIdFilter.class})
class ProviderSessionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthenticateProvider authenticateProvider;

    @MockBean
    private RefreshSession refreshSession;

    @Test
    void authenticateReturnsCreatedWithTheNewSession() throws Exception {
        ProviderSessionId sessionId = ProviderSessionId.generate();
        when(authenticateProvider.authenticate(any())).thenReturn(new AuthenticateProvider.Result(sessionId,
                ProviderType.MOCK, Instant.parse("2026-08-01T00:00:00Z")));

        mockMvc.perform(post("/internal/api/v1/providers/MOCK/sessions"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sessionId").value(sessionId.value().toString()))
                .andExpect(jsonPath("$.providerType").value("MOCK"));
    }

    @Test
    void authenticateAgainstAnUnsupportedProviderReturns404() throws Exception {
        when(authenticateProvider.authenticate(any())).thenThrow(new ProviderNotSupportedException(new ProviderType("REDBUS")));

        mockMvc.perform(post("/internal/api/v1/providers/REDBUS/sessions"))
                .andExpect(status().isNotFound());
    }
}
