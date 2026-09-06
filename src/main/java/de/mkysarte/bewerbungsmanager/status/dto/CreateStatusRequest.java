package de.mkysarte.bewerbungsmanager.status.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateStatusRequest(
        @NotBlank @Size(max = 100) String titel,
        @Size(max = 2000) String beschreibung
) {
}
