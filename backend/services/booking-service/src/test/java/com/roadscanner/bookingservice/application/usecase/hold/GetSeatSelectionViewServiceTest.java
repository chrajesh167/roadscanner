package com.roadscanner.bookingservice.application.usecase.hold;

import com.roadscanner.bookingservice.domain.exception.TripNotBookableException;
import com.roadscanner.bookingservice.domain.model.ProviderType;
import com.roadscanner.bookingservice.domain.model.TripId;
import com.roadscanner.bookingservice.domain.port.in.GetSeatSelectionView;
import com.roadscanner.bookingservice.domain.port.out.InventoryClient;
import com.roadscanner.bookingservice.domain.port.out.ProviderIntegrationClient;
import com.roadscanner.bookingservice.testsupport.fakes.StubInventoryClient;
import com.roadscanner.bookingservice.testsupport.fakes.StubProviderIntegrationClient;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GetSeatSelectionViewServiceTest {

    private final StubInventoryClient inventoryClient = new StubInventoryClient();
    private final StubProviderIntegrationClient providerIntegrationClient = new StubProviderIntegrationClient();
    private final GetSeatSelectionViewService service =
            new GetSeatSelectionViewService(inventoryClient, providerIntegrationClient);

    @Test
    void composesStaticLayoutWithLiveStatus() {
        TripId tripId = new TripId(UUID.randomUUID());
        inventoryClient.seatLayoutResult = id -> Optional.of(new InventoryClient.SeatLayoutView(
                List.of(new InventoryClient.SeatShape("L1", "LOWER", "SLEEPER", false, 1))));
        inventoryClient.providerMappingResult = id -> Optional.of(
                new InventoryClient.ProviderMappingView(new ProviderType("MOCK"), "MOCK-TRIP-1"));
        providerIntegrationClient.seatMapResult = () -> new ProviderIntegrationClient.SeatMapView(List.of(
                new ProviderIntegrationClient.SeatStatusView("L1", "LOWER", "SLEEPER", "AVAILABLE",
                        BigDecimal.valueOf(500), "INR")));

        GetSeatSelectionView.Result result = service.get(new GetSeatSelectionView.Command(tripId));

        assertThat(result.seats()).hasSize(1);
        assertThat(result.seats().get(0).seatNumber()).isEqualTo("L1");
        assertThat(result.seats().get(0).status()).isEqualTo("AVAILABLE");
    }

    @Test
    void seatWithNoLiveMatchIsMarkedUnknown() {
        TripId tripId = new TripId(UUID.randomUUID());
        inventoryClient.seatLayoutResult = id -> Optional.of(new InventoryClient.SeatLayoutView(
                List.of(new InventoryClient.SeatShape("L1", "LOWER", "SLEEPER", false, 1))));
        inventoryClient.providerMappingResult = id -> Optional.of(
                new InventoryClient.ProviderMappingView(new ProviderType("MOCK"), "MOCK-TRIP-1"));
        providerIntegrationClient.seatMapResult = () -> new ProviderIntegrationClient.SeatMapView(List.of());

        GetSeatSelectionView.Result result = service.get(new GetSeatSelectionView.Command(tripId));

        assertThat(result.seats().get(0).status()).isEqualTo("UNKNOWN");
    }

    @Test
    void failsWhenTripHasNoProviderMapping() {
        TripId tripId = new TripId(UUID.randomUUID());
        inventoryClient.seatLayoutResult = id -> Optional.of(new InventoryClient.SeatLayoutView(List.of()));
        inventoryClient.providerMappingResult = id -> Optional.empty();

        assertThatThrownBy(() -> service.get(new GetSeatSelectionView.Command(tripId)))
                .isInstanceOf(TripNotBookableException.class);
    }
}
