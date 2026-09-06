package de.mkysarte.bewerbungsmanager.erinnerung.dto;

/**
 * Ein fälliger Hinweis zu einer Bewerbung.
 *
 * @param art  worum es geht
 * @param text fertig formulierter Hinweis für die Karte
 */
public record Erinnerung(Art art, String text) {

    public enum Art {
        /** Bewerbung angelegt, aber noch kein Anschreiben hochgeladen. */
        ANSCHREIBEN_FEHLT,
        /** Seit dem Abschicken ist die Nachfassfrist verstrichen. */
        NACHFASSEN
    }
}
