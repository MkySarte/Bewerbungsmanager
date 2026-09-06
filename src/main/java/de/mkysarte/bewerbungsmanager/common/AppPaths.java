package de.mkysarte.bewerbungsmanager.common;

import java.io.File;
import java.nio.file.Path;

/**
 * Feste Ablageorte der Anwendung im Benutzerverzeichnis.
 *
 * <p>Es gibt zwei getrennte Datenbankdateien, und diese Trennung ist der Kern des
 * Sicherheitskonzepts: {@link #demoDatabase()} enthält ausschließlich Musterdaten und wird mit
 * dem öffentlich bekannten Demo-Passwort geöffnet, {@link #realDatabase()} enthält die echten
 * Bewerbungsdaten und wird mit dem Passwort des Nutzers geöffnet. Der Demo-Zugang besitzt den
 * Schlüssel zur echten Datenbank schlicht nicht — die Trennung ist dadurch kryptografisch
 * erzwungen und nicht bloß in der Oberfläche geprüft.
 */
public final class AppPaths {

    private static final String DIRECTORY_NAME = ".bewerbungsmanager";

    private AppPaths() {
    }

    public static Path dataDirectory() {
        return Path.of(System.getProperty("user.home"), DIRECTORY_NAME);
    }

    /** Musterdaten des Demo-/Initial-Zugangs. */
    public static Path demoDatabase() {
        return dataDirectory().resolve("demo.db");
    }

    /** Echte Bewerbungsdaten des eingerichteten Kontos. */
    public static Path realDatabase() {
        return dataDirectory().resolve("data.db");
    }

    /**
     * Pfad in der Form, die die JDBC-URL erwartet — auch unter Windows mit Schrägstrichen.
     */
    public static String toJdbcPath(Path path) {
        return path.toString().replace(File.separatorChar, '/');
    }
}
