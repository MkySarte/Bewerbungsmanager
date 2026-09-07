package de.mkysarte.bewerbungsmanager.bewerbungseintrag.service;

import de.mkysarte.bewerbungsmanager.common.exception.AppException;
import de.mkysarte.bewerbungsmanager.bewerbungscontainer.entity.BewerbungscontainerEntity;
import de.mkysarte.bewerbungsmanager.bewerbungscontainer.repository.BewerbungscontainerRepository;
import de.mkysarte.bewerbungsmanager.bewerbungseintrag.dto.BewerbungseintragResponse;
import de.mkysarte.bewerbungsmanager.bewerbungseintrag.dto.CreateBewerbungseintragRequest;
import de.mkysarte.bewerbungsmanager.bewerbungseintrag.dto.PatchBewerbungseintragRequest;
import de.mkysarte.bewerbungsmanager.bewerbungseintrag.dto.UpdateBewerbungseintragRequest;
import de.mkysarte.bewerbungsmanager.bewerbungseintrag.entity.BewerbungseintragEntity;
import de.mkysarte.bewerbungsmanager.bewerbungseintrag.entity.StatusVerlaufEntity;
import de.mkysarte.bewerbungsmanager.bewerbungseintrag.repository.StatusVerlaufRepository;
import de.mkysarte.bewerbungsmanager.bewerbungseintrag.repository.BewerbungseintragRepository;
import de.mkysarte.bewerbungsmanager.document.entity.DocumentEntity;
import de.mkysarte.bewerbungsmanager.document.entity.DocumentScope;
import de.mkysarte.bewerbungsmanager.document.entity.DocumentType;
import de.mkysarte.bewerbungsmanager.document.repository.DocumentRepository;
import de.mkysarte.bewerbungsmanager.firma.entity.FirmaEntity;
import de.mkysarte.bewerbungsmanager.firma.repository.FirmaRepository;
import de.mkysarte.bewerbungsmanager.status.entity.StatusEntity;
import de.mkysarte.bewerbungsmanager.status.repository.StatusRepository;
import de.mkysarte.bewerbungsmanager.stellenausschreibung.entity.StellenausschreibungEntity;
import de.mkysarte.bewerbungsmanager.stellenausschreibung.repository.StellenausschreibungRepository;
import de.mkysarte.bewerbungsmanager.session.CurrentUserHolder;
import de.mkysarte.bewerbungsmanager.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class BewerbungseintragService {

    private static final String DEFAULT_CONTAINER_NAME = "Standardcontainer";

    private final BewerbungseintragRepository bewerbungseintragRepository;
    private final BewerbungscontainerRepository bewerbungscontainerRepository;
    private final FirmaRepository firmaRepository;
    private final StellenausschreibungRepository stellenausschreibungRepository;
    private final StatusRepository statusRepository;
    private final StatusVerlaufRepository statusVerlaufRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final CurrentUserHolder currentUserHolder;

    public BewerbungseintragService(
            BewerbungseintragRepository bewerbungseintragRepository,
            BewerbungscontainerRepository bewerbungscontainerRepository,
            FirmaRepository firmaRepository,
            StellenausschreibungRepository stellenausschreibungRepository,
            StatusRepository statusRepository,
            StatusVerlaufRepository statusVerlaufRepository,
            DocumentRepository documentRepository,
            UserRepository userRepository,
            CurrentUserHolder currentUserHolder
    ) {
        this.bewerbungseintragRepository = bewerbungseintragRepository;
        this.bewerbungscontainerRepository = bewerbungscontainerRepository;
        this.firmaRepository = firmaRepository;
        this.stellenausschreibungRepository = stellenausschreibungRepository;
        this.statusRepository = statusRepository;
        this.statusVerlaufRepository = statusVerlaufRepository;
        this.documentRepository = documentRepository;
        this.userRepository = userRepository;
        this.currentUserHolder = currentUserHolder;
    }

    @Transactional
    public BewerbungseintragResponse createBewerbungseintrag(CreateBewerbungseintragRequest request) {
        ResolvedCreateReferences references = resolveCreateReferences(request);

        BewerbungseintragEntity entity = new BewerbungseintragEntity();
        entity.setBewerbungscontainerId(references.container().getBewerbungscontainerId());
        entity.setFirmaId(references.firma().getFirmaId());
        entity.setStellenausschreibungId(references.stelle().getStellenausschreibungId());
        entity.setStatusId(references.statusId());
        entity.setNotiz(request.notiz());
        entity.setUrl(request.url());
        entity.setLebenslaufId(request.lebenslaufId());
        entity.setZertifikateId(request.zertifikateId());
        entity.setZeugnisseId(request.zeugnisseId());
        entity.setAnschreibenId(request.anschreibenId());
        entity.setErstelltAm(request.erstelltAm());
        entity.setAktualisiertAm(request.aktualisiertAm());
        entity.setPdfBundleGewuenscht(Boolean.TRUE.equals(request.pdfBundleGewuenscht()));
        entity.setNachfassFristTage(request.nachfassFristTage());
        entity.setEntwurfErinnerungIntervall(request.entwurfErinnerungIntervall());
        entity.setLetzteErinnerungAm(request.letzteErinnerungAm());

        applyContactFields(references.firma(), request.contactPerson(), request.ansprechpartner(), request.phone(), request.telefon(), request.email());
        applyLocationFields(references.stelle(), request.location(), request.standort());

        BewerbungseintragEntity saved = bewerbungseintragRepository.save(entity);
        validateDocumentReferences(saved);
        // Der Verlauf beginnt beim Anlegen - sonst hätte eine Bewerbung, deren Status nie
        // wieder geändert wird, überhaupt kein Datum.
        protokolliereStatus(saved.getBewerbungseintragId(), saved.getStatusId());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public BewerbungseintragResponse getBewerbungseintragById(Long id) {
        BewerbungseintragEntity entity = bewerbungseintragRepository.findById(id)
                .orElseThrow(() -> AppException.notFound("Bewerbungseintrag nicht gefunden"));
        return toResponse(entity);
    }

    /**
     * Alle Bewerbungen, <b>neueste zuerst</b>.
     *
     * <p>Die Reihenfolge gehoert hierher und nicht in die Oberflaeche: Ohne sie kaeme
     * {@code findAll()} in Schluesselreihenfolge zurueck, also aelteste oben - eine gerade
     * angelegte Bewerbung stuende dann ganz unten und muesste gesucht werden. Bei gleichem
     * Zeitstempel entscheidet die Id, denn ein Import legt viele Eintraege fast gleichzeitig an.
     */
    @Transactional(readOnly = true)
    public List<BewerbungseintragResponse> getAllBewerbungseintraege() {
        Comparator<BewerbungseintragEntity> neuesteZuerst = Comparator
                .comparing(BewerbungseintragEntity::getCreatedAt,
                        Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(BewerbungseintragEntity::getBewerbungseintragId,
                        Comparator.nullsFirst(Comparator.naturalOrder()))
                .reversed();

        return bewerbungseintragRepository.findAll().stream()
                .sorted(neuesteZuerst)
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public BewerbungseintragResponse updateBewerbungseintrag(Long id, UpdateBewerbungseintragRequest request) {
        BewerbungseintragEntity entity = bewerbungseintragRepository.findById(id)
                .orElseThrow(() -> AppException.notFound("Bewerbungseintrag nicht gefunden"));

        validateReferences(
                request.bewerbungscontainerId(),
                request.firmaId(),
                request.stellenausschreibungId(),
                request.statusId()
        );

        boolean statusGeaendert = !java.util.Objects.equals(entity.getStatusId(), request.statusId());

        entity.setBewerbungscontainerId(request.bewerbungscontainerId());
        entity.setFirmaId(request.firmaId());
        entity.setStellenausschreibungId(request.stellenausschreibungId());
        entity.setStatusId(request.statusId());
        FirmaEntity firma = firmaRepository.findById(request.firmaId())
                .orElseThrow(() -> AppException.badRequest("Firma existiert nicht"));
        StellenausschreibungEntity stelle = stellenausschreibungRepository.findById(request.stellenausschreibungId())
                .orElseThrow(() -> AppException.badRequest("Stellenausschreibung existiert nicht"));
        applyContactFields(firma, request.contactPerson(), request.ansprechpartner(), request.phone(), request.telefon(), request.email());
        applyLocationFields(stelle, request.location(), request.standort());
        entity.setNotiz(request.notiz());
        entity.setUrl(request.url());
        entity.setLebenslaufId(request.lebenslaufId());
        entity.setZertifikateId(request.zertifikateId());
        entity.setZeugnisseId(request.zeugnisseId());
        entity.setAnschreibenId(request.anschreibenId());
        entity.setErstelltAm(request.erstelltAm());
        entity.setAktualisiertAm(request.aktualisiertAm());
        entity.setPdfBundleGewuenscht(Boolean.TRUE.equals(request.pdfBundleGewuenscht()));
        entity.setNachfassFristTage(request.nachfassFristTage());
        entity.setEntwurfErinnerungIntervall(request.entwurfErinnerungIntervall());
        entity.setLetzteErinnerungAm(request.letzteErinnerungAm());

        validateDocumentReferences(entity);
        BewerbungseintragEntity updated = bewerbungseintragRepository.save(entity);
        if (statusGeaendert) {
            protokolliereStatus(updated.getBewerbungseintragId(), updated.getStatusId());
        }
        return toResponse(updated);
    }

    @Transactional
    public BewerbungseintragResponse patchBewerbungseintrag(Long id, PatchBewerbungseintragRequest request) {
        BewerbungseintragEntity entity = bewerbungseintragRepository.findById(id)
                .orElseThrow(() -> AppException.notFound("Bewerbungseintrag nicht gefunden"));

        boolean hasAnyChange = request.bewerbungscontainerId() != null
                || request.firmaId() != null
                || request.stellenausschreibungId() != null
                || request.statusId() != null
                || request.contactPerson() != null
                || request.ansprechpartner() != null
                || request.phone() != null
                || request.telefon() != null
                || request.email() != null
                || request.location() != null
                || request.standort() != null
                || request.notiz() != null
                || request.url() != null
                || request.lebenslaufId() != null
                || request.zertifikateId() != null
                || request.zeugnisseId() != null
                || request.anschreibenId() != null
                || request.erstelltAm() != null
                || request.aktualisiertAm() != null
                || request.pdfBundleGewuenscht() != null
                || request.nachfassFristTage() != null
                || request.entwurfErinnerungIntervall() != null
                || request.letzteErinnerungAm() != null;

        if (!hasAnyChange) {
            throw AppException.badRequest("Mindestens ein Feld für PATCH ist erforderlich");
        }

        Long bewerbungscontainerId = request.bewerbungscontainerId() != null
                ? request.bewerbungscontainerId()
                : entity.getBewerbungscontainerId();
        Long firmaId = request.firmaId() != null ? request.firmaId() : entity.getFirmaId();
        Long stellenausschreibungId = request.stellenausschreibungId() != null
                ? request.stellenausschreibungId()
                : entity.getStellenausschreibungId();
        Long statusId = request.statusId() != null ? request.statusId() : entity.getStatusId();

        validateReferences(bewerbungscontainerId, firmaId, stellenausschreibungId, statusId);

        if (request.bewerbungscontainerId() != null) {
            entity.setBewerbungscontainerId(request.bewerbungscontainerId());
        }
        if (request.firmaId() != null) {
            entity.setFirmaId(request.firmaId());
        }
        if (request.stellenausschreibungId() != null) {
            entity.setStellenausschreibungId(request.stellenausschreibungId());
        }
        boolean statusGeaendert = request.statusId() != null
                && !java.util.Objects.equals(entity.getStatusId(), request.statusId());
        if (request.statusId() != null) {
            entity.setStatusId(request.statusId());
        }
        Long effectiveFirmaId = request.firmaId() != null ? request.firmaId() : entity.getFirmaId();
        Long effectiveStelleId = request.stellenausschreibungId() != null
                ? request.stellenausschreibungId()
                : entity.getStellenausschreibungId();
        if (request.contactPerson() != null
                || request.ansprechpartner() != null
                || request.phone() != null
                || request.telefon() != null
                || request.email() != null) {
            FirmaEntity firma = firmaRepository.findById(effectiveFirmaId)
                    .orElseThrow(() -> AppException.badRequest("Firma existiert nicht"));
            applyContactFields(firma, request.contactPerson(), request.ansprechpartner(), request.phone(), request.telefon(), request.email());
        }
        if (request.location() != null || request.standort() != null) {
            StellenausschreibungEntity stelle = stellenausschreibungRepository.findById(effectiveStelleId)
                    .orElseThrow(() -> AppException.badRequest("Stellenausschreibung existiert nicht"));
            applyLocationFields(stelle, request.location(), request.standort());
        }
        if (request.notiz() != null) {
            entity.setNotiz(request.notiz());
        }
        if (request.url() != null) {
            entity.setUrl(request.url());
        }
        if (request.lebenslaufId() != null) {
            entity.setLebenslaufId(request.lebenslaufId());
        }
        if (request.zertifikateId() != null) {
            entity.setZertifikateId(request.zertifikateId());
        }
        if (request.zeugnisseId() != null) {
            entity.setZeugnisseId(request.zeugnisseId());
        }
        if (request.anschreibenId() != null) {
            entity.setAnschreibenId(request.anschreibenId());
        }
        if (request.erstelltAm() != null) {
            entity.setErstelltAm(request.erstelltAm());
        }
        if (request.aktualisiertAm() != null) {
            entity.setAktualisiertAm(request.aktualisiertAm());
        }
        if (request.pdfBundleGewuenscht() != null) {
            entity.setPdfBundleGewuenscht(request.pdfBundleGewuenscht());
        }
        if (request.nachfassFristTage() != null) {
            entity.setNachfassFristTage(request.nachfassFristTage());
        }
        if (request.entwurfErinnerungIntervall() != null) {
            entity.setEntwurfErinnerungIntervall(request.entwurfErinnerungIntervall());
        }
        if (request.letzteErinnerungAm() != null) {
            entity.setLetzteErinnerungAm(request.letzteErinnerungAm());
        }

        validateDocumentReferences(entity);
        BewerbungseintragEntity updated = bewerbungseintragRepository.save(entity);
        if (statusGeaendert) {
            protokolliereStatus(updated.getBewerbungseintragId(), updated.getStatusId());
        }
        return toResponse(updated);
    }

    // =====================================================================
    //  Gezielte Einzelaenderungen
    //
    //  PATCH nimmt inzwischen 23 Felder entgegen - ein Aufruf mit 22 nulls wäre an der
    //  Aufrufstelle nicht mehr lesbar und beim nächsten neuen Feld eine Fehlerquelle.
    // =====================================================================

    /** Setzt die Nachfassfrist dieser Bewerbung; {@code null} stellt auf den globalen Standard zurück. */
    @Transactional
    public BewerbungseintragResponse setzeNachfassFrist(Long id, Integer tage) {
        BewerbungseintragEntity entity = bewerbungseintragRepository.findById(id)
                .orElseThrow(() -> AppException.notFound("Bewerbungseintrag nicht gefunden"));
        entity.setNachfassFristTage(tage);
        return toResponse(bewerbungseintragRepository.save(entity));
    }

    /**
     * Setzt das Datum, seit dem diese Bewerbung als Entwurf liegt.
     *
     * <p>Anders als die Abschlussdaten ist das eine Spalte am Eintrag selbst, kein
     * Verlaufseintrag - deshalb eine eigene Methode statt {@link #setzeStatusDatum}.
     */
    @Transactional
    public BewerbungseintragResponse setzeErstelltAm(Long id, LocalDate datum) {
        BewerbungseintragEntity entity = bewerbungseintragRepository.findById(id)
                .orElseThrow(() -> AppException.notFound("Bewerbungseintrag nicht gefunden"));
        if (datum == null) {
            throw AppException.badRequest("Datum ist erforderlich");
        }
        entity.setErstelltAm(datum);
        return toResponse(bewerbungseintragRepository.save(entity));
    }

    /** Setzt den Erinnerungsrhythmus für den Entwurf; {@code null} = globaler Standard. */
    @Transactional
    public BewerbungseintragResponse setzeEntwurfIntervall(Long id, String intervall) {
        BewerbungseintragEntity entity = bewerbungseintragRepository.findById(id)
                .orElseThrow(() -> AppException.notFound("Bewerbungseintrag nicht gefunden"));
        entity.setEntwurfErinnerungIntervall(intervall);
        return toResponse(bewerbungseintragRepository.save(entity));
    }

    /**
     * Setzt oder korrigiert das Datum, an dem diese Bewerbung einen Status erreicht hat.
     *
     * <p>Notwendig, weil {@code abgeschicktAm} und die Abschlussdaten keine Spalten am Eintrag
     * sind, sondern aus dem Statusverlauf abgelesen werden. Wer eine bestehende Liste einspielt,
     * hat aber vor Wochen abgeschickt - ohne diese Methode stuende dort der Importzeitpunkt,
     * und Nachfassfrist wie Faelligkeit rechneten auf einem falschen Tag.
     *
     * <p>Gibt es zu dem Titel noch keine Verlaufszeile, wird eine angelegt. Genau das braucht
     * der Import: Eine Bewerbung, die als Absage hereinkommt, hat nie einen Wechsel auf
     * "abgeschickt" durchlaufen - ein Absendedatum hat sie trotzdem.
     *
     * <p>Der aktuelle Status des Eintrags bleibt unberuehrt; hier wird nur der Verlauf geführt.
     */
    @Transactional
    public BewerbungseintragResponse setzeStatusDatum(Long id, String statusTitel, LocalDate datum) {
        BewerbungseintragEntity entity = bewerbungseintragRepository.findById(id)
                .orElseThrow(() -> AppException.notFound("Bewerbungseintrag nicht gefunden"));
        if (datum == null) {
            throw AppException.badRequest("Datum ist erforderlich");
        }
        StatusEntity status = statusRepository.findByTitel(statusTitel)
                .orElseThrow(() -> AppException.badRequest("Status existiert nicht: " + statusTitel));

        StatusVerlaufEntity verlauf = massgeblicheVerlaufszeile(id, status.getTitel())
                .orElseGet(() -> {
                    StatusVerlaufEntity neue = new StatusVerlaufEntity();
                    neue.setBewerbungseintragId(id);
                    neue.setStatusId(status.getStatusId());
                    neue.setStatusTitel(status.getTitel());
                    return neue;
                });
        // Die Uhrzeit des urspruenglichen Wechsels beibehalten, damit sich beim reinen
        // Korrigieren des Tages nichts still verschiebt.
        LocalTime uhrzeit = verlauf.getGeaendertAm() != null
                ? verlauf.getGeaendertAm().toLocalTime()
                : LocalTime.MIDNIGHT;
        verlauf.setGeaendertAm(datum.atTime(uhrzeit));
        statusVerlaufRepository.save(verlauf);

        return toResponse(entity);
    }

    /**
     * Haelt fest, dass zu dieser Bewerbung eine Benachrichtigung herausging.
     * Ohne diesen Vermerk kaeme dieselbe Meldung beim nächsten Durchlauf erneut.
     */
    @Transactional
    public void markiereErinnerungGesendet(Long id, java.time.LocalDate datum) {
        bewerbungseintragRepository.findById(id).ifPresent(entity -> {
            entity.setLetzteErinnerungAm(datum);
            bewerbungseintragRepository.save(entity);
        });
    }

    @Transactional
    public void deleteBewerbungseintrag(Long id) {
        if (!bewerbungseintragRepository.existsById(id)) {
            throw AppException.notFound("Bewerbungseintrag nicht gefunden");
        }
        statusVerlaufRepository.deleteByBewerbungseintragId(id);
        bewerbungseintragRepository.deleteById(id);
    }

    /**
     * Schreibt einen Verlaufseintrag. Der Statustitel wandert mit, damit der Verlauf auch
     * dann noch lesbar ist, wenn der Status später umbenannt oder gelöscht wird.
     */
    private void protokolliereStatus(Long bewerbungseintragId, Long statusId) {
        StatusVerlaufEntity verlauf = new StatusVerlaufEntity();
        verlauf.setBewerbungseintragId(bewerbungseintragId);
        verlauf.setStatusId(statusId);
        verlauf.setStatusTitel(optionalOf(statusRepository.findById(statusId))
                .map(StatusEntity::getTitel)
                .orElse(null));
        verlauf.setGeaendertAm(LocalDateTime.now());
        statusVerlaufRepository.save(verlauf);
    }

    /** Zeitpunkt, zu dem dieser Statustitel zuletzt gesetzt wurde - oder null. */
    private LocalDateTime letzterStatuswechsel(Long bewerbungseintragId, String statusTitel) {
        return massgeblicheVerlaufszeile(bewerbungseintragId, statusTitel)
                .map(StatusVerlaufEntity::getGeaendertAm)
                .orElse(null);
    }

    /**
     * Die Verlaufszeile, die fuer diesen Statustitel gilt: die zuletzt <em>geschriebene</em>.
     *
     * <p>Nicht die mit dem spaetesten Datum - sonst koennte ein rueckdatierter Eintrag eine
     * aeltere Zeile desselben Titels nach vorn spuelen.
     */
    private Optional<StatusVerlaufEntity> massgeblicheVerlaufszeile(Long bewerbungseintragId,
                                                                    String statusTitel) {
        if (bewerbungseintragId == null || statusTitel == null) {
            return Optional.empty();
        }
        return statusVerlaufRepository
                .findByBewerbungseintragIdOrderByStatusVerlaufIdDesc(bewerbungseintragId)
                .stream()
                .filter(v -> statusTitel.equalsIgnoreCase(v.getStatusTitel()))
                .findFirst();
    }

    private void validateReferences(Long bewerbungscontainerId, Long firmaId, Long stellenausschreibungId, Long statusId) {
        if (!bewerbungscontainerRepository.existsById(bewerbungscontainerId)) {
            throw AppException.badRequest("Bewerbungscontainer existiert nicht");
        }
        if (!firmaRepository.existsById(firmaId)) {
            throw AppException.badRequest("Firma existiert nicht");
        }
        if (!stellenausschreibungRepository.existsById(stellenausschreibungId)) {
            throw AppException.badRequest("Stellenausschreibung existiert nicht");
        }
        if (!statusRepository.existsById(statusId)) {
            throw AppException.badRequest("Status existiert nicht");
        }
    }

    private BewerbungseintragResponse toResponse(BewerbungseintragEntity entity) {
        String containerName = optionalOf(bewerbungscontainerRepository.findById(entity.getBewerbungscontainerId()))
                .map(BewerbungscontainerEntity::getName)
                .orElse(null);
        Optional<FirmaEntity> firma = optionalOf(firmaRepository.findById(entity.getFirmaId()));
        String firmaName = firma.map(FirmaEntity::getName).orElse(null);
        String contactPerson = firma.map(FirmaEntity::getKontaktPerson).orElse(null);
        String phone = firma.map(FirmaEntity::getTelefon).orElse(null);
        String email = firma.map(FirmaEntity::getEmail).orElse(null);
        Optional<StellenausschreibungEntity> stelle = optionalOf(stellenausschreibungRepository.findById(entity.getStellenausschreibungId()));
        String stellenbezeichnung = stelle.map(StellenausschreibungEntity::getTitel).orElse(null);
        String location = stelle.map(StellenausschreibungEntity::getOrt).orElse(null);
        String statusTitel = optionalOf(statusRepository.findById(entity.getStatusId()))
                .map(s -> s.getTitel())
                .orElse(null);

        return new BewerbungseintragResponse(
                entity.getBewerbungseintragId(),
                entity.getBewerbungscontainerId(),
                containerName,
                entity.getFirmaId(),
                firmaName,
                entity.getStellenausschreibungId(),
                stellenbezeichnung,
                contactPerson,
                contactPerson,
                phone,
                phone,
                email,
                location,
                location,
                entity.getStatusId(),
                statusTitel,
                entity.getNotiz(),
                entity.getUrl(),
                entity.getLebenslaufId(),
                entity.getZertifikateId(),
                entity.getZeugnisseId(),
                entity.getAnschreibenId(),
                entity.getErstelltAm(),
                entity.getAktualisiertAm(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.isPdfBundleGewuenscht(),
                entity.getNachfassFristTage(),
                entity.getEntwurfErinnerungIntervall(),
                entity.getLetzteErinnerungAm(),
                letzterStatuswechsel(entity.getBewerbungseintragId(), "ABGESCHICKT"),
                letzterStatuswechsel(entity.getBewerbungseintragId(), "ABSAGE"),
                letzterStatuswechsel(entity.getBewerbungseintragId(), "ERFOLG")
        );
    }

    private ResolvedCreateReferences resolveCreateReferences(CreateBewerbungseintragRequest request) {
        BewerbungscontainerEntity container = resolveContainer(request.bewerbungscontainerId(), request.containerName());
        FirmaEntity firma = resolveFirma(request.firmaId(), request.firmaName());
        StellenausschreibungEntity stelle = resolveStelle(
                request.stellenausschreibungId(),
                request.stellenbezeichnung(),
                firma.getFirmaId()
        );
        Long statusId = resolveStatusId(request.statusId());

        return new ResolvedCreateReferences(
                container,
                firma,
                stelle,
                statusId
        );
    }

    private void applyContactFields(
            FirmaEntity firma,
            String contactPersonRaw,
            String ansprechpartnerRaw,
            String phoneRaw,
            String telefonRaw,
            String emailRaw
    ) {
        boolean changed = false;
        if (contactPersonRaw != null || ansprechpartnerRaw != null) {
            String contactPerson = normalizeText(firstNonNull(contactPersonRaw, ansprechpartnerRaw));
            if (!Objects.equals(firma.getKontaktPerson(), contactPerson)) {
                firma.setKontaktPerson(contactPerson);
                changed = true;
            }
        }
        if (phoneRaw != null || telefonRaw != null) {
            String phone = normalizeText(firstNonNull(phoneRaw, telefonRaw));
            if (!Objects.equals(firma.getTelefon(), phone)) {
                firma.setTelefon(phone);
                changed = true;
            }
        }
        if (emailRaw != null) {
            String email = normalizeText(emailRaw);
            if (!Objects.equals(firma.getEmail(), email)) {
                firma.setEmail(email);
                changed = true;
            }
        }
        if (changed) {
            firmaRepository.save(firma);
        }
    }

    private void applyLocationFields(StellenausschreibungEntity stelle, String locationRaw, String standortRaw) {
        if (locationRaw == null && standortRaw == null) {
            return;
        }
        String location = normalizeText(firstNonNull(locationRaw, standortRaw));
        if (!Objects.equals(stelle.getOrt(), location)) {
            stelle.setOrt(location);
            stellenausschreibungRepository.save(stelle);
        }
    }

    private BewerbungscontainerEntity resolveContainer(Long containerId, String containerNameRaw) {
        String containerName = normalizeText(containerNameRaw);
        if (containerId != null) {
            BewerbungscontainerEntity existing = bewerbungscontainerRepository.findById(containerId)
                    .orElseThrow(() -> AppException.badRequest("Bewerbungscontainer existiert nicht"));
            if (containerName != null && !containerName.equalsIgnoreCase(existing.getName())) {
                existing.setName(containerName);
                return bewerbungscontainerRepository.save(existing);
            }
            return existing;
        }

        Long userId = resolveAuthenticatedUserId();
        BewerbungscontainerEntity container = bewerbungscontainerRepository.findByUserId(userId)
                .orElseGet(() -> {
                    BewerbungscontainerEntity created = new BewerbungscontainerEntity();
                    created.setUserId(userId);
                    created.setName(containerName != null ? containerName : DEFAULT_CONTAINER_NAME);
                    return bewerbungscontainerRepository.save(created);
                });

        if (containerName != null && !containerName.equalsIgnoreCase(container.getName())) {
            container.setName(containerName);
            return bewerbungscontainerRepository.save(container);
        }
        return container;
    }

    private FirmaEntity resolveFirma(Long firmaId, String firmaNameRaw) {
        String firmaName = normalizeText(firmaNameRaw);
        if (firmaId != null) {
            FirmaEntity existing = firmaRepository.findById(firmaId)
                    .orElseThrow(() -> AppException.badRequest("Firma existiert nicht"));
            if (firmaName != null && !firmaName.equalsIgnoreCase(existing.getName())) {
                throw AppException.conflict("firmaId passt nicht zu firmaName");
            }
            return existing;
        }

        if (firmaName == null) {
            throw AppException.badRequest("firmaId oder firmaName ist erforderlich");
        }

        return firmaRepository.findFirstByNameIgnoreCase(firmaName)
                .orElseGet(() -> {
                    FirmaEntity created = new FirmaEntity();
                    created.setName(firmaName);
                    return firmaRepository.save(created);
                });
    }

    private StellenausschreibungEntity resolveStelle(Long stelleId, String stellenbezeichnungRaw, Long firmaId) {
        String stellenbezeichnung = normalizeText(stellenbezeichnungRaw);
        if (stelleId != null) {
            StellenausschreibungEntity existing = stellenausschreibungRepository.findById(stelleId)
                    .orElseThrow(() -> AppException.badRequest("Stellenausschreibung existiert nicht"));
            if (!existing.getFirmaId().equals(firmaId)) {
                throw AppException.conflict("stellenausschreibungId passt nicht zur Firma");
            }
            if (stellenbezeichnung != null && !stellenbezeichnung.equalsIgnoreCase(existing.getTitel())) {
                throw AppException.conflict("stellenausschreibungId passt nicht zur stellenbezeichnung");
            }
            return existing;
        }

        if (stellenbezeichnung == null) {
            throw AppException.badRequest("stellenausschreibungId oder stellenbezeichnung ist erforderlich");
        }

        return stellenausschreibungRepository.findFirstByFirmaIdAndTitelIgnoreCase(firmaId, stellenbezeichnung)
                .orElseGet(() -> {
                    StellenausschreibungEntity created = new StellenausschreibungEntity();
                    created.setFirmaId(firmaId);
                    created.setTitel(stellenbezeichnung);
                    return stellenausschreibungRepository.save(created);
                });
    }

    private Long resolveStatusId(Long statusId) {
        if (statusId == null) {
            throw AppException.badRequest("statusId ist erforderlich");
        }
        if (!statusRepository.existsById(statusId)) {
            throw AppException.badRequest("Status existiert nicht");
        }
        return statusId;
    }

    private Long resolveAuthenticatedUserId() {
        Long userId = currentUserHolder.getUserId();
        if (userId == null) {
            throw AppException.badRequest("Kein angemeldeter Benutzer");
        }
        return userId;
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
        if (normalized.isEmpty()) {
            return null;
        }
        return normalized;
    }

    private String firstNonNull(String primary, String fallback) {
        return primary != null ? primary : fallback;
    }

    private <T> Optional<T> optionalOf(Optional<T> value) {
        return value != null ? value : Optional.empty();
    }

    private record ResolvedCreateReferences(
            BewerbungscontainerEntity container,
            FirmaEntity firma,
            StellenausschreibungEntity stelle,
            Long statusId
    ) {
    }

    private void validateDocumentReferences(BewerbungseintragEntity entity) {
        // Gelöschte Dokumente still nullen statt zu werfen
        if (!isDocumentValid(entity.getLebenslaufId(), DocumentType.LEBENSLAUF, DocumentScope.GLOBAL))
            entity.setLebenslaufId(null);
        if (!isDocumentValid(entity.getZertifikateId(), DocumentType.ZERTIFIKATE, DocumentScope.GLOBAL))
            entity.setZertifikateId(null);
        if (!isDocumentValid(entity.getZeugnisseId(), DocumentType.ZEUGNISSE, DocumentScope.GLOBAL))
            entity.setZeugnisseId(null);
        if (!isDocumentValid(entity.getAnschreibenId(), DocumentType.ANSCHREIBEN, DocumentScope.APPLICATION))
            entity.setAnschreibenId(null);
    }

    private boolean isDocumentValid(Long documentId, DocumentType type, DocumentScope scope) {
        if (documentId == null) return true;
        return documentRepository.findById(documentId)
                .map(d -> d.getType() == type && d.getScope() == scope)
                .orElse(false); // Dokument gelöscht → als ungültig behandeln
    }
}
