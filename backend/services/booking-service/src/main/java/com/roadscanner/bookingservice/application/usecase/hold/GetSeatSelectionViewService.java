package com.roadscanner.bookingservice.application.usecase.hold;

import com.roadscanner.bookingservice.domain.exception.TripNotBookableException;
import com.roadscanner.bookingservice.domain.model.TripId;
import com.roadscanner.bookingservice.domain.port.in.GetSeatSelectionView;
import com.roadscanner.bookingservice.domain.port.out.InventoryClient;
import com.roadscanner.bookingservice.domain.port.out.ProviderIntegrationClient;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Implements {@link GetSeatSelectionView} — see
 * docs/services/inventory-service/sequence-diagrams.md flow 4, reproduced as this service's own
 * "Seat Selection View" flow in docs/services/booking-service/sequence-diagrams.md. */
public class GetSeatSelectionViewService implements GetSeatSelectionView {

    private final InventoryClient inventoryClient;
    private final ProviderIntegrationClient providerIntegrationClient;

    public GetSeatSelectionViewService(InventoryClient inventoryClient,
                                        ProviderIntegrationClient providerIntegrationClient) {
        this.inventoryClient = inventoryClient;
        this.providerIntegrationClient = providerIntegrationClient;
    }

    @Override
    public Result get(Command command) {
        TripId tripId = command.tripId();
        InventoryClient.SeatLayoutView layout = inventoryClient.getSeatLayout(tripId)
                .orElseThrow(() -> new TripNotBookableException(tripId, "no seat layout"));
        InventoryClient.ProviderMappingView mapping = inventoryClient.getProviderMapping(tripId)
                .orElseThrow(() -> new TripNotBookableException(tripId,
                        "no ProviderMapping — this trip cannot currently be booked"));

        ProviderIntegrationClient.SeatMapView liveMap =
                providerIntegrationClient.getSeatMap(mapping.providerType(), mapping.providerTripId());
        Map<String, ProviderIntegrationClient.SeatStatusView> liveBySeatNumber = liveMap.seats().stream()
                .collect(java.util.stream.Collectors.toMap(ProviderIntegrationClient.SeatStatusView::seatNumber,
                        Function.identity(), (a, b) -> a));

        List<SeatView> seats = layout.seats().stream()
                .map(shape -> {
                    ProviderIntegrationClient.SeatStatusView live = liveBySeatNumber.get(shape.seatNumber());
                    return new SeatView(shape.seatNumber(), shape.deck(), shape.seatType(),
                            shape.wheelchairAccessible(), shape.position(),
                            live != null ? live.status() : "UNKNOWN",
                            live != null ? live.priceAmount() : null,
                            live != null ? live.priceCurrency() : null);
                })
                .toList();
        return new Result(seats);
    }
}
