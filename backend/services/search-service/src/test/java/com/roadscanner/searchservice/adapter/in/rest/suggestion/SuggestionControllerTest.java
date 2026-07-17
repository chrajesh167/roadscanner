package com.roadscanner.searchservice.adapter.in.rest.suggestion;

import com.roadscanner.searchservice.adapter.in.rest.exception.GlobalExceptionHandler;
import com.roadscanner.searchservice.config.SearchProperties;
import com.roadscanner.searchservice.domain.port.in.GetSearchSuggestions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** See {@code SearchControllerTest}'s Javadoc for why {@link SearchProperties} is supplied as a
 * real instance here rather than a {@code @MockBean}. */
@WebMvcTest(SuggestionController.class)
@Import({GlobalExceptionHandler.class, SuggestionControllerTest.TestConfig.class})
class SuggestionControllerTest {

    private static final int DEFAULT_MAX_RESULTS = 7;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GetSearchSuggestions getSearchSuggestions;

    @TestConfiguration
    static class TestConfig {
        @Bean
        SearchProperties searchProperties() {
            return new SearchProperties(
                    new SearchProperties.Pagination(20, 50),
                    new SearchProperties.Suggestions(DEFAULT_MAX_RESULTS),
                    new SearchProperties.Availability(Duration.ofSeconds(15), Duration.ofMillis(500)),
                    new SearchProperties.Kafka("trip-events", "review-events"));
        }
    }

    @Test
    void returnsSuggestionsForAQuery() throws Exception {
        when(getSearchSuggestions.suggest(any())).thenReturn(
                new GetSearchSuggestions.SearchSuggestionsResult(List.of("Mumbai", "Mysore")));

        mockMvc.perform(get("/api/v1/search/suggestions").param("query", "Mu"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestions[0]").value("Mumbai"))
                .andExpect(jsonPath("$.suggestions[1]").value("Mysore"));
    }

    @Test
    void blankQueryReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/search/suggestions").param("query", " "))
                .andExpect(status().isBadRequest());
    }

    @Test
    void defaultsMaxResultsFromConfigurationWhenNotSpecified() throws Exception {
        when(getSearchSuggestions.suggest(any())).thenReturn(new GetSearchSuggestions.SearchSuggestionsResult(List.of()));

        mockMvc.perform(get("/api/v1/search/suggestions").param("query", "Mu"))
                .andExpect(status().isOk());

        verify(getSearchSuggestions).suggest(argThat(command -> command.maxResults() == DEFAULT_MAX_RESULTS));
    }
}
