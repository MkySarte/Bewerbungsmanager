package de.mkysarte.bewerbungsmanager.common.crypto;

/**
 * Das angegebene Passwort konnte den Schlüssel-Tresor nicht öffnen.
 *
 * Bewusst getrennt von {@link VaultException}: hier ist das Passwort falsch,
 * dort ist die Tresordatei beschädigt oder nicht lesbar. Für den Nutzer sind
 * das zwei völlig verschiedene Situationen.
 */
public class InvalidPasswordException extends Exception {

    public InvalidPasswordException(String message) {
        super(message);
    }
}
