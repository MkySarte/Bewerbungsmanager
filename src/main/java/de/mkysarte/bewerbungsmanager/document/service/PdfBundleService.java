package de.mkysarte.bewerbungsmanager.document.service;

import de.mkysarte.bewerbungsmanager.common.exception.AppException;
import de.mkysarte.bewerbungsmanager.document.entity.DocumentEntity;
import de.mkysarte.bewerbungsmanager.document.repository.DocumentRepository;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Führt die Unterlagen einer Bewerbung zu einer einzigen PDF zusammen.
 *
 * <p>Reihenfolge ist <b>Anschreiben, Lebenslauf, Zeugnisse, Zertifikate</b> — die übliche
 * Reihenfolge einer deutschen Bewerbungsmappe. Das Anschreiben gehört nach vorn, weil es das
 * erste Blatt ist, das gelesen wird.
 *
 * <p>Das Bündel wird bei jedem Herunterladen frisch erzeugt und nirgends gespeichert. So kann
 * es nicht veralten, wenn eine der Einzeldateien später ausgetauscht wird.
 *
 * <p>Nutzt PDFBox, das ohnehin schon für die Vorschau eingebunden ist
 * ({@link PdfPreviewService}) — es kommt keine Abhängigkeit dazu.
 */
@Service
public class PdfBundleService {

    private static final Logger log = LoggerFactory.getLogger(PdfBundleService.class);

    private final DocumentRepository documentRepository;

    public PdfBundleService(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    /**
     * Fügt die angegebenen Dokumente in der übergebenen Reihenfolge zusammen.
     * {@code null}-Einträge und nicht gefundene Dokumente werden übersprungen.
     *
     * @throws AppException wenn am Ende keine einzige verwendbare PDF übrig bleibt
     */
    public byte[] merge(List<Long> documentIdsInOrder) {
        List<DocumentEntity> documents = new ArrayList<>();
        for (Long id : documentIdsInOrder) {
            if (id == null) {
                continue;
            }
            documentRepository.findById(id).ifPresentOrElse(
                    documents::add,
                    () -> log.warn("Dokument {} nicht gefunden - wird im Bündel übersprungen", id));
        }

        PDFMergerUtility merger = new PDFMergerUtility();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        merger.setDestinationStream(output);

        int added = 0;
        for (DocumentEntity document : documents) {
            if (!isPdf(document)) {
                // Nur PDFs lassen sich zusammenfuehren. Lieber überspringen und den Rest
                // liefern, als den ganzen Vorgang an einer Bilddatei scheitern zu lassen.
                log.warn("'{}' ist kein PDF ({}) - wird im Bündel übersprungen",
                        document.getFileName(), document.getContentType());
                continue;
            }
            merger.addSource(new RandomAccessReadBuffer(document.getContent()));
            added++;
        }

        if (added == 0) {
            throw AppException.badRequest(
                    "Für diese Bewerbung gibt es keine PDF-Unterlagen, die sich zusammenführen lassen.");
        }

        try {
            merger.mergeDocuments(null);
        } catch (IOException e) {
            log.error("PDF-Bündel konnte nicht erzeugt werden", e);
            throw AppException.internal("Das PDF-Bündel konnte nicht erzeugt werden.");
        }
        return output.toByteArray();
    }

    /**
     * Dateiname für das Bündel, z. B. {@code bewerbung-musterfirma-gmbh-2026-09-06.pdf}.
     */
    public String buildFileName(String firmaName) {
        String firma = firmaName == null || firmaName.isBlank() ? "bewerbung" : firmaName;
        String slug = firma.toLowerCase(Locale.GERMAN)
                .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (slug.isBlank()) {
            slug = "bewerbung";
        }
        return "bewerbung-" + slug + "-" + java.time.LocalDate.now() + ".pdf";
    }

    private boolean isPdf(DocumentEntity document) {
        if (document.getContent() == null || document.getContent().length == 0) {
            return false;
        }
        String contentType = document.getContentType();
        if (contentType != null && contentType.toLowerCase(Locale.ROOT).contains("pdf")) {
            return true;
        }
        String fileName = document.getFileName();
        return fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith(".pdf");
    }
}
