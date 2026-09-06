package de.mkysarte.bewerbungsmanager.common.crypto;

import de.mkysarte.bewerbungsmanager.common.AppPaths;

import java.nio.file.Path;

/**
 * Die eine Datenbank, die in dieser Sitzung geöffnet ist.
 *
 * <p>Wird beim Anmelden festgelegt — also bevor der Spring-Context startet — und danach nicht
 * mehr geändert. Pro Programmlauf ist immer nur <b>eine</b> der beiden Datenbankdateien offen;
 * ein Wechsel zwischen Demo und echtem Konto erfordert eine Abmeldung.
 *
 * @param demo         Demo-Sitzung (Musterdaten) statt echtem Konto
 * @param username     angemeldeter Benutzername
 * @param databaseFile geöffnete Datenbankdatei
 * @param databaseKey  Schlüssel dieser Datei, als Hex für die JDBC-URL
 */
public record DbSession(boolean demo, String username, Path databaseFile, String databaseKey) {

    /**
     * JDBC-URL mit Cipher-Konfiguration.
     *
     * <p>{@code legacy=4} wählt das SQLCipher-4-Format, {@code hexkey_mode=true} sorgt dafür,
     * dass der Schlüssel als Rohbytes übernommen wird statt noch einmal durch eine
     * Passwortableitung zu laufen — die hat der Tresor bereits gemacht.
     */
    public String jdbcUrl() {
        return "jdbc:sqlite:file:" + AppPaths.toJdbcPath(databaseFile)
                + "?cipher=sqlcipher&legacy=4&hexkey_mode=true&key=" + databaseKey;
    }
}
