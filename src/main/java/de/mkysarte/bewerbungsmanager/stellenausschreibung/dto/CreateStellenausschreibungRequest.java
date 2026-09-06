package de.mkysarte.bewerbungsmanager.stellenausschreibung.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreateStellenausschreibungRequest(
        @NotNull @Positive Long firmaId,
        @NotBlank @Size(max = 255) String titel,
        String url,
        LocalDate veroeffentlichtAm,
        LocalDate gefundenAm,
        @Size(max = 255) String ort,
        @Size(max = 100) String remoteAnteil,
        String beschreibung
) {
}
