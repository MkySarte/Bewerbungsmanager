package de.mkysarte.bewerbungsmanager.erinnerung.service;

import de.mkysarte.bewerbungsmanager.bewerbungseintrag.dto.BewerbungseintragResponse;
import de.mkysarte.bewerbungsmanager.einstellung.service.EinstellungService;
import de.mkysarte.bewerbungsmanager.erinnerung.dto.Erinnerung;
import de.mkysarte.bewerbungsmanager.erinnerung.dto.ErinnerungsIntervall;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ErinnerungServiceTest {

    /** Feste Uhr, damit Fristen ohne Warten prüfbar sind. */
    private static final ZonedDateTime JETZT =
            ZonedDateTime.of(2026, 9, 20, 12, 0, 0, 0, ZoneId.systemDefault());
    private static final LocalDate HEUTE = JETZT.toLocalDate();

    @Mock
    private EinstellungService einstellungService;

    private ErinnerungService erinnerungService;

    @BeforeEach
    void setUp() {
        when(einstellungService.getNachfassFristTage()).thenReturn(14);
        when(einstellungService.isErinnerungAnschreibenAktiv()).thenReturn(true);
        when(einstellungService.isBenachrichtigungenAktiv()).thenReturn(true);
        when(einstellungService.getEntwurfIntervall()).thenReturn(ErinnerungsIntervall.WOECHENTLICH);
        erinnerungService = new ErinnerungService(
                einstellungService, Clock.fixed(JETZT.toInstant(), ZoneId.systemDefault()));
    }

    // =====================================================================
    //  Nachfassdatum für die Karte
    // =====================================================================

    @Test
    void nachfassdatumIstAbschickdatumPlusFrist() {
        BewerbungseintragResponse abgeschickt = abgeschicktVor(3);

        assertEquals(HEUTE.minusDays(3).plusDays(14), erinnerungService.nachfassenAb(abgeschickt));
    }

    @Test
    void eigeneFristVerschiebtDasNachfassdatum() {
        BewerbungseintragResponse mitFrist = abgeschicktVor(3, 7, null);

        assertEquals(HEUTE.minusDays(3).plusDays(7), erinnerungService.nachfassenAb(mitFrist));
    }

    @Test
    void ohneAbschickenGibtEsKeinNachfassdatum() {
        assertNull(erinnerungService.nachfassenAb(entwurfSeit(5)));
    }

    /** Nach einer Absage wartet man auf nichts mehr. */
    @Test
    void nachAbsageGibtEsKeinNachfassdatumMehr() {
        BewerbungseintragResponse abgesagt = bauen("ABSAGE", 42L,
                HEUTE.minusDays(40), HEUTE.minusDays(40).atStartOfDay(), null, null, null);

        assertNull(erinnerungService.nachfassenAb(abgesagt));
        assertFalse(erinnerungService.istFaellig(abgesagt));
    }

    @Test
    void vorDemTerminIstNichtsFaellig() {
        assertFalse(erinnerungService.nachfassenFaellig(abgeschicktVor(13)));
    }

    @Test
    void amTerminIstEsFaellig() {
        assertTrue(erinnerungService.nachfassenFaellig(abgeschicktVor(14)));
    }

    // =====================================================================
    //  Hinweise auf der Karte
    // =====================================================================

    @Test
    void fehlendesAnschreibenWirdGemeldet() {
        BewerbungseintragResponse ohneAnschreiben = bauen("ENTWURF", null,
                HEUTE.minusDays(1), null, null, null, null);

        List<Erinnerung> erinnerungen = erinnerungService.fuer(ohneAnschreiben);

        assertEquals(1, erinnerungen.size());
        assertEquals(Erinnerung.Art.ANSCHREIBEN_FEHLT, erinnerungen.getFirst().art());
    }

    @Test
    void abgeschalteteErinnerungMeldetFehlendesAnschreibenNicht() {
        when(einstellungService.isErinnerungAnschreibenAktiv()).thenReturn(false);
        BewerbungseintragResponse ohneAnschreiben = bauen("ENTWURF", null,
                HEUTE.minusDays(1), null, null, null, null);

        assertFalse(erinnerungService.istFaellig(ohneAnschreiben));
    }

    // =====================================================================
    //  Benachrichtigungen: Rhythmus und Wiederholungsschutz
    // =====================================================================

    /** Der Kern des Wiederholungsschutzes: am selben Tag nie zweimal. */
    @Test
    void amSelbenTagWirdNichtZweimalBenachrichtigt() {
        BewerbungseintragResponse heuteSchonGemeldet =
                abgeschicktVor(20, null, HEUTE);

        assertFalse(erinnerungService.benachrichtigungFaellig(heuteSchonGemeldet));
    }

    @Test
    void nachfassenMeldetSichAmNaechstenTagWieder() {
        BewerbungseintragResponse gesternGemeldet =
                abgeschicktVor(20, null, HEUTE.minusDays(1));

        assertTrue(erinnerungService.benachrichtigungFaellig(gesternGemeldet));
    }

    @Test
    void taeglichMeldetSichTaeglich() {
        BewerbungseintragResponse entwurf = entwurfSeit(10, ErinnerungsIntervall.TAEGLICH, HEUTE.minusDays(1));

        assertTrue(erinnerungService.benachrichtigungFaellig(entwurf));
    }

    @Test
    void woechentlichMeldetSichNichtNachEinemTag() {
        BewerbungseintragResponse entwurf =
                entwurfSeit(10, ErinnerungsIntervall.WOECHENTLICH, HEUTE.minusDays(1));

        assertFalse(erinnerungService.benachrichtigungFaellig(entwurf));
    }

    @Test
    void woechentlichMeldetSichNachSiebenTagen() {
        BewerbungseintragResponse entwurf =
                entwurfSeit(30, ErinnerungsIntervall.WOECHENTLICH, HEUTE.minusDays(7));

        assertTrue(erinnerungService.benachrichtigungFaellig(entwurf));
    }

    @Test
    void monatlichMeldetSichNichtNachEinerWoche() {
        BewerbungseintragResponse entwurf =
                entwurfSeit(60, ErinnerungsIntervall.MONATLICH, HEUTE.minusDays(7));

        assertFalse(erinnerungService.benachrichtigungFaellig(entwurf));
    }

    @Test
    void ausMeldetSichNie() {
        BewerbungseintragResponse entwurf =
                entwurfSeit(365, ErinnerungsIntervall.AUS, HEUTE.minusDays(300));

        assertFalse(erinnerungService.benachrichtigungFaellig(entwurf));
    }

    /** Ein frisch angelegter Entwurf soll nicht sofort melden. */
    @Test
    void frischerEntwurfMeldetSichNichtSofort() {
        BewerbungseintragResponse entwurf = entwurfSeit(0, ErinnerungsIntervall.WOECHENTLICH, null);

        assertFalse(erinnerungService.benachrichtigungFaellig(entwurf));
    }

    @Test
    void abgeschalteteBenachrichtigungenLiefernNichts() {
        when(einstellungService.isBenachrichtigungenAktiv()).thenReturn(false);

        assertTrue(erinnerungService.faelligeBenachrichtigungen(
                List.of(abgeschicktVor(30, null, null))).isEmpty());
    }

    @Test
    void faelligeBenachrichtigungenFiltertRichtig() {
        List<BewerbungseintragResponse> alle = List.of(
                abgeschicktVor(30, null, null),   // fällig
                abgeschicktVor(3),                 // Frist läuft noch
                abgeschicktVor(30, null, HEUTE));  // heute schon gemeldet

        assertEquals(1, erinnerungService.faelligeBenachrichtigungen(alle).size());
    }

    // =====================================================================
    //  Fristen und Rhythmen
    // =====================================================================

    @Test
    void eigeneFristSchlaegtDenGlobalenStandard() {
        assertEquals(14, erinnerungService.effektiveFristTage(abgeschicktVor(1)));
        assertEquals(7, erinnerungService.effektiveFristTage(abgeschicktVor(1, 7, null)));
    }

    @Test
    void eigenerRhythmusSchlaegtDenGlobalenStandard() {
        assertEquals(ErinnerungsIntervall.WOECHENTLICH,
                erinnerungService.effektivesIntervall(entwurfSeit(1)));
        assertEquals(ErinnerungsIntervall.TAEGLICH,
                erinnerungService.effektivesIntervall(
                        entwurfSeit(1, ErinnerungsIntervall.TAEGLICH, null)));
    }

    @Test
    void unbekannterRhythmusFaelltAufDenStandardZurueck() {
        BewerbungseintragResponse kaputt = bauen("ENTWURF", 42L, HEUTE.minusDays(1),
                null, null, "GIBT_ES_NICHT", null);

        assertEquals(ErinnerungsIntervall.WOECHENTLICH, erinnerungService.effektivesIntervall(kaputt));
    }

    // =====================================================================

    private BewerbungseintragResponse entwurfSeit(int tage) {
        return entwurfSeit(tage, null, null);
    }

    private BewerbungseintragResponse entwurfSeit(int tage, ErinnerungsIntervall intervall,
                                                  LocalDate letzteErinnerung) {
        return bauen("ENTWURF", 42L, HEUTE.minusDays(tage), null,
                null, intervall == null ? null : intervall.name(), letzteErinnerung);
    }

    private BewerbungseintragResponse abgeschicktVor(int tage) {
        return abgeschicktVor(tage, null, null);
    }

    private BewerbungseintragResponse abgeschicktVor(int tage, Integer frist, LocalDate letzteErinnerung) {
        return bauen("ABGESCHICKT", 42L, HEUTE.minusDays(tage),
                HEUTE.minusDays(tage).atStartOfDay(), frist, null, letzteErinnerung);
    }

    private BewerbungseintragResponse bauen(String statusTitel, Long anschreibenId, LocalDate erstelltAm,
                                            LocalDateTime abgeschicktAm, Integer frist,
                                            String intervall, LocalDate letzteErinnerung) {
        return new BewerbungseintragResponse(
                1L, 1L, "Standardcontainer",
                1L, "Musterfirma GmbH",
                1L, "Java Developer",
                null, null, null, null, null, null, null,
                1L, statusTitel,
                null, null,
                null, null, null, anschreibenId,
                erstelltAm, null, erstelltAm == null ? null : erstelltAm.atStartOfDay(), null,
                false, frist, intervall, letzteErinnerung,
                abgeschicktAm,
                "ABSAGE".equalsIgnoreCase(statusTitel) ? abgeschicktAm : null,
                "ERFOLG".equalsIgnoreCase(statusTitel) ? abgeschicktAm : null
        );
    }
}
