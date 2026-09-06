package de.mkysarte.bewerbungsmanager.session;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sichert die Masterpasswort-PDF ab — insbesondere die Zeichenkodierung.
 *
 * <p>Die PDF wird von Hand zusammengesetzt, ohne Bibliothek. Deshalb ist sie empfindlich:
 * Ohne {@code /WinAnsiEncoding} an der Schrift und ohne passenden Zeichensatz beim Schreiben
 * würde aus einem „ü" im Betrachter „Ã¼". Genau das ist beim Umstellen auf echte Umlaute
 * beinahe passiert; dieser Test fängt es.
 */
class MasterPasswordPdfServiceTest {

    private MasterPasswordPdfService service;

    @BeforeEach
    void setUp() {
        service = new MasterPasswordPdfService();
    }

    @Test
    void masterpasswortHatVierBloeckeZuFuenfZeichen() {
        String passwort = service.generateMasterPassword();

        String[] bloecke = passwort.split("-");
        assertEquals(4, bloecke.length, passwort);
        for (String block : bloecke) {
            assertEquals(5, block.length(), passwort);
        }
    }

    @Test
    void jedesMasterpasswortIstAnders() {
        assertFalse(service.generateMasterPassword().equals(service.generateMasterPassword()));
    }

    /** Leicht verwechselbare Zeichen sind ausgeschlossen, damit man es abtippen kann. */
    @Test
    void masterpasswortMeidetVerwechselbareZeichen() {
        for (int i = 0; i < 50; i++) {
            String passwort = service.generateMasterPassword();
            assertFalse(passwort.matches(".*[IO01].*"),
                    "Verwechselbares Zeichen in: " + passwort);
        }
    }

    @Test
    void pdfIstLesbarUndEnthaeltDieZugangsdaten() throws IOException {
        byte[] pdf = service.createPdf("testnutzer", "ABCDE-FGHIJ-KLMNO-PQRST");

        try (PDDocument document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document);

            assertTrue(text.contains("testnutzer"), text);
            assertTrue(text.contains("ABCDE-FGHIJ-KLMNO-PQRST"), text);
        }
    }

    /**
     * Der eigentliche Punkt: Die Umlaute müssen im Betrachter als Umlaute ankommen.
     * Bei falscher Kodierung stünde dort „Ã¼" statt „ü".
     */
    @Test
    void umlauteWerdenKorrektDargestellt() throws IOException {
        byte[] pdf = service.createPdf("testnutzer", "ABCDE-FGHIJ-KLMNO-PQRST");

        try (PDDocument document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document);

            assertTrue(text.contains("für"), "Umlaut nicht korrekt dargestellt:\n" + text);
            assertTrue(text.contains("müssen"), "Umlaut nicht korrekt dargestellt:\n" + text);
            assertTrue(text.contains("verschlüsselt"), "Umlaut nicht korrekt dargestellt:\n" + text);
            assertFalse(text.contains("Ã"), "Doppelt kodierte Umlaute im Text:\n" + text);
        }
    }

    /**
     * Die Querverweistabelle enthält Byte-Abstände. Werden sie mit einem anderen
     * Zeichensatz berechnet als die Ausgabe, verschieben Umlaute alle Verweise und die
     * Datei wird unlesbar — deshalb hier ausdrücklich geprüft.
     */
    @Test
    void byteabstaendeStimmenTrotzUmlaute() throws IOException {
        byte[] pdf = service.createPdf("Müller", "ABCDE-FGHIJ-KLMNO-PQRST");
        String roh = new String(pdf, StandardCharsets.ISO_8859_1);

        int startxref = roh.lastIndexOf("startxref");
        int offset = Integer.parseInt(roh.substring(startxref + "startxref".length()).trim()
                .split("\\s+")[0]);

        assertTrue(roh.startsWith("xref", offset),
                "Der Verweis auf die Querverweistabelle zeigt ins Leere (Offset " + offset + ")");

        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertTrue(new PDFTextStripper().getText(document).contains("Müller"));
        }
    }
}
