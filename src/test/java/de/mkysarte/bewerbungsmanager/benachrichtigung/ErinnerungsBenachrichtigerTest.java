package de.mkysarte.bewerbungsmanager.benachrichtigung;

import de.mkysarte.bewerbungsmanager.bewerbungseintrag.dto.BewerbungseintragResponse;
import de.mkysarte.bewerbungsmanager.bewerbungseintrag.service.BewerbungseintragService;
import de.mkysarte.bewerbungsmanager.einstellung.service.EinstellungService;
import de.mkysarte.bewerbungsmanager.erinnerung.service.ErinnerungService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ErinnerungsBenachrichtigerTest {

    private static final LocalDate HEUTE = LocalDate.of(2026, 9, 20);

    @Mock
    private BewerbungseintragService bewerbungseintragService;
    @Mock
    private ErinnerungService erinnerungService;
    @Mock
    private EinstellungService einstellungService;
    @Mock
    private TrayService trayService;

    private ErinnerungsBenachrichtiger benachrichtiger;

    @BeforeEach
    void setUp() {
        when(trayService.istSichtbar()).thenReturn(true);
        when(einstellungService.isBenachrichtigungenAktiv()).thenReturn(true);
        when(erinnerungService.heute()).thenReturn(HEUTE);
        benachrichtiger = new ErinnerungsBenachrichtiger(
                bewerbungseintragService, erinnerungService, einstellungService, trayService);
    }

    @Test
    void ohneInfobereichWirdNichtsGemeldet() {
        when(trayService.istSichtbar()).thenReturn(false);

        benachrichtiger.pruefeUndMelde();

        verify(trayService, never()).melden(anyString(), anyString());
        verify(bewerbungseintragService, never()).getAllBewerbungseintraege();
    }

    @Test
    void abgeschalteteBenachrichtigungenMeldenNichts() {
        when(einstellungService.isBenachrichtigungenAktiv()).thenReturn(false);

        benachrichtiger.pruefeUndMelde();

        verify(trayService, never()).melden(anyString(), anyString());
    }

    /** Der Vermerk ist das, was die Wiederholung verhindert - er muss zuverlässig gesetzt werden. */
    @Test
    void jedeGemeldeteBewerbungWirdVermerkt() {
        BewerbungseintragResponse eine = bewerbung(1L);
        BewerbungseintragResponse andere = bewerbung(2L);
        when(bewerbungseintragService.getAllBewerbungseintraege()).thenReturn(List.of(eine, andere));
        when(erinnerungService.faelligeBenachrichtigungen(any())).thenReturn(List.of(eine, andere));
        when(erinnerungService.benachrichtigungsText(any())).thenReturn("Musterfirma GmbH — nachfassen");

        benachrichtiger.pruefeUndMelde();

        verify(trayService, times(2)).melden(anyString(), anyString());
        verify(bewerbungseintragService).markiereErinnerungGesendet(1L, HEUTE);
        verify(bewerbungseintragService).markiereErinnerungGesendet(2L, HEUTE);
    }

    /** Bei vielen offenen Punkten nicht den Infobereich zuspammen. */
    @Test
    void vieleFaelligeErgebenEineSammelmeldung() {
        List<BewerbungseintragResponse> fuenf = List.of(
                bewerbung(1L), bewerbung(2L), bewerbung(3L), bewerbung(4L), bewerbung(5L));
        when(bewerbungseintragService.getAllBewerbungseintraege()).thenReturn(fuenf);
        when(erinnerungService.faelligeBenachrichtigungen(any())).thenReturn(fuenf);
        when(erinnerungService.benachrichtigungsText(any())).thenReturn("Musterfirma GmbH");

        benachrichtiger.pruefeUndMelde();

        // 3 Einzelmeldungen + 1 Sammelmeldung, aber alle 5 werden vermerkt
        verify(trayService, times(4)).melden(anyString(), anyString());
        fuenf.forEach(b -> verify(bewerbungseintragService)
                .markiereErinnerungGesendet(eq(b.bewerbungseintragId()), eq(HEUTE)));
    }

    @Test
    void nichtsFaelligMeldetNichts() {
        when(bewerbungseintragService.getAllBewerbungseintraege()).thenReturn(List.of(bewerbung(1L)));
        when(erinnerungService.faelligeBenachrichtigungen(any())).thenReturn(List.of());

        benachrichtiger.pruefeUndMelde();

        verify(trayService, never()).melden(anyString(), anyString());
        verify(bewerbungseintragService, never()).markiereErinnerungGesendet(any(), any());
    }

    /** Ein Datenbankfehler darf die laufende Anwendung nicht stören. */
    @Test
    void fehlerBeimPruefenWirdGeschluckt() {
        when(bewerbungseintragService.getAllBewerbungseintraege())
                .thenThrow(new IllegalStateException("Datenbank weg"));

        benachrichtiger.pruefeUndMelde();

        verify(trayService, never()).melden(anyString(), anyString());
    }

    @Test
    void ueberblickMeldetDieAnzahl() {
        when(bewerbungseintragService.getAllBewerbungseintraege())
                .thenReturn(List.of(bewerbung(1L), bewerbung(2L)));
        when(erinnerungService.istFaellig(any())).thenReturn(true);

        benachrichtiger.meldeUeberblick();

        verify(trayService).melden("Bewerbungsmanager", "2 Bewerbungen brauchen Aufmerksamkeit.");
    }

    @Test
    void ueberblickSchweigtWennNichtsAnsteht() {
        when(bewerbungseintragService.getAllBewerbungseintraege()).thenReturn(List.of(bewerbung(1L)));
        when(erinnerungService.istFaellig(any())).thenReturn(false);

        benachrichtiger.meldeUeberblick();

        verify(trayService, never()).melden(anyString(), anyString());
    }

    private BewerbungseintragResponse bewerbung(Long id) {
        return new BewerbungseintragResponse(
                id, 1L, "Standardcontainer",
                1L, "Musterfirma GmbH",
                1L, "Java Developer",
                null, null, null, null, null, null, null,
                1L, "ABGESCHICKT",
                null, null,
                null, null, null, 42L,
                null, null, null, null,
                false, null, null, null,
                null, null, null
        );
    }
}
