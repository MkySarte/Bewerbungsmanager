package de.mkysarte.bewerbungsmanager.session;

import de.mkysarte.bewerbungsmanager.common.InitialAccess;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

@Service
public class MasterPasswordPdfService {

    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    /**
     * Kodierung des PDF-Inhalts. Passend zu /WinAnsiEncoding, das im Bereich der Umlaute
     * mit Latin-1 uebereinstimmt. Latin-1 ist als einziges davon in jeder JVM garantiert
     * vorhanden - und alle Texte dieser PDF liegen in diesem Bereich.
     */
    private static final java.nio.charset.Charset PDF_ZEICHENSATZ = StandardCharsets.ISO_8859_1;
    private final SecureRandom secureRandom = new SecureRandom();

    public String generateMasterPassword() {
        StringBuilder sb = new StringBuilder();
        for (int block = 0; block < 4; block++) {
            if (block > 0) {
                sb.append('-');
            }
            for (int i = 0; i < 5; i++) {
                sb.append(CHARS.charAt(secureRandom.nextInt(CHARS.length())));
            }
        }
        return sb.toString();
    }

    public byte[] createPdf(String username, String masterPassword) {
        List<String> lines = List.of(
                "Bewerbungsmanager - Masterpasswort",
                "",
                "Benutzer: " + username,
                "Masterpasswort: " + masterPassword,
                "",
                "Wichtige Hinweise:",
                "- Bitte diese PDF sicher speichern oder ausdrucken.",
                "- Das Masterpasswort ist die Sicherheitsreserve für dieses Konto:",
                "  damit lässt sich ein vergessenes Passwort neu setzen.",
                "- Ihre Daten sind verschlüsselt. Gehen Passwort UND Masterpasswort",
                "  verloren, sind sie endgültig nicht mehr lesbar - auch nicht für uns.",
                "- In die Anwendung selbst kommen Sie dann weiterhin über den",
                "  Demo- und Notfallzugang:",
                "  Benutzername: " + InitialAccess.USERNAME,
                "  Passwort: " + InitialAccess.PASSWORD,
                "  Dort können Sie neu anfangen, ohne neu installieren zu müssen."
        );

        StringBuilder content = new StringBuilder();
        content.append("BT\n/F1 18 Tf\n50 760 Td\n");
        boolean first = true;
        for (String line : lines) {
            if (!first) {
                content.append("0 -24 Td\n");
            }
            content.append('(').append(escape(line)).append(") Tj\n");
            first = false;
        }
        content.append("ET");

        String stream = content.toString();
        List<String> objects = new ArrayList<>();
        objects.add("<< /Type /Catalog /Pages 2 0 R >>");
        objects.add("<< /Type /Pages /Count 1 /Kids [3 0 R] >>");
        objects.add("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>");
        // Ohne /Encoding nimmt der Betrachter die eingebaute Kodierung der Schrift, in der
        // Umlaute nicht an den Latin-1-Positionen liegen - aus "ü" wuerde Buchstabensalat.
        objects.add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>");
        objects.add("<< /Length " + stream.getBytes(PDF_ZEICHENSATZ).length + " >>\nstream\n" + stream + "\nendstream");

        StringBuilder pdf = new StringBuilder("%PDF-1.4\n");
        List<Integer> offsets = new ArrayList<>();
        offsets.add(0);
        for (int i = 0; i < objects.size(); i++) {
            offsets.add(pdf.toString().getBytes(PDF_ZEICHENSATZ).length);
            pdf.append(i + 1).append(" 0 obj\n").append(objects.get(i)).append("\nendobj\n");
        }

        int xrefOffset = pdf.toString().getBytes(PDF_ZEICHENSATZ).length;
        pdf.append("xref\n0 ").append(objects.size() + 1).append("\n");
        pdf.append("0000000000 65535 f \n");
        for (int i = 1; i < offsets.size(); i++) {
            pdf.append(String.format("%010d 00000 n \n", offsets.get(i)));
        }
        pdf.append("trailer\n<< /Size ").append(objects.size() + 1).append(" /Root 1 0 R >>\n");
        pdf.append("startxref\n").append(xrefOffset).append("\n%%EOF");
        return pdf.toString().getBytes(PDF_ZEICHENSATZ);
    }

    private String escape(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("(", "\\(")
                .replace(")", "\\)");
    }
}
