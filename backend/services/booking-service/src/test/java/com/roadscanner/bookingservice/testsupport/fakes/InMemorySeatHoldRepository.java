package com.roadscanner.bookingservice.testsupport.fakes;

import com.roadscanner.bookingservice.domain.model.SeatHold;
import com.roadscanner.bookingservice.domain.model.SeatHoldId;
import com.roadscanner.bookingservice.domain.port.out.SeatHoldRepository;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class InMemorySeatHoldRepository implements SeatHoldRepository {

    private final Map<UUID, SeatHold> holds = new LinkedHashMap<>();

    @Override
    public SeatHold save(SeatHold seatHold) {
        holds.put(seatHold.id().value(), seatHold);
        return seatHold;
    }

    @Override
    public Optional<SeatHold> findById(SeatHoldId id) {
        return Optional.ofNullable(holds.get(id.value()));
    }

    @Override
    public Optional<SeatHold> findByProviderBlockReference(String providerBlockReference) {
        return holds.values().stream()
                .filter(h -> h.providerBlockReference().equals(providerBlockReference))
                .findFirst();
    }

    @Override
    public void deleteById(SeatHoldId id) {
        holds.remove(id.value());
    }

    @Override
    public List<SeatHold> findAllExpiredBefore(Instant cutoff) {
        return holds.values().stream().filter(h -> h.expiresAt().isBefore(cutoff)).toList();
    }
}
