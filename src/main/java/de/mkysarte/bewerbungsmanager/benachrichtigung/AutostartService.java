package de.mkysarte.bewerbungsmanager.benachrichtigung;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.Optional;

/**
 * Trägt die Anwendung in den Windows-Autostart ein oder wieder aus.
 *
 * <p>Geschrieben wird nach {@code HKCU\\...\\CurrentVersion\\Run} — also nur für den
 * angemeldeten Benutzer, ohne Administratorrechte und ohne systemweite Änderung.
 *
 * <p><b>Läuft nur aus der installierten Fassung sinnvoll.</b> Startet die App über
 * {@code mvn javafx:run}, zeigt der Prozesspfad auf {@code java.exe} — ein Autostart darauf
 * würde nur eine nackte JVM starten. Deshalb prüft {@link #istVerfuegbar()}, ob überhaupt eine
 * eigene ausführbare Datei dahintersteht, und die Oberfläche blendet den Schalter sonst aus.
 *
 * <p>Kein Spring-Bean: wird auch vor dem Start des Contexts gebraucht.
 */
public final class AutostartService {

    private static final Logger log = LoggerFactory.getLogger(AutostartService.class);

    private static final String RUN_KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run";
    private static final String EINTRAG = "Bewerbungsmanager";

    /** Startargument, mit dem die App direkt in den Infobereich startet. */
    public static final String ARG_MINIMIERT = "--minimized";

    private AutostartService() {
    }

    public static boolean istWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    /** Pfad der laufenden ausführbaren Datei, sofern es eine eigene ist. */
    public static Optional<String> programmPfad() {
        return ProcessHandle.current().info().command()
                .filter(pfad -> {
                    String name = pfad.toLowerCase(Locale.ROOT);
                    // java.exe/javaw.exe bedeutet: wir laufen aus der Entwicklungsumgebung.
                    return !name.endsWith("java.exe") && !name.endsWith("javaw.exe")
                            && !name.endsWith("/java") && !name.endsWith("\\java");
                });
    }

    /** Lässt sich Autostart hier überhaupt sinnvoll einrichten? */
    public static boolean istVerfuegbar() {
        return istWindows() && programmPfad().isPresent();
    }

    /** Grund, warum es nicht geht — für den Hinweis in den Einstellungen. */
    public static String nichtVerfuegbarGrund() {
        if (!istWindows()) {
            return "Autostart wird derzeit nur unter Windows unterstützt.";
        }
        return "Autostart ist nur in der installierten Fassung möglich, nicht beim Start aus der "
                + "Entwicklungsumgebung.";
    }

    /**
     * Schaltet den Autostart ein oder aus.
     *
     * @return true, wenn die Änderung tatsächlich geschrieben wurde
     */
    public static boolean setzen(boolean aktiv) {
        if (!istVerfuegbar()) {
            log.info("Autostart nicht verfügbar: {}", nichtVerfuegbarGrund());
            return false;
        }
        try {
            ProcessBuilder builder = aktiv
                    ? new ProcessBuilder("reg", "add", RUN_KEY, "/v", EINTRAG, "/t", "REG_SZ",
                            "/d", "\"" + programmPfad().orElseThrow() + "\" " + ARG_MINIMIERT, "/f")
                    : new ProcessBuilder("reg", "delete", RUN_KEY, "/v", EINTRAG, "/f");
            Process process = builder.redirectErrorStream(true).start();
            int code = process.waitFor();
            if (code != 0 && aktiv) {
                log.warn("Autostart konnte nicht gesetzt werden (Rückgabewert {})", code);
                return false;
            }
            // Beim Entfernen ist ein Fehler normal, wenn der Eintrag gar nicht existierte.
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            log.warn("Autostart konnte nicht geändert werden", e);
            return false;
        }
    }
}
