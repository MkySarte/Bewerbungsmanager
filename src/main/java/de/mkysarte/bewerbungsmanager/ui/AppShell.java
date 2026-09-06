package de.mkysarte.bewerbungsmanager.ui;

/**
 * Zugriff auf die Anwendungshülle aus dem laufenden Spring-Context heraus.
 *
 * <p>Nötig, weil Abmelden mehr ist als ein Szenenwechsel: Die Datenbank ist an die Sitzung
 * gebunden, also muss der Spring-Context samt DataSource geschlossen und der
 * Anmeldebildschirm neu aufgebaut werden. Diesen Ablauf kennt nur
 * {@code BewerbungsmanagerApp} — die Oberfläche stößt ihn über diese Schnittstelle an.
 *
 * <p>Die Implementierung wird beim Hochfahren als Singleton in den Context gelegt und ist
 * dadurch injizierbar wie jeder andere Bean.
 */
public interface AppShell {

    /**
     * Meldet ab: schließt den Spring-Context (und damit die geöffnete Datenbank) und zeigt
     * wieder den Anmeldebildschirm.
     */
    void logout();

    /**
     * Übernimmt geänderte Einstellungen, die die Hülle betreffen — Symbol im Infobereich
     * anlegen oder entfernen, Zeitgeber neu starten.
     *
     * <p>Ohne diesen Rückkanal würde ein frisch eingeschalteter Infobereich erst beim
     * nächsten Programmstart erscheinen.
     */
    default void einstellungenUebernommen() {
    }
}
