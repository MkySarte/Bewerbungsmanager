package de.mkysarte.bewerbungsmanager.export.service;

import de.mkysarte.bewerbungsmanager.bewerbungseintrag.dto.BewerbungseintragResponse;
import de.mkysarte.bewerbungsmanager.einstellung.service.EinstellungService;
import de.mkysarte.bewerbungsmanager.erinnerung.dto.ErinnerungsIntervall;
import de.mkysarte.bewerbungsmanager.erinnerung.service.ErinnerungService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MarkdownExportServiceTest {

    private static final ZonedDateTime JETZT =
            ZonedDateTime.of(2026, 9, 20, 12, 0, 0, 0, ZoneId.systemDefault());
    private static final LocalDate HEUTE = JETZT.toLocalDate();

    private static final int SPALTEN = 11;

    @Mock
    private EinstellungService einstellungService;

    private MarkdownExportService exportService;

    @BeforeEach
    void setUp() throws Exception {
        when(einstellungService.getNachfassFristTage()).thenReturn(14);
        when(einstellungService.getEntwurfIntervall()).thenReturn(ErinnerungsIntervall.WOECHENTLICH);

        // Paketprivater Konstruktor mit fester Uhr - so ist "Stand:" im Test vorhersehbar.
        Constructor<ErinnerungService> ctor =
                ErinnerungService.class.getDeclaredConstructor(EinstellungService.class, Clock.class);
        ctor.setAccessible(true);
        ErinnerungService erinnerungService = ctor.newInstance(
                einstellungService, Clock.fixed(JETZT.toInstant(), ZoneId.systemDefault()));

        exportService = new MarkdownExportService(erinnerungService);
    }

    @Test
    void kopfzeileNenntDatumUndAnzahl() {
        String md = exportService.export(List.of(bewerbung("Musterfirma GmbH"), bewerbung("Beispiel AG")));

        assertTrue(md.startsWith("# Bewerbungen"), md);
        assertTrue(md.contains("Stand: 20.09.2026 — 2 Bewerbungen"), md);
    }

    @Test
    void eineBewerbungWirdImSingularGezaehlt() {
        assertTrue(exportService.export(List.of(bewerbung("Musterfirma GmbH")))
                .contains("— 1 Bewerbung\n"));
    }

    @Test
    void kopfUndTrennzeileHabenDieGleicheSpaltenzahl() {
        String[] zeilen = exportService.export(List.of()).split("\n");
        String kopf = zeilenMitBalken(zeilen, 0);
        String trenner = zeilenMitBalken(zeilen, 1);

        assertEquals(SPALTEN, zaehleZellen(kopf), "Kopfzeile: " + kopf);
        assertEquals(SPALTEN, zaehleZellen(trenner), "Trennzeile: " + trenner);
        assertTrue(kopf.contains("Firma"), kopf);
        assertTrue(kopf.contains("Ansprechpartner"), kopf);
        assertTrue(kopf.contains("Link"), kopf);
    }

    @Test
    void leereListeErgibtKopfOhneDatenzeilen() {
        String md = exportService.export(List.of());

        assertTrue(md.contains("— 0 Bewerbungen"), md);
        // Nur Kopf- und Trennzeile
        assertEquals(2, md.lines().filter(l -> l.startsWith("|")).count(), md);
    }

    @Test
    void nullListeWirdWieLeereListeBehandelt() {
        assertTrue(exportService.export(null).contains("— 0 Bewerbungen"));
    }

    /** Ein Balken im Text würde die Zelle vorzeitig beenden und die Tabelle verschieben. */
    @Test
    void balkenImTextWirdMaskiert() {
        String md = exportService.export(List.of(bewerbung("Muster | Firma GmbH")));

        String datenzeile = datenzeile(md);
        assertTrue(datenzeile.contains("Muster \\| Firma GmbH"), datenzeile);
        assertEquals(SPALTEN, zaehleZellen(datenzeile), "Spaltenzahl darf sich nicht ändern");
    }

    /** Ein Zeilenumbruch im Feld würde die Tabellenzeile zerreißen. */
    @Test
    void zeilenumbruchImFeldWirdZuEinemLeerzeichen() {
        BewerbungseintragResponse mitUmbruch = bauen("Musterfirma GmbH",
                "Java Developer\nmit Schwerpunkt Backend", null, null, null);

        String datenzeile = datenzeile(exportService.export(List.of(mitUmbruch)));

        assertTrue(datenzeile.contains("Java Developer mit Schwerpunkt Backend"), datenzeile);
        assertEquals(1, exportService.export(List.of(mitUmbruch)).lines()
                .filter(l -> l.contains("Musterfirma GmbH")).count());
    }

    @Test
    void fehlendeWerteErscheinenAlsGedankenstrich() {
        String datenzeile = datenzeile(exportService.export(List.of(
                bauen("Musterfirma GmbH", null, null, null, null))));

        assertTrue(datenzeile.contains("–"), datenzeile);
        assertEquals(SPALTEN, zaehleZellen(datenzeile));
    }

    @Test
    void nachfassdatumWirdBerechnet() {
        BewerbungseintragResponse abgeschickt = bauen("Musterfirma GmbH", "Java Developer",
                "ABGESCHICKT", HEUTE.minusDays(4).atStartOfDay(), null);

        String datenzeile = datenzeile(exportService.export(List.of(abgeschickt)));

        assertTrue(datenzeile.contains("16.09.2026"), "Abschickdatum fehlt: " + datenzeile);
        // 16.09. + 14 Tage Standardfrist
        assertTrue(datenzeile.contains("30.09.2026"), "Nachfassdatum fehlt: " + datenzeile);
    }

    @Test
    void absageErscheintInDerAbschlussSpalte() {
        BewerbungseintragResponse abgesagt = bauen("Musterfirma GmbH", "Java Developer",
                "ABSAGE", null, HEUTE.minusDays(2).atStartOfDay());

        assertTrue(datenzeile(exportService.export(List.of(abgesagt))).contains("Absage 18.09.2026"));
    }

    @Test
    void erfolgErscheintInDerAbschlussSpalte() {
        BewerbungseintragResponse erfolgreich = new BewerbungseintragResponse(
                1L, 1L, "Standardcontainer", 1L, "Musterfirma GmbH",
                1L, "Java Developer",
                null, null, null, null, null, null, null,
                1L, "ERFOLG", null, null,
                null, null, null, 42L,
                HEUTE.minusDays(10), null, HEUTE.minusDays(10).atStartOfDay(), null,
                false, null, null, null,
                null, null, HEUTE.minusDays(1).atStartOfDay());

        assertTrue(datenzeile(exportService.export(List.of(erfolgreich))).contains("Erfolg 19.09.2026"));
    }

    @Test
    void kontaktspaltenWerdenGefuellt() {
        BewerbungseintragResponse mitKontakt = new BewerbungseintragResponse(
                1L, 1L, "Standardcontainer", 1L, "Musterfirma GmbH",
                1L, "Java Developer",
                "Max Mustermann", "Max Mustermann", null, null, "bewerbung@example.org",
                "Musterstadt", "Musterstadt",
                1L, "ENTWURF", null, "https://www.example.org/jobs/1",
                null, null, null, 42L,
                HEUTE, null, HEUTE.atStartOfDay(), null,
                false, null, null, null, null, null, null);

        String datenzeile = datenzeile(exportService.export(List.of(mitKontakt)));

        assertTrue(datenzeile.contains("Max Mustermann"), datenzeile);
        assertTrue(datenzeile.contains("bewerbung@example.org"), datenzeile);
        assertTrue(datenzeile.contains("https://www.example.org/jobs/1"), datenzeile);
        assertTrue(datenzeile.contains("Musterstadt"), datenzeile);
    }

    @Test
    void dateinameEnthaeltDasDatum() {
        assertEquals("bewerbungen-2026-09-20.md", exportService.buildFileName());
    }

    @Test
    void entwurfBekommtKeinNachfassdatum() {
        String datenzeile = datenzeile(exportService.export(List.of(
                bauen("Musterfirma GmbH", "Java Developer", "ENTWURF", null, null))));

        // Es darf kein Datum im September 2026 ausser dem Entwurfsdatum auftauchen
        assertFalse(datenzeile.contains("04.10.2026"), datenzeile);
    }

    // =====================================================================

    private String datenzeile(String md) {
        return md.lines()
                .filter(l -> l.startsWith("|") && !l.contains("---") && !l.contains("Firma |"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Keine Datenzeile in:\n" + md));
    }

    private String zeilenMitBalken(String[] zeilen, int index) {
        return java.util.Arrays.stream(zeilen).filter(l -> l.startsWith("|")).toList().get(index);
    }

    /** Zellen zählen: die Zeile beginnt und endet mit einem Balken. */
    private int zaehleZellen(String zeile) {
        return zeile.split("(?<!\\\\)\\|", -1).length - 2;
    }

    private BewerbungseintragResponse bewerbung(String firma) {
        return bauen(firma, "Java Developer", null, null, null);
    }

    private BewerbungseintragResponse bauen(String firma, String stelle, String status,
                                            LocalDateTime abgeschicktAm, LocalDateTime absageAm) {
        return new BewerbungseintragResponse(
                1L, 1L, "Standardcontainer", 1L, firma,
                1L, stelle,
                null, null, null, null, null, null, null,
                1L, status == null ? "ENTWURF" : status, null, null,
                null, null, null, 42L,
                HEUTE.minusDays(10), null, HEUTE.minusDays(10).atStartOfDay(), null,
                false, null, null, null,
                abgeschicktAm, absageAm, null);
    }
}
