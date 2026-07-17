package com.roadscanner.searchservice.adapter.in.rest.admin;

import com.roadscanner.searchservice.adapter.in.rest.exception.GlobalExceptionHandler;
import com.roadscanner.searchservice.domain.port.in.RebuildIndex;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReindexController.class)
@Import(GlobalExceptionHandler.class)
class ReindexControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RebuildIndex rebuildIndex;

    @Test
    void triggersRebuildAndReturnsAccepted() throws Exception {
        mockMvc.perform(post("/internal/search/reindex"))
                .andExpect(status().isAccepted());

        verify(rebuildIndex).rebuild();
    }
}
