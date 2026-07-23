package com.roadscanner.bookingservice.adapter.in.rest.booking;

import com.roadscanner.bookingservice.adapter.in.rest.RequesterContextResolver;
import com.roadscanner.bookingservice.domain.model.BookingId;
import com.roadscanner.bookingservice.domain.model.RequesterContext;
import com.roadscanner.bookingservice.domain.model.Role;
import com.roadscanner.bookingservice.domain.model.SeatHoldId;
import com.roadscanner.bookingservice.domain.model.TripId;
import com.roadscanner.bookingservice.domain.port.in.CancelBooking;
import com.roadscanner.bookingservice.domain.port.in.CreateBooking;
import com.roadscanner.bookingservice.domain.port.in.GetBooking;
import com.roadscanner.bookingservice.domain.port.in.ListBookingHistory;
import com.roadscanner.bookingservice.domain.port.in.ListTripBookings;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import java.util.UUID;

/** {@code Create Booking} / {@code Get Booking} / {@code List Booking History} /
 * {@code List Trip Bookings} / {@code Cancel Booking} —
 * docs/services/booking-service/api-summary.md. */
@RestController
@RequestMapping("/api/v1/bookings")
@Tag(name = "Bookings", description = "Booking creation, retrieval, history, and cancellation")
class BookingController {

    private final CreateBooking createBooking;
    private final GetBooking getBooking;
    private final ListBookingHistory listBookingHistory;
    private final ListTripBookings listTripBookings;
    private final CancelBooking cancelBooking;

    BookingController(CreateBooking createBooking, GetBooking getBooking, ListBookingHistory listBookingHistory,
                       ListTripBookings listTripBookings, CancelBooking cancelBooking) {
        this.createBooking = createBooking;
        this.getBooking = getBooking;
        this.listBookingHistory = listBookingHistory;
        this.listTripBookings = listTripBookings;
        this.cancelBooking = cancelBooking;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a booking", description = "Creates a PENDING_PAYMENT booking against a held reference.")
    CreateBookingResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateBookingRequest request) {
        RequesterContext requester = RequesterContextResolver.from(jwt);
        if (requester.role() != Role.TRAVELER) {
            throw new AccessDeniedException("Only travelers may create a booking");
        }
        CreateBooking.Result result = createBooking.create(new CreateBooking.Command(requester.requesterId(),
                new SeatHoldId(request.seatHoldId()),
                request.passengers().stream().map(PassengerRequest::toCommand).toList()));
        return CreateBookingResponse.from(result);
    }

    @GetMapping("/{bookingId}")
    @Operation(summary = "Get a booking", description = "Ownership-checked — a denied request reads as 404, not 403.")
    BookingResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID bookingId) {
        RequesterContext requester = RequesterContextResolver.from(jwt);
        GetBooking.Result result = getBooking.get(new GetBooking.Command(new BookingId(bookingId), requester));
        return BookingResponse.from(result.booking());
    }

    @GetMapping
    @Operation(summary = "List bookings",
            description = "TRAVELER: own history. OPERATOR/ADMIN/SUPPORT with tripId: that trip's bookings. "
                    + "ADMIN/SUPPORT with onBehalfOfTravelerId: that traveler's history.")
    BookingsResponse list(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) UUID tripId,
                           @RequestParam(required = false) UUID onBehalfOfTravelerId) {
        RequesterContext requester = RequesterContextResolver.from(jwt);
        if (tripId != null) {
            ListTripBookings.Result result = listTripBookings.list(
                    new ListTripBookings.Command(new TripId(tripId), requester));
            return BookingsResponse.from(result.bookings());
        }
        ListBookingHistory.Result result = listBookingHistory.list(
                new ListBookingHistory.Command(requester, Optional.ofNullable(onBehalfOfTravelerId)));
        return BookingsResponse.from(result.bookings());
    }

    @PostMapping("/{bookingId}/cancel")
    @Operation(summary = "Cancel a booking", description = "Idempotent — cancelling an already-cancelled booking is a no-op.")
    CancelBookingResponse cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID bookingId) {
        RequesterContext requester = RequesterContextResolver.from(jwt);
        CancelBooking.Result result = cancelBooking.cancel(
                new CancelBooking.Command(new BookingId(bookingId), requester));
        return new CancelBookingResponse(result.status().name());
    }
}
