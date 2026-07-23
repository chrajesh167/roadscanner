package com.roadscanner.bookingservice.application.usecase.hold;

import com.roadscanner.bookingservice.domain.exception.TripNotBookableException;
import com.roadscanner.bookingservice.domain.model.Fare;
import com.roadscanner.bookingservice.domain.model.SeatHold;
import com.roadscanner.bookingservice.domain.model.SeatHoldId;
import com.roadscanner.bookingservice.domain.port.in.HoldSeats;
import com.roadscanner.bookingservice.domain.port.out.InventoryClient;
import com.roadscanner.bookingservice.domain.port.out.ProviderIntegrationClient;
import com.roadscanner.bookingservice.domain.port.out.SeatHoldRepository;

import java.time.Clock;
import java.util.Currency;

/**
 * Implements {@link HoldSeats}. Re-validates the trip, resolves its {@code ProviderMapping},
 * places the hold with {@code provider-integration-service}, and persists a local
 * {@link SeatHold} capturing everything {@code Create Booking} will need later without a second
 * round-trip (docs/services/booking-service/domain-model.md's "Why This Isn't Itself a `Booking`
 * in a `HOLDING` State").
 */
public class HoldSeatsService implements HoldSeats {

    private final InventoryClient inventoryClient;
    private final ProviderIntegrationClient providerIntegrationClient;
    private final SeatHoldRepository seatHoldRepository;
    private final Clock clock;

    public HoldSeatsService(InventoryClient inventoryClient, ProviderIntegrationClient providerIntegrationClient,
                             SeatHoldRepository seatHoldRepository, Clock clock) {
        this.inventoryClient = inventoryClient;
        this.providerIntegrationClient = providerIntegrationClient;
        this.seatHoldRepository = seatHoldRepository;
        this.clock = clock;
    }

    @Override
    public Result hold(Command command) {
        InventoryClient.TripSnapshot trip = inventoryClient.getTrip(command.tripId())
                .filter(InventoryClient.TripSnapshot::bookable)
                .orElseThrow(() -> new TripNotBookableException(command.tripId(), "trip not found or not bookable"));
        InventoryClient.ProviderMappingView mapping = inventoryClient.getProviderMapping(command.tripId())
                .orElseThrow(() -> new TripNotBookableException(command.tripId(),
                        "no ProviderMapping — this trip cannot currently be booked"));

        ProviderIntegrationClient.Reservation reservation = providerIntegrationClient.blockSeats(
                mapping.providerType(), mapping.providerTripId(), command.seatNumbers());

        SeatHold hold = SeatHold.create(SeatHoldId.generate(), command.travelerId(), command.tripId(),
                trip.departureTime(), mapping.providerType(), mapping.providerTripId(),
                reservation.providerBlockReference(), reservation.seatNumbers(),
                new Fare(trip.fareAmount(), Currency.getInstance(trip.fareCurrency())), reservation.expiresAt(),
                clock.instant());
        seatHoldRepository.save(hold);

        return new Result(hold.id(), hold.seatNumbers(), hold.expiresAt());
    }
}
