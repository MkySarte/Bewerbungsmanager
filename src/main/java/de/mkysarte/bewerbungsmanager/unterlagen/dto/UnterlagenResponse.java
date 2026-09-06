package de.mkysarte.bewerbungsmanager.unterlagen.dto;

import java.time.LocalDateTime;

public record UnterlagenResponse(
        Long unterlagenId,
        Long userId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
