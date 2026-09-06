package de.mkysarte.bewerbungsmanager.erinnerung.service;

import de.mkysarte.bewerbungsmanager.bewerbungseintrag.dto.BewerbungseintragResponse;
import de.mkysarte.bewerbungsmanager.einstellung.service.EinstellungService;
import de.mkysarte.bewerbungsmanager.erinnerung.dto.Erinnerung;
import de.mkysarte.bewerbungsmanager.erinnerung.dto.ErinnerungsIntervall;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Entscheidet, was zu einer Bewerbung ansteht.
 *
 * <p>Zwei Dinge werden hier bewusst getrennt gehalten:
 * <ul>
 *   <li><b>Die Karte</b> zeigt einen Zustand — „Entwurf seit …", „Nachfassen ab …". Statisch,
 *       ohne Rhythmus: Ein Entwurf ist ein Entwurf, egal wie oft man erinnert werden will.</li>
 *   <li><b>Die Benachrichtigung</b> braucht dagegen den Rhythmus <i>und</i> das Datum der
 *       letzten Meldung. Ohne das feuert ein „täglich" bei jedem Programmstart erneut und die
 *       Erinnerung wird zu dem, was man wegklickt statt zu lesen.</li>
 * </ul>
 *
 * <p>Die {@link Clock} ist einsetzbar, damit sich Fristen im Test ohne Warten prüfen lassen.
 */
@Service
public class ErinnerungService {

    public static final String STATUS_ENTWURF = "ENTWURF";
    public static final String STATUS_ABGESCHICKT = "ABGESCHICKT";

    private final EinstellungService einstellungService;
    private final Clock clock;

    @Autowired
    public ErinnerungService(EinstellungService einstellungService) {
        this(einstellungService, Clock.systemDefaultZone());
    }

    /** Nur für Tests: erlaubt eine feste Uhr, damit Fristen ohne Warten prüfbar sind. */
    ErinnerungService(EinstellungService einstellungService, Clock clock) {
        this.einstellungService = einstellungService;
        this.clock = clock;
    }

    public LocalDate heute() {
        return LocalDate.now(clock);
    }

    // =====================================================================
    //  Für die Karte
    // =====================================================================

    /**
     * Ab wann nachgefasst werden sollte — oder {@code null}, wenn die Bewerbung nicht
     * abgeschickt ist oder kein Abschickdatum vorliegt.
     */
    public LocalDate nachfassenAb(BewerbungseintragResponse application) {
        if (application == null || application.abgeschicktAm() == null) {
            return null;
        }
        if (!STATUS_ABGESCHICKT.equalsIgnoreCase(application.statusTitel())) {
            return null;
        }
        return application.abgeschicktAm().toLocalDate().plusDays(effektiveFristTage(application));
    }

    /** Ist der Nachfasstermin erreicht? */
    public boolean nachfassenFaellig(BewerbungseintragResponse application) {
        LocalDate ab = nachfassenAb(application);
        return ab != null && !heute().isBefore(ab);
    }

    /** Fehlt das Anschreiben, und ist diese Erinnerung überhaupt eingeschaltet? */
    public boolean anschreibenFehlt(BewerbungseintragResponse application) {
        return application != null
                && application.anschreibenId() == null
                && einstellungService.isErinnerungAnschreibenAktiv();
    }

    /** Alle offenen Hinweise zu einer Bewerbung, leer wenn nichts ansteht. */
    public List<Erinnerung> fuer(BewerbungseintragResponse application) {
        List<Erinnerung> erinnerungen = new ArrayList<>();
        if (application == null) {
            return erinnerungen;
        }
        if (anschreibenFehlt(application)) {
            erinnerungen.add(new Erinnerung(
                    Erinnerung.Art.ANSCHREIBEN_FEHLT, "Noch kein Anschreiben hochgeladen"));
        }
        if (nachfassenFaellig(application)) {
            erinnerungen.add(new Erinnerung(
                    Erinnerung.Art.NACHFASSEN, "Nachfassen fällig seit " + formatiere(nachfassenAb(application))));
        }
        return erinnerungen;
    }

    public boolean istFaellig(BewerbungseintragResponse application) {
        return !fuer(application).isEmpty();
    }

    /** Die Frist dieser Bewerbung, sonst der global eingestellte Standard. */
    public int effektiveFristTage(BewerbungseintragResponse application) {
        Integer eigene = application == null ? null : application.nachfassFristTage();
        return eigene != null
                ? EinstellungService.clampFrist(eigene)
                : einstellungService.getNachfassFristTage();
    }

    /** Der Rhythmus dieser Bewerbung, sonst der global eingestellte Standard. */
    public ErinnerungsIntervall effektivesIntervall(BewerbungseintragResponse application) {
        ErinnerungsIntervall eigenes = application == null
                ? null
                : ErinnerungsIntervall.ausText(application.entwurfErinnerungIntervall());
        return eigenes != null ? eigenes : einstellungService.getEntwurfIntervall();
    }

    // =====================================================================
    //  Für die Benachrichtigungen
    // =====================================================================

    /**
     * Die Bewerbungen, zu denen jetzt eine Benachrichtigung herausgehen soll.
     *
     * <p>Anders als {@link #für} berücksichtigt das den gewählten Rhythmus und wann zuletzt
     * gemeldet wurde — es liefert also nur, was seit der letzten Meldung wieder fällig geworden
     * ist. Wer die Liste verarbeitet, muss anschließend {@code letzteErinnerungAm} auf heute
     * setzen, sonst kommt dieselbe Meldung beim nächsten Durchlauf erneut.
     */
    public List<BewerbungseintragResponse> faelligeBenachrichtigungen(
            List<BewerbungseintragResponse> applications) {
        if (applications == null || !einstellungService.isBenachrichtigungenAktiv()) {
            return List.of();
        }
        return applications.stream().filter(this::benachrichtigungFaellig).toList();
    }

    /** Kurztext für die Benachrichtigung, z. B. „Musterfirma GmbH — nachfassen". */
    public String benachrichtigungsText(BewerbungseintragResponse application) {
        String firma = application.firmaName() != null ? application.firmaName() : "Bewerbung";
        if (nachfassenFaellig(application)) {
            return firma + " — nachfassen (abgeschickt am " + formatiere(application.abgeschicktAm().toLocalDate()) + ")";
        }
        return firma + " — Entwurf liegt seit " + formatiere(entwurfSeit(application));
    }

    boolean benachrichtigungFaellig(BewerbungseintragResponse application) {
        if (application == null) {
            return false;
        }
        // Am selben Tag nicht zweimal - das ist der ganze Zweck von letzteErinnerungAm.
        if (heute().equals(application.letzteErinnerungAm())) {
            return false;
        }
        if (nachfassenFaellig(application)) {
            return abstandErreicht(application, 1);
        }
        if (STATUS_ENTWURF.equalsIgnoreCase(application.statusTitel())) {
            ErinnerungsIntervall intervall = effektivesIntervall(application);
            return intervall.istAktiv() && abstandErreicht(application, intervall.getTage());
        }
        return false;
    }

    /**
     * Ist seit der letzten Meldung genug Zeit vergangen? Wurde noch nie gemeldet, zählt das
     * Entstehungsdatum — sonst würde eine gerade angelegte Bewerbung sofort melden.
     */
    private boolean abstandErreicht(BewerbungseintragResponse application, int tage) {
        LocalDate letzte = application.letzteErinnerungAm();
        if (letzte == null) {
            LocalDate seit = entwurfSeit(application);
            return seit == null || ChronoUnit.DAYS.between(seit, heute()) >= tage;
        }
        return ChronoUnit.DAYS.between(letzte, heute()) >= tage;
    }

    /** Seit wann die Bewerbung existiert — bevorzugt das fachliche Datum. */
    public LocalDate entwurfSeit(BewerbungseintragResponse application) {
        if (application == null) {
            return null;
        }
        if (application.erstelltAm() != null) {
            return application.erstelltAm();
        }
        return Optional.ofNullable(application.createdAt())
                .map(java.time.LocalDateTime::toLocalDate)
                .orElse(null);
    }

    private String formatiere(LocalDate datum) {
        return datum == null ? "—" : datum.format(DATUM);
    }

    /** Einheitliches Datumsformat in der gesamten Oberfläche. */
    public static final java.time.format.DateTimeFormatter DATUM =
            java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy");
}
