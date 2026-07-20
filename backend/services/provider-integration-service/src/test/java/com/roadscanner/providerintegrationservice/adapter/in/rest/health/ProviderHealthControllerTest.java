package com.roadscanner.providerintegrationservice.adapter.in.rest.health;

import com.roadscanner.providerintegrationservice.adapter.in.rest.exception.GlobalExceptionHandler;
import com.roadscanner.providerintegrationservice.adapter.in.rest.filter.CorrelationIdFilter;
import com.roadscanner.providerintegrationservice.domain.model.HealthState;
import com.roadscanner.providerintegrationservice.domain.model.ProviderHealth;
import com.roadscanner.providerintegrationservice.domain.model.ProviderType;
import com.roadscanner.providerintegrationservice.domain.port.in.CheckProviderHealth;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProviderHealthController.class)
@Import({GlobalExceptionHandler.class, CorrelationIdFilter.class})
class ProviderHealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CheckProviderHealth checkProviderHealth;

    @Test
    void returnsTheCurrentHealthRecord() throws Exception {
        ProviderHealth health = ProviderHealth.reconstitute(ProviderType.MOCK, HealthState.HEALTHY,
                Instant.parse("2026-07-01T00:00:00Z"), Instant.parse("2026-07-01T00:00:00Z"), null, 0,
                Instant.parse("2026-07-01T00:00:00Z"));
        when(checkProviderHealth.check(any())).thenReturn(new CheckProviderHealth.Result(health));

        mockMvc.perform(get("/internal/api/v1/providers/MOCK/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providerType").value("MOCK"))
                .andExpect(jsonPath("$.currentState").value("HEALTHY"))
                .andExpect(jsonPath("$.consecutiveFailures").value(0));
    }
}
