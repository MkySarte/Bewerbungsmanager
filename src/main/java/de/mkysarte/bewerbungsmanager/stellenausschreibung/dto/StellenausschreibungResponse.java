package de.mkysarte.bewerbungsmanager.stellenausschreibung.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record StellenausschreibungResponse(
        Long stellenausschreibungId,
        Long firmaId,
        String titel,
        String url,
        LocalDate veroeffentlichtAm,
        LocalDate gefundenAm,
        String ort,
        String remoteAnteil,
        String beschreibung,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
