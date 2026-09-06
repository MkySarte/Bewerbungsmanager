package de.mkysarte.bewerbungsmanager.bewerbungscontainer.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateBewerbungscontainerRequest(
        @NotNull @Positive Long userId
) {
}
