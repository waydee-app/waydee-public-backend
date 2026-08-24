package com.waydee.identity.api.dto;

import com.waydee.identity.api.dto.FollowDtos.UserSummary;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class BlockDtos {

    private BlockDtos() {
    }

    public record BlockedUserRow(UserSummary user, String reason, Instant blockedAt) {
    }

    public record BlockRequest(@Size(max = 200, message = "Not en fazla 200 karakter olabilir") String reason) {
    }
}
