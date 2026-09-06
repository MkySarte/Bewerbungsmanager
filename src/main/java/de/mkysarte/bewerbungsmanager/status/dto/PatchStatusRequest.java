package de.mkysarte.bewerbungsmanager.status.dto;

import jakarta.validation.constraints.Size;

public record PatchStatusRequest(
        @Size(max = 100) String titel,
        @Size(max = 2000) String beschreibung
) {
}
