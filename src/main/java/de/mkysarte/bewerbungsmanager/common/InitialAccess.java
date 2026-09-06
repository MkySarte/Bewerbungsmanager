package de.mkysarte.bewerbungsmanager.common;

/**
 * Der Initial-Zugang — Demo und letzter Ausweg zugleich.
 *
 * <p>Diese Zugangsdaten sind <b>absichtlich</b> fest und öffentlich. Sie erfüllen zwei Aufgaben:
 * <ul>
 *   <li><b>Demo</b> — die App vor der Entscheidung mit Musterdaten ausprobieren</li>
 *   <li><b>Letzter Ausweg</b> — sind Passwort <i>und</i> Masterpasswort verloren, kommt man
 *       trotzdem wieder in die Anwendung hinein, statt sie deinstallieren zu müssen</li>
 * </ul>
 *
 * <p>Damit dieser Weg nie vergessen wird, stehen die Daten dauerhaft sichtbar im Login.
 *
 * <p>Dass sie öffentlich sind, ist unbedenklich: Der Initial-Zugang öffnet ausschließlich
 * {@code demo.db} mit Musterdaten. Die echte Datenbank ist mit einem anderen Schlüssel
 * verschlüsselt, den dieser Zugang nicht besitzt — er kommt an die echten Daten nicht heran,
 * auch nicht an der Oberfläche vorbei.
 */
public final class InitialAccess {

    public static final String USERNAME = "mkysarte";
    public static final String PASSWORD = "changeme";

    private InitialAccess() {
    }

    public static boolean isInitialUsername(String username) {
        return username != null && USERNAME.equalsIgnoreCase(username.trim());
    }
}
