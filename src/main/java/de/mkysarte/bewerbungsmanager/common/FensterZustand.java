package de.mkysarte.bewerbungsmanager.common;

import javafx.animation.PauseTransition;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Merkt sich Größe und Position des Fensters über Programmläufe hinweg.
 *
 * <p>Bewusst <b>nicht</b> in den Einstellungen der Datenbank: Das Fenster entsteht, bevor sich
 * jemand angemeldet hat — zu diesem Zeitpunkt ist die Datenbank noch verschlüsselt und nicht
 * lesbar. Die Maße landen deshalb in einer eigenen, unverschlüsselten Datei neben dem
 * Schlüssel-Tresor. Fenstergrößen verraten nichts, das ist unbedenklich.
 *
 * <p>Kein Spring-Bean: wird vor dem Start des Contexts gebraucht.
 */
public final class FensterZustand {

    private static final Logger log = LoggerFactory.getLogger(FensterZustand.class);

    private static final String DATEINAME = "fenster.properties";

    private final Path datei;

    /**
     * Letzte Maße im nicht maximierten Zustand. Im Maximierten liefert die Bühne die
     * Bildschirmgröße — die zu speichern hieße, dass ein späteres Wiederherstellen ein
     * bildschirmfüllendes, aber nicht maximiertes Fenster ergäbe.
     */
    private double breite;
    private double hoehe;
    private double x = Double.NaN;
    private double y = Double.NaN;
    private boolean maximiert;

    public FensterZustand(Path dataDir) {
        this.datei = dataDir.resolve(DATEINAME);
    }

    /**
     * Stellt die zuletzt gemerkten Maße wieder her und beginnt, Änderungen mitzuschreiben.
     *
     * @param standardBreite Breite beim allerersten Start
     * @param standardHoehe  Höhe beim allerersten Start
     */
    public void anwendenUndBeobachten(Stage stage, double standardBreite, double standardHoehe) {
        lesen();

        stage.setWidth(breite > 0 ? breite : standardBreite);
        stage.setHeight(hoehe > 0 ? hoehe : standardHoehe);

        if (istSichtbarePosition(x, y, stage.getWidth(), stage.getHeight())) {
            stage.setX(x);
            stage.setY(y);
        } else {
            // Kein passender Bildschirm mehr (anderer Monitor, andere Auflösung) - lieber
            // mittig als ausserhalb des sichtbaren Bereichs.
            stage.centerOnScreen();
        }
        stage.setMaximized(maximiert);

        /*
         * Die Maße werden bewusst verzoegert übernommen statt direkt im Zuhörer.
         *
         * Beim Maximieren setzt JavaFX erst die Position (unter Windows auf -7/-7) und erst
         * danach das Maximiert-Kennzeichen. Eine Pruefung im Zuhörer kommt für die Position
         * also zu spaet, und man merkt sich einen Platz knapp ausserhalb des Bildschirms.
         * Nach einer kurzen Ruhepause ist der Zustand dagegen stabil, und es wird nur
         * übernommen, was wirklich ein normales Fenster beschreibt.
         */
        PauseTransition beruhigung = new PauseTransition(Duration.millis(250));
        beruhigung.setOnFinished(e -> uebernehmen(stage));

        ChangeListener<Number> beiAenderung = (obs, alt, neu) -> beruhigung.playFromStart();
        stage.widthProperty().addListener(beiAenderung);
        stage.heightProperty().addListener(beiAenderung);
        stage.xProperty().addListener(beiAenderung);
        stage.yProperty().addListener(beiAenderung);
        stage.maximizedProperty().addListener((obs, alt, neu) -> {
            maximiert = neu;
            beruhigung.playFromStart();
        });
    }

    /** Übernimmt die Maße, sofern sie gerade ein gewoehnliches Fenster beschreiben. */
    private void uebernehmen(Stage stage) {
        if (!stage.isShowing() || stage.isMaximized() || stage.isIconified()) {
            return;
        }
        if (stage.getWidth() > 0 && stage.getHeight() > 0) {
            breite = stage.getWidth();
            hoehe = stage.getHeight();
            x = stage.getX();
            y = stage.getY();
        }
    }

    /** Schreibt den gemerkten Zustand auf die Platte. Fehler bleiben folgenlos. */
    public void speichern() {
        if (breite <= 0 || hoehe <= 0) {
            return;
        }
        Properties properties = new Properties();
        properties.setProperty("breite", String.valueOf((int) breite));
        properties.setProperty("hoehe", String.valueOf((int) hoehe));
        properties.setProperty("maximiert", String.valueOf(maximiert));
        if (!Double.isNaN(x) && !Double.isNaN(y)) {
            properties.setProperty("x", String.valueOf((int) x));
            properties.setProperty("y", String.valueOf((int) y));
        }
        try {
            Files.createDirectories(datei.getParent());
            try (OutputStream out = Files.newOutputStream(datei)) {
                properties.store(out, "Bewerbungsmanager - zuletzt verwendete Fenstergröße");
            }
        } catch (IOException e) {
            // Eine nicht gemerkte Fenstergröße ist ein Schönheitsfehler, kein Grund zu stören.
            log.debug("Fenstergröße konnte nicht gespeichert werden", e);
        }
    }

    private void lesen() {
        if (!Files.isRegularFile(datei)) {
            return;
        }
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(datei)) {
            properties.load(in);
        } catch (IOException e) {
            log.debug("Fenstergröße konnte nicht gelesen werden", e);
            return;
        }
        breite = zahl(properties, "breite", 0);
        hoehe = zahl(properties, "hoehe", 0);
        x = zahl(properties, "x", Double.NaN);
        y = zahl(properties, "y", Double.NaN);
        maximiert = Boolean.parseBoolean(properties.getProperty("maximiert", "false"));
    }

    /**
     * Liegt das Fenster noch auf einem vorhandenen Bildschirm? Nach einem Monitorwechsel
     * zeigen gespeicherte Koordinaten sonst ins Leere und das Fenster wäre unauffindbar.
     */
    private boolean istSichtbarePosition(double x, double y, double breite, double hoehe) {
        if (Double.isNaN(x) || Double.isNaN(y)) {
            return false;
        }
        // Ein Streifen der Titelleiste genuegt, um das Fenster wieder greifen zu können.
        return !Screen.getScreensForRectangle(new Rectangle2D(x, y, Math.max(breite, 1), 40)).isEmpty();
    }

    private double zahl(Properties properties, String schluessel, double standard) {
        String wert = properties.getProperty(schluessel);
        if (wert == null || wert.isBlank()) {
            return standard;
        }
        try {
            return Double.parseDouble(wert.trim());
        } catch (NumberFormatException e) {
            return standard;
        }
    }
}
