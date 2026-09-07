package de.mkysarte.bewerbungsmanager;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wacht über die Anwendungssymbole.
 *
 * <p>Ein Test für ein paar Bilddateien wirkt übertrieben — bis man sieht, dass hier
 * <b>jeder Fehler stumm ist</b>: Der {@code TrayService} malt bei einer fehlenden Datei
 * kommentarlos einen Ersatzkreis, das Fenster nimmt das Java-Standardsymbol, und jpackage
 * beschwert sich über einen falschen Pfad erst beim Bauen des Installers — also frühestens
 * beim nächsten Release. Ein vertippter Name oder eine später eingeführte
 * Ressourcenfilterung, die Binärdateien beschädigt, bliebe ohne diesen Test unbemerkt.
 */
class IconRessourcenTest {

    /** Genau die Größen, die {@code BewerbungsmanagerApp.fensterSymboleLaden} lädt. */
    private static final List<Integer> GROESSEN = List.of(16, 32, 48, 64, 128, 256);

    /** Genau der Pfad, den {@code TrayService.ladeSymbol} abfragt. */
    private static final String TRAY = "/icons/tray.png";

    @Test
    void trayZeigtDasEigeneSymbolUndNichtDenErsatzkreis() throws Exception {
        BufferedImage bild = laden(TRAY);
        assertNotNull(bild, "Ohne " + TRAY + " zeichnet der TrayService still einen Ersatzkreis");
        // Die 32er, weil AWT bei setImageAutoSize besser herunter- als heraufskaliert.
        assertEquals(32, bild.getWidth(), TRAY);
        assertEquals(32, bild.getHeight(), TRAY);
    }

    @Test
    void alleFenstersymboleSindLadbarUndHabenIhreGroesse() throws Exception {
        for (int groesse : GROESSEN) {
            String pfad = "/icons/icon-" + groesse + ".png";
            BufferedImage bild = laden(pfad);
            assertNotNull(bild, pfad + " fehlt oder ist kein lesbares PNG");
            // Stimmt die Kantenlaenge nicht mit dem Namen ueberein, ist entweder die falsche
            // Datei kopiert worden oder eine Filterung hat sie beschaedigt.
            assertEquals(groesse, bild.getWidth(), pfad);
            assertEquals(groesse, bild.getHeight(), pfad);
        }
    }

    /**
     * Die Dateien für jpackage liegen außerhalb des Klassenpfads; die {@code pom.xml} zeigt
     * mit festen Pfaden dorthin. Ein Tippfehler dort fiele sonst erst beim Release auf.
     */
    @Test
    void dieDateienFuerDenInstallerLiegenBereit() {
        for (String name : List.of("bewerbungsmanager.ico", "bewerbungsmanager.icns",
                "bewerbungsmanager.png")) {
            Path datei = Path.of("src", "main", "packaging", name);
            assertTrue(Files.isRegularFile(datei), datei + " fehlt - die pom.xml verweist darauf");
        }
    }

    @Test
    void lizenzdateiIstVorhandenUndNenntDieLizenz() throws Exception {
        Path lizenz = Path.of("LICENSE.md");
        assertTrue(Files.isRegularFile(lizenz), "LICENSE.md fehlt - der Installer zeigt sie an");
        String text = Files.readString(lizenz);
        assertTrue(text.contains("PolyForm Noncommercial License 1.0.0"), "LICENSE.md");
        assertTrue(text.contains("Required Notice:"),
                "Der von PolyForm verlangte Vermerk fehlt");
    }

    private BufferedImage laden(String pfad) throws Exception {
        try (InputStream in = getClass().getResourceAsStream(pfad)) {
            return in == null ? null : ImageIO.read(in);
        }
    }
}
