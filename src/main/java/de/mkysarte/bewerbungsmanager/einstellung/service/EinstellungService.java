package de.mkysarte.bewerbungsmanager.einstellung.service;

import de.mkysarte.bewerbungsmanager.einstellung.entity.EinstellungEntity;
import de.mkysarte.bewerbungsmanager.einstellung.repository.EinstellungRepository;
import de.mkysarte.bewerbungsmanager.erinnerung.dto.ErinnerungsIntervall;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Liest und schreibt die anwendungsweiten Einstellungen.
 *
 * <p>Fehlt ein Wert oder ist er unbrauchbar, wird still der Standard verwendet: Eine
 * Einstellung, die sich nicht lesen lässt, darf die Anwendung nicht am Starten hindern.
 */
@Service
public class EinstellungService {

    private static final Logger log = LoggerFactory.getLogger(EinstellungService.class);

    /** Tage bis zur Nachfass-Erinnerung nach dem Abschicken. */
    public static final String KEY_NACHFASS_FRIST_TAGE = "nachfass.frist.tage";
    public static final int DEFAULT_NACHFASS_FRIST_TAGE = 14;

    /** Soll an ein fehlendes Anschreiben erinnert werden? */
    public static final String KEY_ERINNERUNG_ANSCHREIBEN = "erinnerung.anschreiben.aktiv";
    public static final boolean DEFAULT_ERINNERUNG_ANSCHREIBEN = true;

    /** Standard-Rhythmus für Entwürfe, wenn die Bewerbung keinen eigenen hat. */
    public static final String KEY_ENTWURF_INTERVALL = "erinnerung.entwurf.intervall";
    public static final ErinnerungsIntervall DEFAULT_ENTWURF_INTERVALL = ErinnerungsIntervall.WOECHENTLICH;

    /** Windows-Benachrichtigungen über den Infobereich. */
    public static final String KEY_BENACHRICHTIGUNGEN = "benachrichtigung.aktiv";
    public static final boolean DEFAULT_BENACHRICHTIGUNGEN = false;

    /** Verhalten beim Schließen des Fensters: FRAGEN, TRAY oder BEENDEN. */
    public static final String KEY_SCHLIESSEN_VERHALTEN = "fenster.schliessen";
    public static final String DEFAULT_SCHLIESSEN_VERHALTEN = "FRAGEN";

    /** Minuten im Infobereich bis zum automatischen Sperren; 0 = nie sperren. */
    public static final String KEY_AUTO_SPERRE_MINUTEN = "sicherheit.autosperre.minuten";
    public static final int DEFAULT_AUTO_SPERRE_MINUTEN = 30;

    /** Mit Windows starten (nur aussagekräftig in der installierten Fassung). */
    public static final String KEY_AUTOSTART = "system.autostart";
    public static final boolean DEFAULT_AUTOSTART = false;

    /** Obergrenze, damit ein Tippfehler nicht zu einer Frist von 10.000 Tagen führt. */
    private static final int MAX_FRIST_TAGE = 365;

    /** Obergrenze für die Sperrzeit - alles darüber ist praktisch "nie". */
    private static final int MAX_SPERRE_MINUTEN = 1440;

    private final EinstellungRepository einstellungRepository;

    public EinstellungService(EinstellungRepository einstellungRepository) {
        this.einstellungRepository = einstellungRepository;
    }

    @Transactional(readOnly = true)
    public int getNachfassFristTage() {
        String wert = findWert(KEY_NACHFASS_FRIST_TAGE);
        if (wert == null) {
            return DEFAULT_NACHFASS_FRIST_TAGE;
        }
        try {
            return clampFrist(Integer.parseInt(wert.trim()));
        } catch (NumberFormatException e) {
            log.warn("Unbrauchbarer Wert für {}: '{}' - verwende Standard {}",
                    KEY_NACHFASS_FRIST_TAGE, wert, DEFAULT_NACHFASS_FRIST_TAGE);
            return DEFAULT_NACHFASS_FRIST_TAGE;
        }
    }

    @Transactional
    public void setNachfassFristTage(int tage) {
        speichern(KEY_NACHFASS_FRIST_TAGE, String.valueOf(clampFrist(tage)));
    }

    @Transactional(readOnly = true)
    public boolean isErinnerungAnschreibenAktiv() {
        String wert = findWert(KEY_ERINNERUNG_ANSCHREIBEN);
        return wert == null ? DEFAULT_ERINNERUNG_ANSCHREIBEN : Boolean.parseBoolean(wert.trim());
    }

    @Transactional
    public void setErinnerungAnschreibenAktiv(boolean aktiv) {
        speichern(KEY_ERINNERUNG_ANSCHREIBEN, String.valueOf(aktiv));
    }

    // =====================================================================
    //  Erinnerungen und Benachrichtigungen
    // =====================================================================

    @Transactional(readOnly = true)
    public ErinnerungsIntervall getEntwurfIntervall() {
        ErinnerungsIntervall gespeichert = ErinnerungsIntervall.ausText(findWert(KEY_ENTWURF_INTERVALL));
        return gespeichert != null ? gespeichert : DEFAULT_ENTWURF_INTERVALL;
    }

    @Transactional
    public void setEntwurfIntervall(ErinnerungsIntervall intervall) {
        speichern(KEY_ENTWURF_INTERVALL, intervall.name());
    }

    @Transactional(readOnly = true)
    public boolean isBenachrichtigungenAktiv() {
        return leseBoolean(KEY_BENACHRICHTIGUNGEN, DEFAULT_BENACHRICHTIGUNGEN);
    }

    @Transactional
    public void setBenachrichtigungenAktiv(boolean aktiv) {
        speichern(KEY_BENACHRICHTIGUNGEN, String.valueOf(aktiv));
    }

    // =====================================================================
    //  Fenster und Sicherheit
    // =====================================================================

    @Transactional(readOnly = true)
    public String getSchliessenVerhalten() {
        String wert = findWert(KEY_SCHLIESSEN_VERHALTEN);
        return wert == null || wert.isBlank() ? DEFAULT_SCHLIESSEN_VERHALTEN : wert.trim();
    }

    @Transactional
    public void setSchliessenVerhalten(String verhalten) {
        speichern(KEY_SCHLIESSEN_VERHALTEN, verhalten);
    }

    /** Minuten bis zur automatischen Sperre im Infobereich; 0 bedeutet "nie sperren". */
    @Transactional(readOnly = true)
    public int getAutoSperreMinuten() {
        String wert = findWert(KEY_AUTO_SPERRE_MINUTEN);
        if (wert == null) {
            return DEFAULT_AUTO_SPERRE_MINUTEN;
        }
        try {
            return clampSperre(Integer.parseInt(wert.trim()));
        } catch (NumberFormatException e) {
            log.warn("Unbrauchbarer Wert für {}: '{}' - verwende Standard {}",
                    KEY_AUTO_SPERRE_MINUTEN, wert, DEFAULT_AUTO_SPERRE_MINUTEN);
            return DEFAULT_AUTO_SPERRE_MINUTEN;
        }
    }

    @Transactional
    public void setAutoSperreMinuten(int minuten) {
        speichern(KEY_AUTO_SPERRE_MINUTEN, String.valueOf(clampSperre(minuten)));
    }

    @Transactional(readOnly = true)
    public boolean isAutostartAktiv() {
        return leseBoolean(KEY_AUTOSTART, DEFAULT_AUTOSTART);
    }

    @Transactional
    public void setAutostartAktiv(boolean aktiv) {
        speichern(KEY_AUTOSTART, String.valueOf(aktiv));
    }

    /** Begrenzt die Frist auf einen sinnvollen Bereich (0 = sofort fällig). */
    public static int clampFrist(int tage) {
        return Math.max(0, Math.min(MAX_FRIST_TAGE, tage));
    }

    public static int clampSperre(int minuten) {
        return Math.max(0, Math.min(MAX_SPERRE_MINUTEN, minuten));
    }

    private boolean leseBoolean(String schluessel, boolean standard) {
        String wert = findWert(schluessel);
        return wert == null ? standard : Boolean.parseBoolean(wert.trim());
    }

    private String findWert(String schluessel) {
        return einstellungRepository.findById(schluessel)
                .map(EinstellungEntity::getWert)
                .orElse(null);
    }

    private void speichern(String schluessel, String wert) {
        einstellungRepository.save(new EinstellungEntity(schluessel, wert));
    }
}
