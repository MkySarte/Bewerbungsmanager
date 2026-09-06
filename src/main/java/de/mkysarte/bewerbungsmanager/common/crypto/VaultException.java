package de.mkysarte.bewerbungsmanager.common.crypto;

/**
 * Der Schlüssel-Tresor konnte nicht gelesen oder geschrieben werden
 * (fehlende Datei, beschädigter Inhalt, Dateisystemfehler).
 */
public class VaultException extends RuntimeException {

    public VaultException(String message) {
        super(message);
    }

    public VaultException(String message, Throwable cause) {
        super(message, cause);
    }
}
