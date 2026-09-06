package de.mkysarte.bewerbungsmanager.document.dto;

import de.mkysarte.bewerbungsmanager.document.entity.DocumentScope;
import de.mkysarte.bewerbungsmanager.document.entity.DocumentType;

import java.time.LocalDateTime;

public record DocumentMetaResponse(
        Long documentId,
        String fileName,
        String contentType,
        Long sizeBytes,
        DocumentType type,
        DocumentScope scope,
        Long applicationId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
