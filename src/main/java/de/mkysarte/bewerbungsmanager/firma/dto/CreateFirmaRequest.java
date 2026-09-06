package de.mkysarte.bewerbungsmanager.firma.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateFirmaRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 255) String kontaktPerson,
        @Pattern(regexp = "^[0-9+()\\-\\s]{3,50}$", message = "Telefon hat ein ungültiges Format")
        @Size(max = 50) String telefon,
        @Email @Size(max = 255) String email
) {
}
