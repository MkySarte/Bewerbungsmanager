package de.mkysarte.bewerbungsmanager.document.service;

import de.mkysarte.bewerbungsmanager.common.exception.AppException;
import de.mkysarte.bewerbungsmanager.document.entity.DocumentEntity;
import de.mkysarte.bewerbungsmanager.document.repository.DocumentRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PdfBundleServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    private PdfBundleService pdfBundleService;

    @BeforeEach
    void setUp() {
        pdfBundleService = new PdfBundleService(documentRepository);
    }

    @Test
    void seitenzahlIstDieSummeDerEinzeldateien() throws IOException {
        stub(1L, "anschreiben.pdf", pdfMitSeiten(1));
        stub(2L, "lebenslauf.pdf", pdfMitSeiten(2));
        stub(3L, "zeugnisse.pdf", pdfMitSeiten(3));

        byte[] bundle = pdfBundleService.merge(Arrays.asList(1L, 2L, 3L));

        try (PDDocument merged = Loader.loadPDF(bundle)) {
            assertEquals(6, merged.getNumberOfPages());
        }
    }

    @Test
    void nichtGesetzteUnterlagenWerdenUebersprungen() throws IOException {
        stub(1L, "anschreiben.pdf", pdfMitSeiten(1));

        // null steht für eine nicht verknuepfte Unterlage - das ist der Normalfall
        byte[] bundle = pdfBundleService.merge(Arrays.asList(1L, null, null, null));

        try (PDDocument merged = Loader.loadPDF(bundle)) {
            assertEquals(1, merged.getNumberOfPages());
        }
    }

    @Test
    void nichtGefundeneDokumenteLassenDenRestUnberuehrt() throws IOException {
        stub(1L, "anschreiben.pdf", pdfMitSeiten(2));
        when(documentRepository.findById(99L)).thenReturn(Optional.empty());

        byte[] bundle = pdfBundleService.merge(Arrays.asList(99L, 1L));

        try (PDDocument merged = Loader.loadPDF(bundle)) {
            assertEquals(2, merged.getNumberOfPages());
        }
    }

    /** Eine Bilddatei darf nicht den ganzen Vorgang scheitern lassen. */
    @Test
    void nichtPdfDokumenteWerdenUebersprungen() throws IOException {
        stub(1L, "foto.png", "kein pdf".getBytes());
        stub(2L, "lebenslauf.pdf", pdfMitSeiten(2));

        byte[] bundle = pdfBundleService.merge(Arrays.asList(1L, 2L));

        try (PDDocument merged = Loader.loadPDF(bundle)) {
            assertEquals(2, merged.getNumberOfPages());
        }
    }

    @Test
    void ohneVerwendbareUnterlagenGibtEsEineVerstaendlicheMeldung() {
        AppException exception = assertThrows(AppException.class,
                () -> pdfBundleService.merge(Arrays.asList(null, null)));

        assertTrue(exception.getMessage().contains("keine PDF-Unterlagen"), exception.getMessage());
    }

    @Test
    void dateinameEnthaeltDieFirmaInKleinschreibung() {
        String name = pdfBundleService.buildFileName("Musterfirma GmbH & Co. KG");

        assertTrue(name.startsWith("bewerbung-musterfirma-gmbh-co-kg-"), name);
        assertTrue(name.endsWith(".pdf"), name);
    }

    @Test
    void dateinameKommtAuchOhneFirmaZurecht() {
        assertTrue(pdfBundleService.buildFileName(null).startsWith("bewerbung-"));
        assertTrue(pdfBundleService.buildFileName("   ").startsWith("bewerbung-"));
    }

    // =====================================================================

    private void stub(Long id, String fileName, byte[] content) {
        DocumentEntity document = new DocumentEntity();
        document.setDocumentId(id);
        document.setFileName(fileName);
        document.setContentType(fileName.endsWith(".pdf") ? "application/pdf" : "image/png");
        document.setContent(content);
        when(documentRepository.findById(id)).thenReturn(Optional.of(document));
    }

    private byte[] pdfMitSeiten(int seiten) throws IOException {
        try (PDDocument document = new PDDocument()) {
            for (int i = 0; i < seiten; i++) {
                document.addPage(new PDPage());
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }
}
