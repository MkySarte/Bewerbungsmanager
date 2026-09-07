package de.mkysarte.bewerbungsmanager.bewerbungseintrag.service;

import de.mkysarte.bewerbungsmanager.common.exception.AppException;
import de.mkysarte.bewerbungsmanager.common.exception.ErrorCode;
import de.mkysarte.bewerbungsmanager.bewerbungscontainer.entity.BewerbungscontainerEntity;
import de.mkysarte.bewerbungsmanager.bewerbungscontainer.repository.BewerbungscontainerRepository;
import de.mkysarte.bewerbungsmanager.bewerbungseintrag.dto.BewerbungseintragResponse;
import de.mkysarte.bewerbungsmanager.bewerbungseintrag.dto.CreateBewerbungseintragRequest;
import de.mkysarte.bewerbungsmanager.bewerbungseintrag.dto.PatchBewerbungseintragRequest;
import de.mkysarte.bewerbungsmanager.bewerbungseintrag.dto.UpdateBewerbungseintragRequest;
import de.mkysarte.bewerbungsmanager.bewerbungseintrag.entity.BewerbungseintragEntity;
import de.mkysarte.bewerbungsmanager.bewerbungseintrag.repository.BewerbungseintragRepository;
import de.mkysarte.bewerbungsmanager.document.repository.DocumentRepository;
import de.mkysarte.bewerbungsmanager.firma.entity.FirmaEntity;
import de.mkysarte.bewerbungsmanager.firma.repository.FirmaRepository;
import de.mkysarte.bewerbungsmanager.bewerbungseintrag.repository.StatusVerlaufRepository;
import de.mkysarte.bewerbungsmanager.bewerbungseintrag.entity.StatusVerlaufEntity;
import de.mkysarte.bewerbungsmanager.status.entity.StatusEntity;
import de.mkysarte.bewerbungsmanager.status.repository.StatusRepository;
import de.mkysarte.bewerbungsmanager.stellenausschreibung.entity.StellenausschreibungEntity;
import de.mkysarte.bewerbungsmanager.stellenausschreibung.repository.StellenausschreibungRepository;
import de.mkysarte.bewerbungsmanager.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BewerbungseintragServiceTest {

    @Mock
    private BewerbungseintragRepository bewerbungseintragRepository;

    @Mock
    private BewerbungscontainerRepository bewerbungscontainerRepository;

    @Mock
    private FirmaRepository firmaRepository;

    @Mock
    private StellenausschreibungRepository stellenausschreibungRepository;

    @Mock
    private StatusRepository statusRepository;

    @Mock
    private StatusVerlaufRepository statusVerlaufRepository;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private BewerbungseintragService bewerbungseintragService;

    @Test
    void createBewerbungseintragShouldReturnCreatedEntity() {
        CreateBewerbungseintragRequest request = new CreateBewerbungseintragRequest(
                1L,
                2L,
                3L,
                4L,
                null,
                null,
                null,
                "Max Mustermann",
                null,
                "+491234567",
                null,
                "max@acme.de",
                "Berlin",
                null,
                "Notiz",
                "https://firma.de/job",
                null,
                null,
                null,
                null,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 2)
        , null, null, null, null);
        BewerbungscontainerEntity container = new BewerbungscontainerEntity();
        container.setBewerbungscontainerId(1L);
        when(bewerbungscontainerRepository.findById(1L)).thenReturn(Optional.of(container));

        FirmaEntity firma = new FirmaEntity();
        firma.setFirmaId(2L);
        firma.setName("Acme");
        firma.setKontaktPerson("Alt");
        firma.setTelefon("000");
        firma.setEmail("alt@acme.de");
        when(firmaRepository.findById(2L)).thenReturn(Optional.of(firma));

        StellenausschreibungEntity stelle = new StellenausschreibungEntity();
        stelle.setStellenausschreibungId(3L);
        stelle.setFirmaId(2L);
        stelle.setTitel("Backend Engineer");
        stelle.setOrt("Hamburg");
        when(stellenausschreibungRepository.findById(3L)).thenReturn(Optional.of(stelle));
        when(statusRepository.existsById(4L)).thenReturn(true);
        when(bewerbungseintragRepository.save(any(BewerbungseintragEntity.class))).thenAnswer(invocation -> {
            BewerbungseintragEntity entity = invocation.getArgument(0);
            entity.setBewerbungseintragId(50L);
            entity.setCreatedAt(LocalDateTime.of(2026, 1, 1, 10, 0));
            entity.setUpdatedAt(LocalDateTime.of(2026, 1, 1, 10, 0));
            return entity;
        });

        BewerbungseintragResponse response = bewerbungseintragService.createBewerbungseintrag(request);

        assertEquals(50L, response.bewerbungseintragId());
        assertEquals(2L, response.firmaId());
        assertEquals("max@acme.de", response.email());
        assertEquals("Berlin", response.location());
        verify(bewerbungseintragRepository).save(any(BewerbungseintragEntity.class));
    }

    @Test
    void createBewerbungseintragShouldThrowBadRequestWhenContainerMissing() {
        CreateBewerbungseintragRequest request = new CreateBewerbungseintragRequest(
                99L,
                2L,
                3L,
                4L,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        , null, null, null, null);
        when(bewerbungscontainerRepository.findById(99L)).thenReturn(Optional.empty());

        AppException exception = assertThrows(
                AppException.class,
                () -> bewerbungseintragService.createBewerbungseintrag(request)
        );

        assertEquals(ErrorCode.BAD_REQUEST, exception.getErrorCode());
        verify(bewerbungseintragRepository, never()).save(any(BewerbungseintragEntity.class));
    }

    @Test
    void getBewerbungseintragByIdShouldThrowNotFoundWhenMissing() {
        when(bewerbungseintragRepository.findById(77L)).thenReturn(Optional.empty());

        AppException exception = assertThrows(
                AppException.class,
                () -> bewerbungseintragService.getBewerbungseintragById(77L)
        );

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void updateBewerbungseintragShouldReturnUpdatedEntity() {
        UpdateBewerbungseintragRequest request = new UpdateBewerbungseintragRequest(
                1L,
                2L,
                3L,
                4L,
                "Erika Muster",
                null,
                "+498765432",
                null,
                "erika@acme.de",
                "Muenchen",
                null,
                "Update",
                "https://firma.de/job",
                null,
                null,
                null,
                null,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 3)
        , null, null, null, null);

        BewerbungseintragEntity existing = new BewerbungseintragEntity();
        existing.setBewerbungseintragId(50L);
        existing.setFirmaId(2L);
        existing.setStellenausschreibungId(3L);
        when(bewerbungseintragRepository.findById(50L)).thenReturn(Optional.of(existing));
        when(bewerbungscontainerRepository.existsById(1L)).thenReturn(true);
        when(firmaRepository.existsById(2L)).thenReturn(true);
        when(stellenausschreibungRepository.existsById(3L)).thenReturn(true);
        when(statusRepository.existsById(4L)).thenReturn(true);

        FirmaEntity firma = new FirmaEntity();
        firma.setFirmaId(2L);
        when(firmaRepository.findById(2L)).thenReturn(Optional.of(firma));

        StellenausschreibungEntity stelle = new StellenausschreibungEntity();
        stelle.setStellenausschreibungId(3L);
        when(stellenausschreibungRepository.findById(3L)).thenReturn(Optional.of(stelle));
        when(bewerbungseintragRepository.save(existing)).thenReturn(existing);

        BewerbungseintragResponse response = bewerbungseintragService.updateBewerbungseintrag(50L, request);

        assertEquals(50L, response.bewerbungseintragId());
        assertEquals("Update", response.notiz());
        assertEquals("erika@acme.de", response.email());
        assertEquals("Muenchen", response.standort());
        verify(bewerbungseintragRepository).save(existing);
    }

    @Test
    void updateBewerbungseintragShouldThrowNotFoundWhenMissing() {
        UpdateBewerbungseintragRequest request = new UpdateBewerbungseintragRequest(
                1L,
                2L,
                3L,
                4L,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "Update",
                null,
                null,
                null,
                null,
                null,
                null,
                null
        , null, null, null, null);
        when(bewerbungseintragRepository.findById(999L)).thenReturn(Optional.empty());

        AppException exception = assertThrows(
                AppException.class,
                () -> bewerbungseintragService.updateBewerbungseintrag(999L, request)
        );

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void patchBewerbungseintragShouldUpdateProvidedFieldsOnly() {
        PatchBewerbungseintragRequest request = new PatchBewerbungseintragRequest(
                null,
                null,
                null,
                5L,
                "Patch Kontakt",
                null,
                null,
                "+49000111",
                "patch@acme.de",
                null,
                "Koeln",
                "Patch-Notiz",
                null,
                null,
                null,
                null,
                null,
                null,
                LocalDate.of(2026, 1, 4)
        , null, null, null, null);

        BewerbungseintragEntity existing = new BewerbungseintragEntity();
        existing.setBewerbungseintragId(50L);
        existing.setBewerbungscontainerId(1L);
        existing.setFirmaId(2L);
        existing.setStellenausschreibungId(3L);
        existing.setStatusId(4L);
        existing.setNotiz("Alt");
        existing.setErstelltAm(LocalDate.of(2026, 1, 1));
        existing.setAktualisiertAm(LocalDate.of(2026, 1, 2));

        when(bewerbungseintragRepository.findById(50L)).thenReturn(Optional.of(existing));
        when(bewerbungscontainerRepository.existsById(1L)).thenReturn(true);
        when(firmaRepository.existsById(2L)).thenReturn(true);
        when(stellenausschreibungRepository.existsById(3L)).thenReturn(true);
        when(statusRepository.existsById(5L)).thenReturn(true);

        FirmaEntity firma = new FirmaEntity();
        firma.setFirmaId(2L);
        when(firmaRepository.findById(2L)).thenReturn(Optional.of(firma));

        StellenausschreibungEntity stelle = new StellenausschreibungEntity();
        stelle.setStellenausschreibungId(3L);
        when(stellenausschreibungRepository.findById(3L)).thenReturn(Optional.of(stelle));
        when(bewerbungseintragRepository.save(existing)).thenReturn(existing);

        BewerbungseintragResponse response = bewerbungseintragService.patchBewerbungseintrag(50L, request);

        assertEquals(5L, response.statusId());
        assertEquals("Patch-Notiz", response.notiz());
        assertEquals(LocalDate.of(2026, 1, 4), response.aktualisiertAm());
        assertEquals("patch@acme.de", response.email());
        assertEquals("Koeln", response.location());
        verify(bewerbungseintragRepository).save(existing);
    }

    @Test
    void patchBewerbungseintragShouldThrowBadRequestWhenNoFieldsProvided() {
        PatchBewerbungseintragRequest request = new PatchBewerbungseintragRequest(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        , null, null, null, null);

        BewerbungseintragEntity existing = new BewerbungseintragEntity();
        existing.setBewerbungseintragId(50L);
        when(bewerbungseintragRepository.findById(50L)).thenReturn(Optional.of(existing));

        AppException exception = assertThrows(
                AppException.class,
                () -> bewerbungseintragService.patchBewerbungseintrag(50L, request)
        );

        assertEquals(ErrorCode.BAD_REQUEST, exception.getErrorCode());
    }

    @Test
    void createBewerbungseintragShouldResolveByNamesWithoutDuplicates() {
        CreateBewerbungseintragRequest request = new CreateBewerbungseintragRequest(
                1L,
                null,
                null,
                4L,
                null,
                "Acme GmbH",
                "Backend Engineer",
                null,
                "Ansprechpartner",
                null,
                "+490999",
                "kontakt@acme.de",
                null,
                "Stuttgart",
                "Notiz",
                null,
                null,
                null,
                null,
                null,
                null,
                null
        , null, null, null, null);

        BewerbungscontainerEntity container = new BewerbungscontainerEntity();
        container.setBewerbungscontainerId(1L);
        container.setName("Standardcontainer");
        when(bewerbungscontainerRepository.findById(1L)).thenReturn(Optional.of(container));
        when(statusRepository.existsById(4L)).thenReturn(true);

        FirmaEntity firma = new FirmaEntity();
        firma.setFirmaId(2L);
        firma.setName("Acme GmbH");
        firma.setKontaktPerson("Alt");
        firma.setTelefon("Alt");
        firma.setEmail("alt@acme.de");
        when(firmaRepository.findFirstByNameIgnoreCase("Acme GmbH")).thenReturn(Optional.of(firma));
        when(firmaRepository.findById(2L)).thenReturn(Optional.of(firma));

        StellenausschreibungEntity stelle = new StellenausschreibungEntity();
        stelle.setStellenausschreibungId(3L);
        stelle.setFirmaId(2L);
        stelle.setTitel("Backend Engineer");
        stelle.setOrt("Altort");
        when(stellenausschreibungRepository.findFirstByFirmaIdAndTitelIgnoreCase(2L, "Backend Engineer"))
                .thenReturn(Optional.of(stelle));
        when(stellenausschreibungRepository.findById(3L)).thenReturn(Optional.of(stelle));

        when(bewerbungseintragRepository.save(any(BewerbungseintragEntity.class))).thenAnswer(invocation -> {
            BewerbungseintragEntity entity = invocation.getArgument(0);
            entity.setBewerbungseintragId(50L);
            return entity;
        });

        BewerbungseintragResponse response = bewerbungseintragService.createBewerbungseintrag(request);

        assertEquals(2L, response.firmaId());
        assertEquals("Acme GmbH", response.firmaName());
        assertEquals(3L, response.stellenausschreibungId());
        assertEquals("Backend Engineer", response.stellenbezeichnung());
        assertEquals("kontakt@acme.de", response.email());
        assertEquals("Stuttgart", response.standort());
    }

    @Test
    void patchOnlyNotizShouldKeepKontaktAndStandortUnchanged() {
        PatchBewerbungseintragRequest request = new PatchBewerbungseintragRequest(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "Neue Notiz",
                null,
                null,
                null,
                null,
                null,
                null,
                null
        , null, null, null, null);

        BewerbungseintragEntity existing = new BewerbungseintragEntity();
        existing.setBewerbungseintragId(60L);
        existing.setBewerbungscontainerId(1L);
        existing.setFirmaId(2L);
        existing.setStellenausschreibungId(3L);
        existing.setStatusId(4L);
        existing.setNotiz("Alt");
        when(bewerbungseintragRepository.findById(60L)).thenReturn(Optional.of(existing));
        when(bewerbungscontainerRepository.existsById(1L)).thenReturn(true);
        when(firmaRepository.existsById(2L)).thenReturn(true);
        when(stellenausschreibungRepository.existsById(3L)).thenReturn(true);
        when(statusRepository.existsById(4L)).thenReturn(true);

        FirmaEntity firma = new FirmaEntity();
        firma.setFirmaId(2L);
        firma.setKontaktPerson("Kontakt Alt");
        firma.setTelefon("+49000123");
        firma.setEmail("kontakt@alt.de");
        when(firmaRepository.findById(2L)).thenReturn(Optional.of(firma));

        StellenausschreibungEntity stelle = new StellenausschreibungEntity();
        stelle.setStellenausschreibungId(3L);
        stelle.setOrt("Berlin");
        when(stellenausschreibungRepository.findById(3L)).thenReturn(Optional.of(stelle));

        when(bewerbungseintragRepository.save(existing)).thenReturn(existing);

        BewerbungseintragResponse response = bewerbungseintragService.patchBewerbungseintrag(60L, request);

        assertEquals("Neue Notiz", response.notiz());
        assertEquals("kontakt@alt.de", response.email());
        assertEquals("Kontakt Alt", response.contactPerson());
        assertEquals("+49000123", response.phone());
        assertEquals("Berlin", response.location());
        verify(firmaRepository, never()).save(any(FirmaEntity.class));
        verify(stellenausschreibungRepository, never()).save(any(StellenausschreibungEntity.class));
    }

    @Test
    void patchShouldMapStandortAliasToLocation() {
        PatchBewerbungseintragRequest request = new PatchBewerbungseintragRequest(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "Dresden",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        , null, null, null, null);

        BewerbungseintragEntity existing = new BewerbungseintragEntity();
        existing.setBewerbungseintragId(70L);
        existing.setBewerbungscontainerId(1L);
        existing.setFirmaId(2L);
        existing.setStellenausschreibungId(3L);
        existing.setStatusId(4L);
        when(bewerbungseintragRepository.findById(70L)).thenReturn(Optional.of(existing));
        when(bewerbungscontainerRepository.existsById(1L)).thenReturn(true);
        when(firmaRepository.existsById(2L)).thenReturn(true);
        when(stellenausschreibungRepository.existsById(3L)).thenReturn(true);
        when(statusRepository.existsById(4L)).thenReturn(true);

        FirmaEntity firma = new FirmaEntity();
        firma.setFirmaId(2L);
        when(firmaRepository.findById(2L)).thenReturn(Optional.of(firma));

        StellenausschreibungEntity stelle = new StellenausschreibungEntity();
        stelle.setStellenausschreibungId(3L);
        stelle.setOrt("Berlin");
        when(stellenausschreibungRepository.findById(3L)).thenReturn(Optional.of(stelle));

        when(bewerbungseintragRepository.save(existing)).thenReturn(existing);

        BewerbungseintragResponse response = bewerbungseintragService.patchBewerbungseintrag(70L, request);

        assertEquals("Dresden", response.location());
        assertEquals("Dresden", response.standort());
        verify(stellenausschreibungRepository).save(stelle);
    }

    @Test
    void deleteBewerbungseintragShouldDeleteWhenExists() {
        when(bewerbungseintragRepository.existsById(5L)).thenReturn(true);

        bewerbungseintragService.deleteBewerbungseintrag(5L);

        verify(bewerbungseintragRepository).deleteById(5L);
    }

    // =====================================================================
    //  Reihenfolge und Statusverlauf
    // =====================================================================

    @Test
    void getAllBewerbungseintraegeGibtNeuesteZuerst() {
        BewerbungseintragEntity alt = eintrag(1L, LocalDateTime.of(2026, 1, 1, 9, 0));
        BewerbungseintragEntity mitte = eintrag(2L, LocalDateTime.of(2026, 5, 1, 9, 0));
        BewerbungseintragEntity neu = eintrag(3L, LocalDateTime.of(2026, 9, 1, 9, 0));
        when(bewerbungseintragRepository.findAll()).thenReturn(List.of(alt, neu, mitte));

        List<BewerbungseintragResponse> liste = bewerbungseintragService.getAllBewerbungseintraege();

        assertEquals(List.of(3L, 2L, 1L),
                liste.stream().map(BewerbungseintragResponse::bewerbungseintragId).toList());
    }

    /** Ein Import legt viele Eintraege im selben Moment an - dann entscheidet die Id. */
    @Test
    void beiGleichemZeitstempelEntscheidetDieId() {
        LocalDateTime gleich = LocalDateTime.of(2026, 9, 1, 9, 0);
        when(bewerbungseintragRepository.findAll())
                .thenReturn(List.of(eintrag(1L, gleich), eintrag(3L, gleich), eintrag(2L, gleich)));

        List<BewerbungseintragResponse> liste = bewerbungseintragService.getAllBewerbungseintraege();

        assertEquals(List.of(3L, 2L, 1L),
                liste.stream().map(BewerbungseintragResponse::bewerbungseintragId).toList());
    }

    @Test
    void setzeStatusDatumAendertDieVorhandeneVerlaufszeile() {
        BewerbungseintragEntity eintrag = eintrag(60L, LocalDateTime.of(2026, 9, 1, 9, 0));
        when(bewerbungseintragRepository.findById(60L)).thenReturn(Optional.of(eintrag));
        when(statusRepository.findByTitel("ABGESCHICKT"))
                .thenReturn(Optional.of(status(4L, "ABGESCHICKT")));

        StatusVerlaufEntity vorhanden = verlauf(11L, 60L, "ABGESCHICKT",
                LocalDateTime.of(2026, 9, 20, 14, 30));
        when(statusVerlaufRepository.findByBewerbungseintragIdOrderByStatusVerlaufIdDesc(60L))
                .thenReturn(List.of(vorhanden));

        bewerbungseintragService.setzeStatusDatum(60L, "ABGESCHICKT", LocalDate.of(2026, 8, 4));

        // Der Tag wird korrigiert, die Uhrzeit des urspruenglichen Wechsels bleibt stehen.
        assertEquals(LocalDateTime.of(2026, 8, 4, 14, 30), vorhanden.getGeaendertAm());
        verify(statusVerlaufRepository).save(vorhanden);
    }

    /**
     * Der Fall aus dem Excel-Import: Eine Bewerbung kommt als Absage herein und hat nie einen
     * Wechsel auf "abgeschickt" durchlaufen - ein Absendedatum hat sie trotzdem.
     */
    @Test
    void setzeStatusDatumLegtEineFehlendeVerlaufszeileAn() {
        BewerbungseintragEntity eintrag = eintrag(61L, LocalDateTime.of(2026, 9, 1, 9, 0));
        when(bewerbungseintragRepository.findById(61L)).thenReturn(Optional.of(eintrag));
        when(statusRepository.findByTitel("ABGESCHICKT"))
                .thenReturn(Optional.of(status(4L, "ABGESCHICKT")));
        when(statusVerlaufRepository.findByBewerbungseintragIdOrderByStatusVerlaufIdDesc(61L))
                .thenReturn(List.of(verlauf(12L, 61L, "ABSAGE", LocalDateTime.now())));

        bewerbungseintragService.setzeStatusDatum(61L, "ABGESCHICKT", LocalDate.of(2026, 8, 4));

        ArgumentCaptor<StatusVerlaufEntity> gespeichert =
                ArgumentCaptor.forClass(StatusVerlaufEntity.class);
        verify(statusVerlaufRepository).save(gespeichert.capture());
        assertEquals("ABGESCHICKT", gespeichert.getValue().getStatusTitel());
        assertEquals(LocalDateTime.of(2026, 8, 4, 0, 0), gespeichert.getValue().getGeaendertAm());
        assertEquals(61L, gespeichert.getValue().getBewerbungseintragId());
    }

    @Test
    void setzeStatusDatumWeistUnbekanntenStatusAb() {
        when(bewerbungseintragRepository.findById(62L))
                .thenReturn(Optional.of(eintrag(62L, LocalDateTime.now())));
        when(statusRepository.findByTitel("GIBTESNICHT")).thenReturn(Optional.empty());

        AppException e = assertThrows(AppException.class, () ->
                bewerbungseintragService.setzeStatusDatum(62L, "GIBTESNICHT", LocalDate.now()));

        assertEquals(ErrorCode.BAD_REQUEST, e.getErrorCode());
        verify(statusVerlaufRepository, never()).save(any());
    }

    /**
     * Der Grund, warum der Verlauf nach Einfuegereihenfolge gelesen wird: Bei einem Zyklus
     * abgeschickt - Entwurf - abgeschickt gibt es zwei Zeilen. Wird die neuere zurueckdatiert,
     * darf trotzdem nicht die aeltere gelten.
     */
    @Test
    void rueckdatierenSpueltKeineAeltereVerlaufszeileNachVorn() {
        BewerbungseintragEntity eintrag = eintrag(63L, LocalDateTime.of(2026, 9, 1, 9, 0));
        when(bewerbungseintragRepository.findById(63L)).thenReturn(Optional.of(eintrag));

        StatusVerlaufEntity zuerst = verlauf(20L, 63L, "ABGESCHICKT",
                LocalDateTime.of(2026, 9, 10, 8, 0));
        StatusVerlaufEntity spaeter = verlauf(22L, 63L, "ABGESCHICKT",
                LocalDateTime.of(2026, 6, 1, 8, 0));   // schon zurueckdatiert
        // Einfuegereihenfolge: die hoehere Id steht vorn
        when(statusVerlaufRepository.findByBewerbungseintragIdOrderByStatusVerlaufIdDesc(63L))
                .thenReturn(List.of(spaeter, zuerst));

        BewerbungseintragResponse response = bewerbungseintragService.getBewerbungseintragById(63L);

        assertEquals(LocalDateTime.of(2026, 6, 1, 8, 0), response.abgeschicktAm());
    }

    @Test
    void setzeErstelltAmSchreibtDasFeldAmEintrag() {
        BewerbungseintragEntity eintrag = eintrag(64L, LocalDateTime.of(2026, 9, 1, 9, 0));
        when(bewerbungseintragRepository.findById(64L)).thenReturn(Optional.of(eintrag));
        when(bewerbungseintragRepository.save(eintrag)).thenReturn(eintrag);

        bewerbungseintragService.setzeErstelltAm(64L, LocalDate.of(2026, 7, 15));

        assertEquals(LocalDate.of(2026, 7, 15), eintrag.getErstelltAm());
    }

    // =====================================================================

    private BewerbungseintragEntity eintrag(Long id, LocalDateTime angelegt) {
        BewerbungseintragEntity e = new BewerbungseintragEntity();
        e.setBewerbungseintragId(id);
        e.setCreatedAt(angelegt);
        return e;
    }

    private StatusEntity status(long id, String titel) {
        StatusEntity s = new StatusEntity();
        s.setStatusId(id);
        s.setTitel(titel);
        return s;
    }

    private StatusVerlaufEntity verlauf(Long id, Long eintragId, String titel, LocalDateTime am) {
        StatusVerlaufEntity v = new StatusVerlaufEntity();
        v.setStatusVerlaufId(id);
        v.setBewerbungseintragId(eintragId);
        v.setStatusTitel(titel);
        v.setGeaendertAm(am);
        return v;
    }
}
