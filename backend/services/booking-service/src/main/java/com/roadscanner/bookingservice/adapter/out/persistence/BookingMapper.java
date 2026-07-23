package com.roadscanner.bookingservice.adapter.out.persistence;

import com.roadscanner.bookingservice.domain.model.Booking;
import com.roadscanner.bookingservice.domain.model.BookingId;
import com.roadscanner.bookingservice.domain.model.BookingStatus;
import com.roadscanner.bookingservice.domain.model.CancellationReason;
import com.roadscanner.bookingservice.domain.model.Fare;
import com.roadscanner.bookingservice.domain.model.Passenger;
import com.roadscanner.bookingservice.domain.model.ProviderType;
import com.roadscanner.bookingservice.domain.model.Ticket;
import com.roadscanner.bookingservice.domain.model.TripId;

import java.util.Currency;
import java.util.List;

/** The only class in this package that sees both {@code domain.model} and {@link BookingJpaEntity} —
 * matching {@code inventory-service}'s {@code TripMapper} convention exactly. */
final class BookingMapper {

    Booking toDomain(BookingJpaEntity entity) {
        List<Passenger> passengers = entity.getPassengers().stream()
                .map(p -> new Passenger(p.getFullName(), p.getAge(), p.getGender(), p.getSeatNumber()))
                .toList();
        Ticket ticket = entity.getTicketProviderTicketId() == null ? null
                : new Ticket(entity.getTicketProviderTicketId(), entity.getTicketFormat(), entity.getTicketContent(),
                        entity.getTicketIssuedAt());
        return Booking.reconstitute(
                new BookingId(entity.getId()),
                entity.getTravelerId(),
                new TripId(entity.getTripId()),
                entity.getTripDepartureTime(),
                new ProviderType(entity.getProviderType()),
                entity.getProviderTripId(),
                entity.getProviderBlockReference(),
                entity.getHoldExpiresAt(),
                entity.getProviderBookingReference(),
                passengers,
                new Fare(entity.getFareAmount(), Currency.getInstance(entity.getFareCurrency())),
                BookingStatus.valueOf(entity.getStatus()),
                entity.getCancellationReason() == null ? null : CancellationReason.valueOf(entity.getCancellationReason()),
                entity.isSupportFlagged(),
                entity.getPaymentReference(),
                ticket,
                entity.getCreatedAt(),
                entity.getConfirmedAt(),
                entity.getCancelledAt(),
                entity.getCompletedAt()
        );
    }

    BookingJpaEntity toNewEntity(Booking booking) {
        return new BookingJpaEntity(
                booking.id().value(),
                booking.travelerId(),
                booking.tripId().value(),
                booking.tripDepartureTime(),
                booking.providerType().code(),
                booking.providerTripId(),
                booking.providerBlockReference(),
                booking.holdExpiresAt(),
                booking.providerBookingReference().orElse(null),
                toEmbeddables(booking.passengers()),
                booking.fare().amount(),
                booking.fare().currency().getCurrencyCode(),
                booking.status().name(),
                booking.cancellationReason().map(Enum::name).orElse(null),
                booking.supportFlagged(),
                booking.paymentReference().orElse(null),
                booking.ticket().map(Ticket::providerTicketId).orElse(null),
                booking.ticket().map(Ticket::format).orElse(null),
                booking.ticket().map(Ticket::content).orElse(null),
                booking.ticket().map(Ticket::issuedAt).orElse(null),
                booking.createdAt(),
                booking.confirmedAt().orElse(null),
                booking.cancelledAt().orElse(null),
                booking.completedAt().orElse(null)
        );
    }

    void applyTo(BookingJpaEntity entity, Booking booking) {
        entity.applyMutableState(
                booking.providerBookingReference().orElse(null),
                booking.status().name(),
                booking.cancellationReason().map(Enum::name).orElse(null),
                booking.supportFlagged(),
                booking.paymentReference().orElse(null),
                booking.ticket().map(Ticket::providerTicketId).orElse(null),
                booking.ticket().map(Ticket::format).orElse(null),
                booking.ticket().map(Ticket::content).orElse(null),
                booking.ticket().map(Ticket::issuedAt).orElse(null),
                booking.confirmedAt().orElse(null),
                booking.cancelledAt().orElse(null),
                booking.completedAt().orElse(null)
        );
    }

    private List<PassengerEmbeddable> toEmbeddables(List<Passenger> passengers) {
        return passengers.stream()
                .map(p -> new PassengerEmbeddable(p.fullName(), p.age(), p.gender(), p.seatNumber()))
                .toList();
    }
}
