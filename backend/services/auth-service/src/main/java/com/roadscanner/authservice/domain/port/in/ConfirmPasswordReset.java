package com.roadscanner.authservice.domain.port.in;

import com.roadscanner.authservice.domain.model.PasswordResetRequest;
import com.roadscanner.authservice.domain.model.UserId;

import java.time.Instant;
import java.util.Objects;

/**
 * Completes account recovery — "Confirm Password Reset". The command carries the already-fetched
 * {@link PasswordResetRequest} (resolved by the application layer via
 * {@link com.roadscanner.authservice.domain.port.out.PasswordResetRepository} from the presented
 * token's hash); the implementation delegates single-use/expiry enforcement to
 * {@code PasswordResetRequest.use(...)} and complexity validation to
 * {@link com.roadscanner.authservice.domain.service.PasswordComplexityPolicy} before hashing
 * and calling {@code Credential.changePassword(...)}.
 */
public interface ConfirmPasswordReset {

    ConfirmPasswordResetResult confirm(ConfirmPasswordResetCommand command);

    record ConfirmPasswordResetCommand(PasswordResetRequest request, String newRawPassword, Instant now) {
        public ConfirmPasswordResetCommand {
            Objects.requireNonNull(request, "request must not be null");
            Objects.requireNonNull(newRawPassword, "newRawPassword must not be null");
            Objects.requireNonNull(now, "now must not be null");
        }
    }

    record ConfirmPasswordResetResult(UserId userId) {
        public ConfirmPasswordResetResult {
            Objects.requireNonNull(userId, "userId must not be null");
        }
    }
}
