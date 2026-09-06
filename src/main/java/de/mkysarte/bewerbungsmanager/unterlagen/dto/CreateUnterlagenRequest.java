package de.mkysarte.bewerbungsmanager.unterlagen.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateUnterlagenRequest(
        @NotNull @Positive Long userId,
        @Size(max = 10485760) byte[] lebenslaufFile,
        @Size(max = 10485760) byte[] zeugnisseFile,
        @Size(max = 10485760) byte[] zertifikateFile
) {
}
