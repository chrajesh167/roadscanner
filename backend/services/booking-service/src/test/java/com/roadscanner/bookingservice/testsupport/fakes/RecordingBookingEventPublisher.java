package com.roadscanner.bookingservice.testsupport.fakes;

import com.roadscanner.bookingservice.domain.model.Booking;
import com.roadscanner.bookingservice.domain.port.out.BookingEventPublisher;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class RecordingBookingEventPublisher implements BookingEventPublisher {

    public record Published(String eventType, Booking booking) {
    }

    private final List<Published> events = new ArrayList<>();

    @Override
    public void publishBookingCreated(Booking booking, Instant occurredAt) {
        events.add(new Published("BookingCreated", booking));
    }

    @Override
    public void publishBookingConfirmed(Booking booking, Instant occurredAt) {
        events.add(new Published("BookingConfirmed", booking));
    }

    @Override
    public void publishBookingCancelled(Booking booking, Instant occurredAt) {
        events.add(new Published("BookingCancelled", booking));
    }

    public List<Published> events() {
        return List.copyOf(events);
    }
}
