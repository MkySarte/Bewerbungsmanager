package de.mkysarte.bewerbungsmanager.bewerbungscontainer.dto;

import java.time.LocalDateTime;

public record BewerbungscontainerResponse(
        Long bewerbungscontainerId,
        Long userId,
        String name,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
