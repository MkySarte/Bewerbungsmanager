package de.mkysarte.bewerbungsmanager.benachrichtigung;

import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.AWTException;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

/**
 * Symbol im Infobereich samt Windows-Benachrichtigungen.
 *
 * <p>Benutzt {@link SystemTray} aus dem JDK — keine zusätzliche Abhängigkeit, und das Modul
 * {@code java.desktop} steht bereits im {@code addModules} der {@code pom.xml}, der Installer
 * bringt es also mit.
 *
 * <p>Kein Spring-Bean: Das Symbol überlebt das An- und Abmelden und damit auch das Schließen
 * des Spring-Contexts. Es gehört zur Anwendungshülle, nicht zur Sitzung.
 *
 * <p>Gibt es keinen Infobereich (manche Linux-Oberflächen, eingeschränkte Umgebungen), bleibt
 * der Dienst still: Eine fehlende Benachrichtigung darf die Anwendung nicht aufhalten.
 */
public class TrayService {

    private static final Logger log = LoggerFactory.getLogger(TrayService.class);
    private static final String TITEL = "Bewerbungsmanager";

    private TrayIcon trayIcon;
    private Runnable onOeffnen;
    private Runnable onBeenden;

    public boolean istVerfuegbar() {
        return SystemTray.isSupported();
    }

    public boolean istSichtbar() {
        return trayIcon != null;
    }

    /**
     * Legt das Symbol an. Mehrfaches Aufrufen ist unschädlich.
     *
     * @return true, wenn danach ein Symbol vorhanden ist
     */
    public boolean anzeigen(Runnable onOeffnen, Runnable onBeenden) {
        this.onOeffnen = onOeffnen;
        this.onBeenden = onBeenden;

        if (trayIcon != null) {
            return true;
        }
        if (!istVerfuegbar()) {
            log.info("Infobereich wird auf diesem System nicht unterstützt - keine Benachrichtigungen");
            return false;
        }

        try {
            TrayIcon icon = new TrayIcon(ladeSymbol(), TITEL, baueMenue());
            icon.setImageAutoSize(true);
            // Doppelklick auf das Symbol holt das Fenster zurück - so erwartet man es.
            icon.addActionListener(e -> ausfuehren(this.onOeffnen));
            SystemTray.getSystemTray().add(icon);
            trayIcon = icon;
            return true;
        } catch (AWTException | RuntimeException e) {
            log.warn("Symbol im Infobereich konnte nicht angelegt werden", e);
            return false;
        }
    }

    public void entfernen() {
        if (trayIcon != null) {
            SystemTray.getSystemTray().remove(trayIcon);
            trayIcon = null;
        }
    }

    /**
     * Zeigt eine Windows-Benachrichtigung. Ohne Symbol im Infobereich passiert nichts —
     * bewusst still, damit Aufrufer nicht überall prüfen müssen.
     */
    public void melden(String kopfzeile, String text) {
        if (trayIcon == null) {
            return;
        }
        trayIcon.displayMessage(kopfzeile, text, TrayIcon.MessageType.INFO);
    }

    private PopupMenu baueMenue() {
        PopupMenu menu = new PopupMenu();

        MenuItem oeffnen = new MenuItem("Öffnen");
        oeffnen.addActionListener(e -> ausfuehren(onOeffnen));
        menu.add(oeffnen);

        menu.addSeparator();

        MenuItem beenden = new MenuItem("Beenden");
        beenden.addActionListener(e -> ausfuehren(onBeenden));
        menu.add(beenden);

        return menu;
    }

    /** Die Menüeinträge laufen im AWT-Thread — alles Weitere gehört auf den JavaFX-Thread. */
    private void ausfuehren(Runnable aktion) {
        if (aktion != null) {
            Platform.runLater(aktion);
        }
    }

    private Image ladeSymbol() {
        try (InputStream in = getClass().getResourceAsStream("/icons/tray.png")) {
            if (in != null) {
                return javax.imageio.ImageIO.read(in);
            }
        } catch (IOException e) {
            log.debug("Eigenes Symbol nicht lesbar, verwende Ersatz", e);
        }
        return ersatzSymbol();
    }

    /**
     * Schlichtes Ersatzsymbol, falls keine Bilddatei mitgeliefert wurde: ein gefüllter Kreis
     * in der Akzentfarbe der Anwendung. Besser als gar kein Symbol.
     */
    private Image ersatzSymbol() {
        int groesse = 16;
        BufferedImage image = new BufferedImage(groesse, groesse, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = image.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new java.awt.Color(0x25, 0x63, 0xEB));
        g.fillOval(0, 0, groesse - 1, groesse - 1);
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(4, 7, 8, 2);
        g.dispose();
        return image;
    }

    /** Nur zur Sicherheit, falls die Toolkit-Initialisierung noch nicht erfolgt ist. */
    public static void toolkitVorbereiten() {
        Toolkit.getDefaultToolkit();
    }
}
