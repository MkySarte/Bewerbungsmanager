package de.mkysarte.bewerbungsmanager.document.service;

public record DocumentDownload(
        String fileName,
        String contentType,
        byte[] content
) {
}
