package com.roadscanner.bookingservice.testsupport.fakes;

import com.roadscanner.bookingservice.domain.model.BookingId;
import com.roadscanner.bookingservice.domain.port.out.RefundRequestPort;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public final class RecordingRefundRequestPort implements RefundRequestPort {

    public record Requested(BookingId bookingId, String paymentReference, BigDecimal amount) {
    }

    private final List<Requested> requests = new ArrayList<>();

    @Override
    public void requestRefund(BookingId bookingId, String paymentReference, BigDecimal amount) {
        requests.add(new Requested(bookingId, paymentReference, amount));
    }

    public List<Requested> requests() {
        return List.copyOf(requests);
    }
}
