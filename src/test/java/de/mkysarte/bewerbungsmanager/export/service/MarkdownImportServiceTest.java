package de.mkysarte.bewerbungsmanager.export.service;

import de.mkysarte.bewerbungsmanager.bewerbungseintrag.dto.BewerbungseintragResponse;
import de.mkysarte.bewerbungsmanager.bewerbungseintrag.dto.CreateBewerbungseintragRequest;
import de.mkysarte.bewerbungsmanager.bewerbungseintrag.service.BewerbungseintragService;
import de.mkysarte.bewerbungsmanager.common.exception.AppException;
import de.mkysarte.bewerbungsmanager.einstellung.service.EinstellungService;
import de.mkysarte.bewerbungsmanager.erinnerung.dto.ErinnerungsIntervall;
import de.mkysarte.bewerbungsmanager.erinnerung.service.ErinnerungService;
import de.mkysarte.bewerbungsmanager.export.dto.ImportErgebnis;
import de.mkysarte.bewerbungsmanager.status.entity.StatusEntity;
import de.mkysarte.bewerbungsmanager.status.repository.StatusRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Constructor;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MarkdownImportServiceTest {

    private static final ZonedDateTime JETZT =
            ZonedDateTime.of(2026, 9, 20, 12, 0, 0, 0, ZoneId.systemDefault());
    private static final LocalDate HEUTE = JETZT.toLocalDate();

    private static final long ENTWURF_ID = 7L;
    private static final long ABGESCHICKT_ID = 8L;
    private static final long ABSAGE_ID = 9L;

    @Mock
    private BewerbungseintragService bewerbungseintragService;

    @Mock
    private StatusRepository statusRepository;

    @Mock
    private EinstellungService einstellungService;

    private MarkdownImportService importService;
    private MarkdownExportService exportService;

    @BeforeEach
    void setUp() throws Exception {
        when(statusRepository.findByTitel("ENTWURF"))
                .thenReturn(Optional.of(status(ENTWURF_ID, "ENTWURF")));
        when(statusRepository.findByTitel("ABGESCHICKT"))
                .thenReturn(Optional.of(status(ABGESCHICKT_ID, "ABGESCHICKT")));
        when(statusRepository.findByTitel("ABSAGE"))
                .thenReturn(Optional.of(status(ABSAGE_ID, "ABSAGE")));
        when(statusRepository.findByTitel("IRGENDWAS")).thenReturn(Optional.empty());
        when(bewerbungseintragService.getAllBewerbungseintraege()).thenReturn(List.of());
        // Der Import braucht die Id der angelegten Bewerbung, um Datumsangaben nachzutragen.
        when(bewerbungseintragService.createBewerbungseintrag(any()))
                .thenReturn(bestand("Angelegt", "Stelle", "Ort"));

        importService = new MarkdownImportService(bewerbungseintragService, statusRepository);

        when(einstellungService.getNachfassFristTage()).thenReturn(14);
        when(einstellungService.getEntwurfIntervall()).thenReturn(ErinnerungsIntervall.WOECHENTLICH);
        Constructor<ErinnerungService> ctor =
                ErinnerungService.class.getDeclaredConstructor(EinstellungService.class, Clock.class);
        ctor.setAccessible(true);
        exportService = new MarkdownExportService(
                ctor.newInstance(einstellungService,
                        Clock.fixed(JETZT.toInstant(), ZoneId.systemDefault())));
    }

    /**
     * Der Test, an dem die ganze Zusage hängt: Was der Export schreibt, muss der Import lesen
     * können. Driften die beiden auseinander, fällt es hier auf und nirgends sonst.
     */
    @Test
    void exportierteDateiLaesstSichWiederEinlesen() {
        String md = exportService.export(List.of(
                bestand("Musterfirma GmbH", "Java Developer", "Musterstadt"),
                bestand("Beispiel AG", "Fachinformatiker", "Bremen")));

        ImportErgebnis ergebnis = importService.importiere(md);

        assertEquals(2, ergebnis.angelegt(), ergebnis.hinweise().toString());
        assertTrue(ergebnis.ohneBeanstandung(), ergebnis.hinweise().toString());

        List<CreateBewerbungseintragRequest> auftraege = auftraege(2);
        assertEquals("Musterfirma GmbH", auftraege.get(0).firmaName());
        assertEquals("Java Developer", auftraege.get(0).stellenbezeichnung());
        assertEquals("Musterstadt", auftraege.get(0).standort());
        assertEquals("Beispiel AG", auftraege.get(1).firmaName());
    }

    /** Die Vorlage darf nichts anlegen — ihre Beispielzeile steht bewusst eingerückt. */
    @Test
    void unveraenderteVorlageLegtNichtsAn() {
        ImportErgebnis ergebnis = importService.importiere(exportService.vorlage());

        assertEquals(0, ergebnis.angelegt());
        assertTrue(ergebnis.ohneBeanstandung(), ergebnis.hinweise().toString());
        verify(bewerbungseintragService, never()).createBewerbungseintrag(any());
    }

    @Test
    void fremdeTabelleWirdAbgewiesen() {
        String fremd = """
                # Irgendwas

                | Name | Wert |
                | --- | --- |
                | A | B |
                """;

        AppException e = assertThrows(AppException.class, () -> importService.importiere(fremd));

        assertTrue(e.getMessage().contains("Kopfzeile"), e.getMessage());
        assertTrue(e.getMessage().contains("Firma"), e.getMessage());
        verify(bewerbungseintragService, never()).createBewerbungseintrag(any());
    }

    @Test
    void dateiOhneTabelleWirdAbgewiesen() {
        assertThrows(AppException.class, () -> importService.importiere("# Bewerbungen\n\nnichts."));
    }

    @Test
    void falscheSpaltenzahlBetrifftNurDieEineZeile() {
        String md = tabelle(
                "| Zu Kurz GmbH | Entwickler |",
                zeile("Vollständig AG", "Entwickler", "Bremen"));

        ImportErgebnis ergebnis = importService.importiere(md);

        assertEquals(1, ergebnis.angelegt());
        assertEquals(1, ergebnis.hinweise().size());
        assertTrue(ergebnis.hinweise().get(0).contains("statt 11 Spalten"),
                ergebnis.hinweise().toString());
    }

    @Test
    void platzhalterGeltenAlsLeeresFeld() {
        // Gedankenstrich, Geviertstrich, schlichter Bindestrich und leer - alle vier bedeuten
        // dasselbe, denn von Hand oder aus einer KI kommt mal das eine, mal das andere.
        String md = tabelle("| Muster GmbH | Entwickler | Bremen | – | — | - |  | – | – | – | – |");

        ImportErgebnis ergebnis = importService.importiere(md);

        assertEquals(1, ergebnis.angelegt(), ergebnis.hinweise().toString());
        CreateBewerbungseintragRequest auftrag = auftraege(1).get(0);
        assertNull(auftrag.ansprechpartner());
        assertNull(auftrag.email());
        assertNull(auftrag.url());
    }

    @Test
    void maskierterBalkenKommtAlsBalkenAn() {
        String md = tabelle(zeile("Muster \\| Firma GmbH", "Entwickler", "Bremen"));

        ImportErgebnis ergebnis = importService.importiere(md);

        assertEquals(1, ergebnis.angelegt(), ergebnis.hinweise().toString());
        assertEquals("Muster | Firma GmbH", auftraege(1).get(0).firmaName());
    }

    @Test
    void fehlendePflichtfelderWerdenMitZeilennummerGemeldet() {
        String md = tabelle(zeile("Muster GmbH", "Entwickler", null));

        ImportErgebnis ergebnis = importService.importiere(md);

        assertEquals(0, ergebnis.angelegt());
        assertEquals(1, ergebnis.hinweise().size());
        String hinweis = ergebnis.hinweise().get(0);
        assertTrue(hinweis.startsWith("Zeile "), hinweis);
        assertTrue(hinweis.contains("Ort fehlt"), hinweis);
    }

    @Test
    void vorhandeneBewerbungWirdUebersprungen() {
        when(bewerbungseintragService.getAllBewerbungseintraege()).thenReturn(List.of(
                bestand("Musterfirma GmbH", "Java Developer", "Musterstadt")));

        // Klein-/Grossschreibung und Randleerzeichen duerfen keine Rolle spielen.
        ImportErgebnis ergebnis = importService.importiere(
                tabelle(zeile("  musterfirma gmbh ", "JAVA DEVELOPER", "Bremen")));

        assertEquals(0, ergebnis.angelegt());
        assertEquals(1, ergebnis.uebersprungen().size());
        verify(bewerbungseintragService, never()).createBewerbungseintrag(any());
    }

    @Test
    void doppelteZeileInDerselbenDateiWirdNurEinmalAngelegt() {
        String md = tabelle(
                zeile("Muster GmbH", "Entwickler", "Bremen"),
                zeile("Muster GmbH", "Entwickler", "Hamburg"));

        ImportErgebnis ergebnis = importService.importiere(md);

        assertEquals(1, ergebnis.angelegt());
        assertEquals(1, ergebnis.uebersprungen().size());
    }

    @Test
    void ohneStatusspalteEntstehtEinEntwurf() {
        // Eine frisch gefundene Stelle ist keine abgeschickte Bewerbung.
        ImportErgebnis ergebnis = importService.importiere(
                tabelle(zeile("Muster GmbH", "Entwickler", "Bremen")));

        assertEquals(1, ergebnis.angelegt(), ergebnis.hinweise().toString());
        assertEquals(ENTWURF_ID, auftraege(1).get(0).statusId());
    }

    @Test
    void unbekannterStatusFaelltAufEntwurfZurueck() {
        String md = tabelle("| Muster GmbH | Entwickler | Bremen | IRGENDWAS | – | – | – | – "
                + "| – | – | – |");

        ImportErgebnis ergebnis = importService.importiere(md);

        assertEquals(1, ergebnis.angelegt(), ergebnis.hinweise().toString());
        assertEquals(ENTWURF_ID, auftraege(1).get(0).statusId());
    }

    /** Der eigentliche Zweck der Übernahme: eine anderswo geführte Liste einspielen. */
    @Test
    void statusUndAbsendedatumWerdenUebernommen() {
        String md = tabelle("| Muster GmbH | Entwickler | Bremen | ABGESCHICKT | 01.08.2026 "
                + "| 04.08.2026 | 18.08.2026 | – | – | – | – |");

        ImportErgebnis ergebnis = importService.importiere(md);

        assertEquals(1, ergebnis.angelegt(), ergebnis.hinweise().toString());
        CreateBewerbungseintragRequest auftrag = auftraege(1).get(0);
        assertEquals(ABGESCHICKT_ID, auftrag.statusId());
        assertEquals(LocalDate.of(2026, 8, 1), auftrag.erstelltAm());
        verify(bewerbungseintragService).setzeStatusDatum(1L, "ABGESCHICKT",
                LocalDate.of(2026, 8, 4));
    }

    @Test
    void kleinschreibungImStatusStoertNicht() {
        String md = tabelle("| Muster GmbH | Entwickler | Bremen | abgeschickt | – | – | – | – "
                + "| – | – | – |");

        importService.importiere(md);

        assertEquals(ABGESCHICKT_ID, auftraege(1).get(0).statusId());
    }

    /**
     * Bei einer Absage wird das Absendedatum trotzdem eingetragen — abgeschickt wurde die
     * Bewerbung ja, auch wenn sie nie als "abgeschickt" durch die Anwendung gelaufen ist.
     */
    @Test
    void abschlussSpalteWirdZerlegtUndAbsendedatumTrotzdemGesetzt() {
        String md = tabelle("| Muster GmbH | Entwickler | Bremen | ABSAGE | – | 04.08.2026 "
                + "| – | Absage 18.09.2026 | – | – | – |");

        ImportErgebnis ergebnis = importService.importiere(md);

        assertEquals(1, ergebnis.angelegt(), ergebnis.hinweise().toString());
        assertEquals(ABSAGE_ID, auftraege(1).get(0).statusId());
        verify(bewerbungseintragService).setzeStatusDatum(1L, "ABGESCHICKT",
                LocalDate.of(2026, 8, 4));
        verify(bewerbungseintragService).setzeStatusDatum(1L, "ABSAGE",
                LocalDate.of(2026, 9, 18));
    }

    @Test
    void unlesbaresDatumKostetNurDasFeldNichtDieZeile() {
        String md = tabelle("| Muster GmbH | Entwickler | Bremen | ABGESCHICKT | – | gestern "
                + "| – | – | – | – | – |");

        ImportErgebnis ergebnis = importService.importiere(md);

        assertEquals(1, ergebnis.angelegt(), ergebnis.hinweise().toString());
        assertEquals(1, ergebnis.hinweise().size());
        assertTrue(ergebnis.hinweise().get(0).contains("Abgeschickt"),
                ergebnis.hinweise().toString());
        verify(bewerbungseintragService, never())
                .setzeStatusDatum(anyLong(), eq("ABGESCHICKT"), any());
    }

    /** Das Nachfassdatum rechnet die Anwendung selbst — als Eingabe ist es sinnlos. */
    @Test
    void nachfassdatumWirdUeberlesen() {
        String md = tabelle("| Muster GmbH | Entwickler | Bremen | ABGESCHICKT | – | 04.08.2026 "
                + "| 99.99.9999 | – | – | – | – |");

        ImportErgebnis ergebnis = importService.importiere(md);

        assertEquals(1, ergebnis.angelegt());
        assertTrue(ergebnis.ohneBeanstandung(), ergebnis.hinweise().toString());
    }

    @Test
    void zuLangesFeldWirdAbgewiesen() {
        String md = tabelle(zeile("M".repeat(256), "Entwickler", "Bremen"));

        ImportErgebnis ergebnis = importService.importiere(md);

        assertEquals(0, ergebnis.angelegt());
        assertTrue(ergebnis.hinweise().get(0).contains("länger als 255"),
                ergebnis.hinweise().toString());
    }

    /**
     * Der Anlege-Pfad schreibt Ansprechpartner und E-Mail auf die <em>Firma</em>. Käme statt
     * {@code null} ein leerer Text an, würde ein Import gepflegte Kontaktdaten löschen.
     */
    @Test
    void leereSpaltenReichenNullDurchUndKeinenLeerenText() {
        importService.importiere(tabelle(zeile("Muster GmbH", "Entwickler", "Bremen")));

        CreateBewerbungseintragRequest auftrag = auftraege(1).get(0);
        assertNull(auftrag.ansprechpartner());
        assertNull(auftrag.contactPerson());
        assertNull(auftrag.email());
        assertNull(auftrag.telefon());
        assertNull(auftrag.url());
        // Ein abweichender Containername wuerde den vorhandenen Container umbenennen.
        assertNull(auftrag.containerName());
    }

    @Test
    void unbrauchbareMailWirdWeggelassenDieZeileAberAngelegt() {
        String md = tabelle("| Muster GmbH | Entwickler | Bremen | – | – | – | – | – | "
                + "Frau Meier | keine-adresse | – |");

        ImportErgebnis ergebnis = importService.importiere(md);

        assertEquals(1, ergebnis.angelegt(), ergebnis.hinweise().toString());
        CreateBewerbungseintragRequest auftrag = auftraege(1).get(0);
        assertNull(auftrag.email());
        assertEquals("Frau Meier", auftrag.ansprechpartner());
        assertEquals(1, ergebnis.hinweise().size());
    }

    @Test
    void fehlerBeimAnlegenKipptDenRestNicht() {
        when(bewerbungseintragService.createBewerbungseintrag(any()))
                .thenThrow(AppException.badRequest("Kein angemeldeter Benutzer"))
                .thenReturn(bestand("Zweite AG", "Entwickler", "Bremen"));

        ImportErgebnis ergebnis = importService.importiere(tabelle(
                zeile("Erste GmbH", "Entwickler", "Bremen"),
                zeile("Zweite AG", "Entwickler", "Bremen")));

        assertEquals(1, ergebnis.angelegt());
        assertEquals(1, ergebnis.hinweise().size());
        assertTrue(ergebnis.hinweise().get(0).contains("Kein angemeldeter Benutzer"),
                ergebnis.hinweise().toString());
    }

    @Test
    void fehlenderEntwurfStatusWirdGemeldet() {
        when(statusRepository.findByTitel("ENTWURF")).thenReturn(Optional.empty());

        AppException e = assertThrows(AppException.class,
                () -> importService.importiere(tabelle(zeile("Muster GmbH", "Entwickler", "Bremen"))));

        assertTrue(e.getMessage().contains("ENTWURF"), e.getMessage());
    }

    // =====================================================================

    private List<CreateBewerbungseintragRequest> auftraege(int erwartet) {
        ArgumentCaptor<CreateBewerbungseintragRequest> captor =
                ArgumentCaptor.forClass(CreateBewerbungseintragRequest.class);
        verify(bewerbungseintragService, times(erwartet)).createBewerbungseintrag(captor.capture());
        return captor.getAllValues();
    }

    /** Baut eine vollständige Datei: Kopf, Trennzeile und die übergebenen Datenzeilen. */
    private String tabelle(String... datenzeilen) {
        StringBuilder md = new StringBuilder("# Bewerbungen\n\n");
        md.append(MarkdownTabelle.kopfzeile()).append('\n');
        md.append(MarkdownTabelle.trennzeile()).append('\n');
        for (String zeile : datenzeilen) {
            md.append(zeile).append('\n');
        }
        return md.toString();
    }

    /** Eine Datenzeile mit den drei Pflichtfeldern; der Rest bleibt leer. */
    private String zeile(String firma, String stelle, String ort) {
        StringBuilder z = new StringBuilder("|");
        for (String spalte : MarkdownTabelle.SPALTEN) {
            String wert = switch (spalte) {
                case "Firma" -> firma;
                case "Stelle" -> stelle;
                case "Ort" -> ort;
                default -> null;
            };
            z.append(' ').append(wert == null ? MarkdownTabelle.LEER : wert).append(" |");
        }
        return z.toString();
    }

    private StatusEntity status(long id, String titel) {
        StatusEntity s = new StatusEntity();
        s.setStatusId(id);
        s.setTitel(titel);
        return s;
    }

    private BewerbungseintragResponse bestand(String firma, String stelle, String ort) {
        return new BewerbungseintragResponse(
                1L, 1L, "Standardcontainer", 1L, firma,
                1L, stelle,
                null, null, null, null, null, ort, null,
                1L, "ENTWURF", null, null,
                null, null, null, null,
                HEUTE.minusDays(10), null, HEUTE.minusDays(10).atStartOfDay(), null,
                false, null, null, null,
                null, null, null);
    }
}
