package de.mkysarte.bewerbungsmanager.status.dto;

import java.time.LocalDateTime;

public record StatusResponse(
        Long statusId,
        String titel,
        String beschreibung,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
