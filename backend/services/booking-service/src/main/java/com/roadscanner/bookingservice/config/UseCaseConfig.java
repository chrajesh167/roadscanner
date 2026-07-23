package com.roadscanner.bookingservice.config;

import com.roadscanner.bookingservice.application.usecase.booking.CreateBookingService;
import com.roadscanner.bookingservice.application.usecase.booking.GetBookingService;
import com.roadscanner.bookingservice.application.usecase.booking.ListBookingHistoryService;
import com.roadscanner.bookingservice.application.usecase.booking.ListTripBookingsService;
import com.roadscanner.bookingservice.application.usecase.cancellation.CancelBookingService;
import com.roadscanner.bookingservice.application.usecase.hold.GetSeatSelectionViewService;
import com.roadscanner.bookingservice.application.usecase.hold.HoldSeatsService;
import com.roadscanner.bookingservice.application.usecase.hold.ReleaseHoldService;
import com.roadscanner.bookingservice.application.usecase.payment.HandlePaymentCompletedService;
import com.roadscanner.bookingservice.application.usecase.payment.HandlePaymentFailedService;
import com.roadscanner.bookingservice.application.usecase.payment.HandlePaymentTimedOutService;
import com.roadscanner.bookingservice.application.usecase.scheduled.CompleteBookingService;
import com.roadscanner.bookingservice.application.usecase.scheduled.SweepStaleHoldsService;
import com.roadscanner.bookingservice.application.usecase.ticket.GetTicketService;
import com.roadscanner.bookingservice.application.usecase.trip.HandleSeatReleasedService;
import com.roadscanner.bookingservice.application.usecase.trip.HandleTripCancelledService;
import com.roadscanner.bookingservice.application.usecase.verification.VerifyBookingService;
import com.roadscanner.bookingservice.domain.port.in.CancelBooking;
import com.roadscanner.bookingservice.domain.port.in.CompleteBooking;
import com.roadscanner.bookingservice.domain.port.in.CreateBooking;
import com.roadscanner.bookingservice.domain.port.in.GetBooking;
import com.roadscanner.bookingservice.domain.port.in.GetSeatSelectionView;
import com.roadscanner.bookingservice.domain.port.in.GetTicket;
import com.roadscanner.bookingservice.domain.port.in.HandlePaymentCompleted;
import com.roadscanner.bookingservice.domain.port.in.HandlePaymentFailed;
import com.roadscanner.bookingservice.domain.port.in.HandlePaymentTimedOut;
import com.roadscanner.bookingservice.domain.port.in.HandleSeatReleased;
import com.roadscanner.bookingservice.domain.port.in.HandleTripCancelled;
import com.roadscanner.bookingservice.domain.port.in.HoldSeats;
import com.roadscanner.bookingservice.domain.port.in.ListBookingHistory;
import com.roadscanner.bookingservice.domain.port.in.ListTripBookings;
import com.roadscanner.bookingservice.domain.port.in.ReleaseHold;
import com.roadscanner.bookingservice.domain.port.in.SweepStaleHolds;
import com.roadscanner.bookingservice.domain.port.in.VerifyBooking;
import com.roadscanner.bookingservice.domain.port.out.BookingEventPublisher;
import com.roadscanner.bookingservice.domain.port.out.BookingRepository;
import com.roadscanner.bookingservice.domain.port.out.InventoryClient;
import com.roadscanner.bookingservice.domain.port.out.OperatorCancellationPolicyClient;
import com.roadscanner.bookingservice.domain.port.out.OperatorTripOwnershipVerifier;
import com.roadscanner.bookingservice.domain.port.out.ProviderIntegrationClient;
import com.roadscanner.bookingservice.domain.port.out.RefundRequestPort;
import com.roadscanner.bookingservice.domain.port.out.SeatHoldRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Explicit bean wiring for every application-layer use case — matching every other service in
 * this codebase's identical {@code UseCaseConfig} convention: plain constructors, no Spring
 * stereotype annotations on the application classes themselves. */
@Configuration
public class UseCaseConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public GetSeatSelectionView getSeatSelectionView(InventoryClient inventoryClient,
                                                       ProviderIntegrationClient providerIntegrationClient) {
        return new GetSeatSelectionViewService(inventoryClient, providerIntegrationClient);
    }

    @Bean
    public HoldSeats holdSeats(InventoryClient inventoryClient, ProviderIntegrationClient providerIntegrationClient,
                                SeatHoldRepository seatHoldRepository, Clock clock) {
        return new HoldSeatsService(inventoryClient, providerIntegrationClient, seatHoldRepository, clock);
    }

    @Bean
    public ReleaseHold releaseHold(SeatHoldRepository seatHoldRepository,
                                    ProviderIntegrationClient providerIntegrationClient) {
        return new ReleaseHoldService(seatHoldRepository, providerIntegrationClient);
    }

    @Bean
    public CreateBooking createBooking(SeatHoldRepository seatHoldRepository, BookingRepository bookingRepository,
                                        BookingEventPublisher eventPublisher, Clock clock) {
        return new CreateBookingService(seatHoldRepository, bookingRepository, eventPublisher, clock);
    }

    @Bean
    public GetBooking getBooking(BookingRepository bookingRepository,
                                  OperatorTripOwnershipVerifier ownershipVerifier) {
        return new GetBookingService(bookingRepository, ownershipVerifier);
    }

    @Bean
    public ListBookingHistory listBookingHistory(BookingRepository bookingRepository) {
        return new ListBookingHistoryService(bookingRepository);
    }

    @Bean
    public ListTripBookings listTripBookings(BookingRepository bookingRepository,
                                              OperatorTripOwnershipVerifier ownershipVerifier) {
        return new ListTripBookingsService(bookingRepository, ownershipVerifier);
    }

    @Bean
    public CancelBooking cancelBooking(BookingRepository bookingRepository,
                                        ProviderIntegrationClient providerIntegrationClient,
                                        OperatorCancellationPolicyClient policyClient,
                                        RefundRequestPort refundRequestPort, BookingEventPublisher eventPublisher,
                                        Clock clock) {
        return new CancelBookingService(bookingRepository, providerIntegrationClient, policyClient,
                refundRequestPort, eventPublisher, clock);
    }

    @Bean
    public GetTicket getTicket(BookingRepository bookingRepository) {
        return new GetTicketService(bookingRepository);
    }

    @Bean
    public VerifyBooking verifyBooking(BookingRepository bookingRepository) {
        return new VerifyBookingService(bookingRepository);
    }

    @Bean
    public HandlePaymentCompleted handlePaymentCompleted(BookingRepository bookingRepository,
                                                           ProviderIntegrationClient providerIntegrationClient,
                                                           RefundRequestPort refundRequestPort,
                                                           BookingEventPublisher eventPublisher) {
        return new HandlePaymentCompletedService(bookingRepository, providerIntegrationClient, refundRequestPort,
                eventPublisher);
    }

    @Bean
    public HandlePaymentFailed handlePaymentFailed(BookingRepository bookingRepository,
                                                     ProviderIntegrationClient providerIntegrationClient,
                                                     BookingEventPublisher eventPublisher) {
        return new HandlePaymentFailedService(bookingRepository, providerIntegrationClient, eventPublisher);
    }

    @Bean
    public HandlePaymentTimedOut handlePaymentTimedOut(BookingRepository bookingRepository,
                                                         ProviderIntegrationClient providerIntegrationClient,
                                                         BookingEventPublisher eventPublisher) {
        return new HandlePaymentTimedOutService(bookingRepository, providerIntegrationClient, eventPublisher);
    }

    @Bean
    public HandleSeatReleased handleSeatReleased(SeatHoldRepository seatHoldRepository,
                                                   BookingRepository bookingRepository,
                                                   BookingEventPublisher eventPublisher) {
        return new HandleSeatReleasedService(seatHoldRepository, bookingRepository, eventPublisher);
    }

    @Bean
    public HandleTripCancelled handleTripCancelled(BookingRepository bookingRepository,
                                                     ProviderIntegrationClient providerIntegrationClient,
                                                     RefundRequestPort refundRequestPort,
                                                     BookingEventPublisher eventPublisher) {
        return new HandleTripCancelledService(bookingRepository, providerIntegrationClient, refundRequestPort,
                eventPublisher);
    }

    @Bean
    public CompleteBooking completeBooking(BookingRepository bookingRepository, Clock clock) {
        return new CompleteBookingService(bookingRepository, clock);
    }

    @Bean
    public SweepStaleHolds sweepStaleHolds(SeatHoldRepository seatHoldRepository, BookingRepository bookingRepository,
                                            ProviderIntegrationClient providerIntegrationClient,
                                            BookingEventPublisher eventPublisher, Clock clock) {
        return new SweepStaleHoldsService(seatHoldRepository, bookingRepository, providerIntegrationClient,
                eventPublisher, clock);
    }
}
