package de.mkysarte.bewerbungsmanager.benachrichtigung;

import de.mkysarte.bewerbungsmanager.bewerbungseintrag.dto.BewerbungseintragResponse;
import de.mkysarte.bewerbungsmanager.bewerbungseintrag.service.BewerbungseintragService;
import de.mkysarte.bewerbungsmanager.einstellung.service.EinstellungService;
import de.mkysarte.bewerbungsmanager.erinnerung.service.ErinnerungService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Schickt fällige Erinnerungen als Windows-Benachrichtigung heraus.
 *
 * <p>Zwei Anlässe: einmal direkt nach der Anmeldung als Zusammenfassung, danach in
 * regelmäßigen Abständen, solange die Anwendung läuft.
 *
 * <p>Jede herausgegangene Meldung wird an der Bewerbung vermerkt
 * ({@code letzteErinnerungAm}). Ohne diesen Vermerk käme dieselbe Erinnerung bei jedem
 * Durchlauf erneut — und damit genau das, was man wegklickt statt zu lesen.
 */
@Service
public class ErinnerungsBenachrichtiger {

    private static final Logger log = LoggerFactory.getLogger(ErinnerungsBenachrichtiger.class);

    /** Mehr als das würde den Infobereich zuspammen; der Rest steht im Dashboard. */
    private static final int MAX_EINZELMELDUNGEN = 3;

    private final BewerbungseintragService bewerbungseintragService;
    private final ErinnerungService erinnerungService;
    private final EinstellungService einstellungService;
    private final TrayService trayService;

    public ErinnerungsBenachrichtiger(
            BewerbungseintragService bewerbungseintragService,
            ErinnerungService erinnerungService,
            EinstellungService einstellungService,
            TrayService trayService
    ) {
        this.bewerbungseintragService = bewerbungseintragService;
        this.erinnerungService = erinnerungService;
        this.einstellungService = einstellungService;
        this.trayService = trayService;
    }

    /** Zusammenfassung nach der Anmeldung: wie viele Bewerbungen Aufmerksamkeit brauchen. */
    public void meldeUeberblick() {
        if (!aktiv()) {
            return;
        }
        long faellig = bewerbungseintragService.getAllBewerbungseintraege().stream()
                .filter(erinnerungService::istFaellig)
                .count();
        if (faellig == 0) {
            return;
        }
        trayService.melden("Bewerbungsmanager",
                faellig == 1
                        ? "1 Bewerbung braucht Aufmerksamkeit."
                        : faellig + " Bewerbungen brauchen Aufmerksamkeit.");
    }

    /**
     * Prüft, was seit der letzten Meldung wieder fällig geworden ist, meldet es und hält den
     * Zeitpunkt fest.
     */
    public void pruefeUndMelde() {
        if (!aktiv()) {
            return;
        }
        List<BewerbungseintragResponse> faellige;
        try {
            faellige = erinnerungService.faelligeBenachrichtigungen(
                    bewerbungseintragService.getAllBewerbungseintraege());
        } catch (RuntimeException e) {
            // Eine fehlgeschlagene Erinnerung darf die laufende Anwendung nicht stören.
            log.warn("Erinnerungen konnten nicht geprüft werden", e);
            return;
        }
        if (faellige.isEmpty()) {
            return;
        }

        for (BewerbungseintragResponse application : faellige.stream().limit(MAX_EINZELMELDUNGEN).toList()) {
            trayService.melden("Bewerbungsmanager", erinnerungService.benachrichtigungsText(application));
        }
        if (faellige.size() > MAX_EINZELMELDUNGEN) {
            trayService.melden("Bewerbungsmanager",
                    "und " + (faellige.size() - MAX_EINZELMELDUNGEN) + " weitere Bewerbungen.");
        }

        // Erst nach dem Melden vermerken - sonst verschluckt ein Fehler die Erinnerung dauerhaft.
        faellige.forEach(application -> bewerbungseintragService.markiereErinnerungGesendet(
                application.bewerbungseintragId(), erinnerungService.heute()));
        log.info("{} Erinnerung(en) gemeldet", faellige.size());
    }

    private boolean aktiv() {
        return trayService.istSichtbar() && einstellungService.isBenachrichtigungenAktiv();
    }
}
