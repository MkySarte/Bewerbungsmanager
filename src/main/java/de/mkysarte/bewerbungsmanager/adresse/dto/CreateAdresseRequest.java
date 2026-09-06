package de.mkysarte.bewerbungsmanager.adresse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateAdresseRequest(
        @NotNull @Positive Long firmaId,
        @NotBlank @Size(max = 255) String strasse,
        @Size(max = 50) String hausnummer,
        @Size(max = 20) String plz,
        @NotBlank @Size(max = 255) String ort,
        @Size(max = 100) String land
) {
}
