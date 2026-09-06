package de.mkysarte.bewerbungsmanager.adresse.dto;

import java.time.LocalDateTime;

public record AdresseResponse(
        Long adresseId,
        Long firmaId,
        String strasse,
        String hausnummer,
        String plz,
        String ort,
        String land,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
