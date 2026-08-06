package com.roadscanner.providerintegrationservice.adapter.out.provider.flixbus;

import com.roadscanner.providerintegrationservice.domain.model.CancellationResult;
import com.roadscanner.providerintegrationservice.domain.model.Provider;
import com.roadscanner.providerintegrationservice.domain.model.ProviderCategory;
import com.roadscanner.providerintegrationservice.domain.model.ProviderCredentials;
import com.roadscanner.providerintegrationservice.domain.model.ProviderCredentialsId;
import com.roadscanner.providerintegrationservice.domain.model.ProviderId;
import com.roadscanner.providerintegrationservice.domain.model.ProviderOrder;
import com.roadscanner.providerintegrationservice.domain.model.ProviderType;
import com.roadscanner.providerintegrationservice.testsupport.fakes.InMemoryProviderCredentialsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Endpoints 10 and 11 against the documented contract: reading and cancelling a confirmed order.
 *
 * <p>Both are authorised by the order <strong>token</strong>, carried as a query parameter. That is
 * the assertion this class exists for: the token was captured at confirmation and then discarded by
 * every layer above, which made both of these endpoints unreachable in practice — a paid order
 * nobody could read or cancel. A regression that drops the token again fails here rather than in
 * production.
 */
class FlixBusOrderClientTest {

    private static final String BASE_URL = "http://flixbus.test";
    private static final Instant FIXED = Instant.parse("2026-07-01T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(FIXED, ZoneOffset.UTC);
    private static final String PARTNER_TOKEN = "partner-token-abc";
    private static final String ORDER_ID = "order-7781";
    private static final String ORDER_TOKEN = "order-token-xyz";

    private static final Provider PROVIDER = Provider.reconstitute(ProviderId.generate(), ProviderType.FLIXBUS,
            ProviderCategory.BUS, "FlixBus", true, Set.of(), BASE_URL, 5_000, 2, FIXED, FIXED);

    private MockRestServiceServer mockServer;
    private FlixBusOrderClient client;

    private static FlixBusProperties properties() {
        return new FlixBusProperties(BASE_URL, Duration.ofSeconds(5), Duration.ofSeconds(15), "INR", "+91",
                Duration.ofHours(23), 3, Duration.ofMillis(1), "offline", "cash");
    }

    private static FlixBusCredentials credentials() {
        InMemoryProviderCredentialsRepository repository = new InMemoryProviderCredentialsRepository();
        repository.save(ProviderCredentials.issue(ProviderCredentialsId.generate(), PROVIDER.id(),
                "partner@roadscanner.com", "s3cret", PARTNER_TOKEN, FIXED));
        return new FlixBusCredentials(repository);
    }

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        // Order calls are session-authenticated, so a partner login precedes them. Expectations are
        // order-independent so each test can assert only the call it cares about.
        mockServer = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
        RestClient restClient = builder.build();
        FlixBusCredentials credentials = credentials();
        FlixBusMapper mapper = new FlixBusMapper(CLOCK);
        FlixBusExceptionTranslator translator = new FlixBusExceptionTranslator(CLOCK);
        FlixBusSessionProvider sessionProvider = new FlixBusSessionProvider(
                new FlixBusAuthenticationClient(restClient, mapper, translator, credentials, properties(), CLOCK),
                CLOCK);
        client = new FlixBusOrderClient(restClient, mapper, translator,
                new FlixBusRequestContext(credentials, sessionProvider));

        mockServer.expect(org.springframework.test.web.client.ExpectedCount.manyTimes(),
                        requestTo(BASE_URL + FlixBusAuthenticationClient.LOGIN_PATH))
                .andRespond(withSuccess("{\"token\":\"session-token-1\"}", MediaType.APPLICATION_JSON));
    }

    @Test
    void readingAnOrderSendsTheOrderTokenTheProviderRequires() {
        mockServer.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE_URL + "/order/v3/orders/" + ORDER_ID + "?")))
                .andExpect(queryParam("token", ORDER_TOKEN))
                .andRespond(withSuccess("""
                        {"id":"%s","status":"confirmed","total_price":1299.0}
                        """.formatted(ORDER_ID), MediaType.APPLICATION_JSON));

        ProviderOrder order = client.getOrder(PROVIDER, ORDER_ID, ORDER_TOKEN);

        assertThat(order.providerOrderReference()).isEqualTo(ORDER_ID);
        mockServer.verify();
    }

    @Test
    void cancellingAnOrderSendsTheOrderTokenTheProviderRequires() {
        mockServer.expect(requestTo(org.hamcrest.Matchers.startsWith(
                        BASE_URL + "/order/v3/orders/" + ORDER_ID + "/partner/cancel")))
                .andExpect(queryParam("token", ORDER_TOKEN))
                .andRespond(withSuccess("""
                        {"refund":{"amount":1299.0}}
                        """, MediaType.APPLICATION_JSON));

        CancellationResult result = client.cancelOrder(PROVIDER, ORDER_ID, ORDER_TOKEN, "traveller request");

        assertThat(result.providerOrderReference()).isEqualTo(ORDER_ID);
        mockServer.verify();
    }
}
