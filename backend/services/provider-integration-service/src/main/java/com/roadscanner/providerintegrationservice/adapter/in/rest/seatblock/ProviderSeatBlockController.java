package com.roadscanner.providerintegrationservice.adapter.in.rest.seatblock;

import com.roadscanner.providerintegrationservice.domain.model.ProviderSessionId;
import com.roadscanner.providerintegrationservice.domain.model.SeatNumber;
import com.roadscanner.providerintegrationservice.domain.port.in.BlockSeat;
import com.roadscanner.providerintegrationservice.domain.port.in.ReleaseSeat;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Internal-only — places and releases temporary seat holds with the provider. */
@RestController
@RequestMapping("/internal/api/v1/providers/{providerType}/sessions/{sessionId}")
@Tag(name = "Provider Seat Blocks", description = "Block and release seats")
class ProviderSeatBlockController {

    private final BlockSeat blockSeat;
    private final ReleaseSeat releaseSeat;

    ProviderSeatBlockController(BlockSeat blockSeat, ReleaseSeat releaseSeat) {
        this.blockSeat = blockSeat;
        this.releaseSeat = releaseSeat;
    }

    @PostMapping("/trips/{providerTripId}/seat-blocks")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Block seats", description = "Places a temporary hold on one or more seats with the provider.")
    SeatReservationResponse block(@PathVariable String providerType, @PathVariable UUID sessionId,
                                   @PathVariable String providerTripId, @Valid @RequestBody BlockSeatRequest request) {
        BlockSeat.Result result = blockSeat.block(new BlockSeat.Command(new ProviderSessionId(sessionId), providerTripId,
                request.seatNumbers().stream().map(SeatNumber::new).toList()));
        return SeatReservationResponse.from(result.reservation());
    }

    @DeleteMapping("/seat-blocks/{providerBlockReference}")
    @Operation(summary = "Release a seat block", description = "Idempotent — releasing an already-released block is a no-op, not an error.")
    ReleaseSeatResponse release(@PathVariable String providerType, @PathVariable UUID sessionId,
                                 @PathVariable String providerBlockReference) {
        ReleaseSeat.Result result = releaseSeat.release(
                new ReleaseSeat.Command(new ProviderSessionId(sessionId), providerBlockReference));
        return new ReleaseSeatResponse(result.released());
    }
}
