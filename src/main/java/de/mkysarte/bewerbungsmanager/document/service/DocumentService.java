package de.mkysarte.bewerbungsmanager.document.service;

import de.mkysarte.bewerbungsmanager.bewerbungseintrag.repository.BewerbungseintragRepository;
import de.mkysarte.bewerbungsmanager.common.exception.AppException;
import de.mkysarte.bewerbungsmanager.document.dto.DocumentMetaResponse;
import de.mkysarte.bewerbungsmanager.document.entity.DocumentEntity;
import de.mkysarte.bewerbungsmanager.document.entity.DocumentScope;
import de.mkysarte.bewerbungsmanager.document.entity.DocumentType;
import de.mkysarte.bewerbungsmanager.document.repository.DocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DocumentService {

    private static final long MAX_FILE_SIZE_BYTES = 10L * 1024L * 1024L;
    private static final String PDF_CONTENT_TYPE = "application/pdf";

    private final DocumentRepository documentRepository;
    private final BewerbungseintragRepository bewerbungseintragRepository;

    public DocumentService(
            DocumentRepository documentRepository,
            BewerbungseintragRepository bewerbungseintragRepository
    ) {
        this.documentRepository = documentRepository;
        this.bewerbungseintragRepository = bewerbungseintragRepository;
    }

    /**
     * Dokument hochladen.
     *
     * Signatur angepasst: statt MultipartFile werden Roh-Bytes übergeben,
     * damit JavaFX FileChooser (java.io.File) direkt verwendet werden kann.
     *
     * Verwendung aus JavaFX-Controller:
     *   byte[] bytes = Files.readAllBytes(chosenFile.toPath());
     *   documentService.upload(bytes, chosenFile.getName(), "application/pdf", type, scope, null);
     *
     * Verwendung aus REST-Controller (Profil "web"):
     *   documentService.upload(file.getBytes(), file.getOriginalFilename(), file.getContentType(), type, scope, appId);
     */
    @Transactional
    public DocumentMetaResponse upload(
            byte[] content,
            String fileName,
            String contentType,
            DocumentType type,
            DocumentScope scope,
            Long applicationId
    ) {
        validateFile(content, contentType);
        validateScope(scope, applicationId);

        DocumentEntity entity = new DocumentEntity();
        entity.setFileName(fileName == null || fileName.isBlank() ? "upload.pdf" : fileName);
        entity.setContentType(contentType == null ? PDF_CONTENT_TYPE : contentType);
        entity.setSizeBytes((long) content.length);
        entity.setType(type);
        entity.setScope(scope);
        entity.setApplicationId(applicationId);
        entity.setContent(content);

        DocumentEntity saved = documentRepository.save(entity);
        return toMeta(saved);
    }

    @Transactional(readOnly = true)
    public DocumentDownload download(Long id) {
        DocumentEntity entity = findByIdOrThrow(id);
        return new DocumentDownload(entity.getFileName(), entity.getContentType(), entity.getContent());
    }

    @Transactional(readOnly = true)
    public DocumentMetaResponse getMeta(Long id) {
        return toMeta(findByIdOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<DocumentMetaResponse> list(DocumentScope scope, DocumentType type, Long applicationId) {
        if (scope == null && type == null && applicationId == null) {
            return documentRepository.findAll().stream().map(this::toMeta).toList();
        }
        if (scope != null && type != null && applicationId != null) {
            return documentRepository.findByScopeAndTypeAndApplicationId(scope, type, applicationId).stream().map(this::toMeta).toList();
        }
        if (scope != null && type != null) {
            return documentRepository.findByScopeAndType(scope, type).stream().map(this::toMeta).toList();
        }
        if (scope != null && applicationId != null) {
            return documentRepository.findByScopeAndApplicationId(scope, applicationId).stream().map(this::toMeta).toList();
        }
        if (type != null && applicationId != null) {
            return documentRepository.findByTypeAndApplicationId(type, applicationId).stream().map(this::toMeta).toList();
        }
        if (scope != null) {
            return documentRepository.findByScope(scope).stream().map(this::toMeta).toList();
        }
        if (type != null) {
            return documentRepository.findByType(type).stream().map(this::toMeta).toList();
        }
        return documentRepository.findByApplicationId(applicationId).stream().map(this::toMeta).toList();
    }

    @Transactional
    public void delete(Long id) {
        if (!documentRepository.existsById(id)) {
            throw AppException.notFound("Dokument nicht gefunden");
        }
        documentRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public DocumentEntity findByIdOrThrow(Long id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> AppException.notFound("Dokument nicht gefunden"));
    }

    private void validateFile(byte[] content, String contentType) {
        if (content == null || content.length == 0) {
            throw AppException.badRequest("Datei ist erforderlich");
        }
        if (content.length > MAX_FILE_SIZE_BYTES) {
            throw AppException.badRequest("Datei darf maximal 10 MB groß sein");
        }
        if (!PDF_CONTENT_TYPE.equalsIgnoreCase(contentType)) {
            throw AppException.badRequest("Nur PDF-Dateien sind erlaubt");
        }
    }

    private void validateScope(DocumentScope scope, Long applicationId) {
        if (scope == DocumentScope.APPLICATION) {
            if (applicationId == null) {
                throw AppException.badRequest("applicationId ist bei APPLICATION-Scope erforderlich");
            }
            if (!bewerbungseintragRepository.existsById(applicationId)) {
                throw AppException.notFound("Bewerbungseintrag für applicationId nicht gefunden");
            }
            return;
        }

        if (applicationId != null) {
            throw AppException.conflict("applicationId darf nur bei APPLICATION-Scope gesetzt sein");
        }
    }

    private DocumentMetaResponse toMeta(DocumentEntity entity) {
        return new DocumentMetaResponse(
                entity.getDocumentId(),
                entity.getFileName(),
                entity.getContentType(),
                entity.getSizeBytes(),
                entity.getType(),
                entity.getScope(),
                entity.getApplicationId(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
