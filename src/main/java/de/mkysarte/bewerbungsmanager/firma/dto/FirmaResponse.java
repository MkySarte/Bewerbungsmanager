package de.mkysarte.bewerbungsmanager.firma.dto;

import java.time.LocalDateTime;

public record FirmaResponse(
        Long firmaId,
        String name,
        String kontaktPerson,
        String telefon,
        String email,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
