package de.mkysarte.bewerbungsmanager.erinnerung.dto;

/**
 * Wie oft an einen liegengebliebenen Entwurf erinnert werden soll.
 *
 * <p>Steuert ausschließlich die <b>Benachrichtigungen</b>. Auf der Karte steht ohnehin immer,
 * seit wann der Entwurf liegt — ein Entwurf ist ein Entwurf, unabhängig vom gewählten Rhythmus.
 */
public enum ErinnerungsIntervall {

    TAEGLICH("täglich", 1),
    WOECHENTLICH("1x pro Woche", 7),
    MONATLICH("1x pro Monat", 30),
    AUS("aus", -1);

    private final String bezeichnung;
    private final int tage;

    ErinnerungsIntervall(String bezeichnung, int tage) {
        this.bezeichnung = bezeichnung;
        this.tage = tage;
    }

    public String getBezeichnung() {
        return bezeichnung;
    }

    /** Abstand zwischen zwei Meldungen in Tagen; bei {@link #AUS} bedeutungslos. */
    public int getTage() {
        return tage;
    }

    public boolean istAktiv() {
        return this != AUS;
    }

    /**
     * Liest den in der Datenbank gespeicherten Namen.
     * Unbekannte oder fehlende Werte ergeben {@code null} — dann gilt der globale Standard.
     */
    public static ErinnerungsIntervall ausText(String wert) {
        if (wert == null || wert.isBlank()) {
            return null;
        }
        try {
            return valueOf(wert.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public String toString() {
        return bezeichnung;
    }
}
