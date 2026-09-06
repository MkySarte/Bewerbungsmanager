package de.mkysarte.bewerbungsmanager.session;

import de.mkysarte.bewerbungsmanager.bewerbungscontainer.entity.BewerbungscontainerEntity;
import de.mkysarte.bewerbungsmanager.bewerbungscontainer.repository.BewerbungscontainerRepository;
import de.mkysarte.bewerbungsmanager.bewerbungseintrag.entity.BewerbungseintragEntity;
import de.mkysarte.bewerbungsmanager.bewerbungseintrag.repository.BewerbungseintragRepository;
import de.mkysarte.bewerbungsmanager.common.crypto.DbSession;
import de.mkysarte.bewerbungsmanager.common.security.ReferenceDataBootstrapService;
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
import de.mkysarte.bewerbungsmanager.user.entity.UserEntity;
import de.mkysarte.bewerbungsmanager.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.nio.charset.StandardCharsets;

/**
 * Legt die Musterdaten des Demo-Zugangs an.
 *
 * <p>Läuft <b>ausschließlich</b> in der Demo-Sitzung und damit nur in {@code demo.db}. Das echte
 * Konto bekommt niemals Musterdaten — es startet leer, so wie man es von einer frisch
 * eingerichteten Anwendung erwartet.
 *
 * <p>Firmen, Personen und Adressen stammen bewusst aus dem Muster-/Beispiel-Namensraum
 * (Musterfirma, Max Mustermann, {@code example.org}). Erfundene, aber real klingende Namen
 * könnten zufällig existierende Unternehmen treffen.
 */
@Component
@Order(20)
@RequiredArgsConstructor
public class DesktopDemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DesktopDemoDataSeeder.class);

    private final BewerbungseintragRepository bewerbungseintragRepository;
    private final BewerbungscontainerRepository bewerbungscontainerRepository;
    private final FirmaRepository firmaRepository;
    private final StellenausschreibungRepository stellenausschreibungRepository;
    private final StatusRepository statusRepository;
    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;
    private final ReferenceDataBootstrapService referenceDataBootstrapService;
    private final DbSession dbSession;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!dbSession.demo()) {
            // Das echte Konto startet leer - hier wird nichts angelegt.
            return;
        }

        UserEntity user = userRepository.findByUsername(dbSession.username()).orElse(null);
        if (user == null) {
            log.warn("Demo-Benutzer nicht gefunden - Musterdaten wurden nicht angelegt");
            return;
        }

        referenceDataBootstrapService.ensureMinimumReferenceData(user.getUserId());
        seedDemoDataForUser(user.getUserId());
    }

    @Transactional
    public void seedDemoDataForUser(Long userId) {
        if (userId == null) {
            return;
        }

        BewerbungscontainerEntity container = bewerbungscontainerRepository.findByUserId(userId)
                .orElseGet(() -> {
                    BewerbungscontainerEntity created = new BewerbungscontainerEntity();
                    created.setUserId(userId);
                    created.setName("Standardcontainer");
                    return bewerbungscontainerRepository.save(created);
                });

        long existingCount = bewerbungseintragRepository.findAll().stream()
                .filter(entry -> container.getBewerbungscontainerId().equals(entry.getBewerbungscontainerId()))
                .count();
        if (existingCount >= 4) {
            return;
        }

        StatusEntity entwurf = requireStatus("ENTWURF");
        StatusEntity abgeschickt = requireStatus("ABGESCHICKT");
        StatusEntity absage = requireStatus("ABSAGE");
        StatusEntity erfolg = requireStatus("ERFOLG");

        Long lebenslaufId = ensureGlobalDocument("lebenslauf-demo.pdf", DocumentType.LEBENSLAUF);
        Long zertifikateId = ensureGlobalDocument("zertifikate-demo.pdf", DocumentType.ZERTIFIKATE);
        Long zeugnisseId = ensureGlobalDocument("zeugnisse-demo.pdf", DocumentType.ZEUGNISSE);

        int missingEntries = (int) (4 - existingCount);
        if (missingEntries >= 1) {
            seedEntry(container, entwurf,
                    "Musterfirma GmbH", "Max Mustermann", "bewerbung@example.org", "030 000000 1", "Musterstadt",
                    "Junior Java Developer", "https://www.example.org/jobs/java-developer",
                    "Telefonscreen für nächste Woche vorbereitet.", LocalDate.now().minusDays(2),
                    lebenslaufId, null, null, true);
        }
        if (missingEntries >= 2) {
            seedEntry(container, abgeschickt,
                    "Beispiel AG", "Erika Mustermann", "jobs@example.org", "040 000000 2", "Beispielstadt",
                    "Frontend Entwickler", "https://www.example.org/karriere/frontend",
                    "Bewerbung abgeschickt, warte auf Rückmeldung.", LocalDate.now().minusDays(6),
                    lebenslaufId, zertifikateId, null, false);
        }
        if (missingEntries >= 3) {
            seedEntry(container, absage,
                    "Muster Handels KG", "Erika Musterfrau", "karriere@example.org", "069 000000 3", "Musterhausen",
                    "Full Stack Engineer", "https://www.example.org/jobs/full-stack",
                    "Absage nach dem Erstgespräch erhalten.", LocalDate.now().minusDays(14),
                    lebenslaufId, null, null, false);
        }
        if (missingEntries >= 4) {
            seedEntry(container, erfolg,
                    "Beispiel Software SE", "Max Musterfrau", "team@example.org", "089 000000 4", "Musterberg",
                    "Softwareentwickler Java", "https://www.example.org/careers/java",
                    "Zusage erhalten, Vertragsgespräch läuft.", LocalDate.now().minusDays(20),
                    lebenslaufId, zertifikateId, zeugnisseId, true);
        }

        log.info("{} Muster-Bewerbungen für den Demo-Zugang angelegt", missingEntries);
    }

    private void seedEntry(
            BewerbungscontainerEntity container,
            StatusEntity status,
            String companyName,
            String contactPerson,
            String email,
            String phone,
            String location,
            String title,
            String url,
            String note,
            LocalDate createdAt,
            Long lebenslaufId,
            Long zertifikateId,
            Long zeugnisseId,
            boolean withCoverLetter
    ) {
        FirmaEntity firma = new FirmaEntity();
        firma.setName(companyName);
        firma.setKontaktPerson(contactPerson);
        firma.setEmail(email);
        firma.setTelefon(phone);
        firma = firmaRepository.save(firma);

        StellenausschreibungEntity stelle = new StellenausschreibungEntity();
        stelle.setFirmaId(firma.getFirmaId());
        stelle.setTitel(title);
        stelle.setOrt(location);
        stelle.setUrl(url);
        stelle.setBeschreibung(note);
        stelle.setGefundenAm(createdAt);
        stelle.setVeroeffentlichtAm(createdAt.minusDays(2));
        stelle = stellenausschreibungRepository.save(stelle);

        BewerbungseintragEntity entry = new BewerbungseintragEntity();
        entry.setBewerbungscontainerId(container.getBewerbungscontainerId());
        entry.setFirmaId(firma.getFirmaId());
        entry.setStellenausschreibungId(stelle.getStellenausschreibungId());
        entry.setStatusId(status.getStatusId());
        entry.setUrl(url);
        entry.setNotiz(note);
        entry.setErstelltAm(createdAt);
        entry.setAktualisiertAm(createdAt.plusDays(1));
        entry.setLebenslaufId(lebenslaufId);
        entry.setZertifikateId(zertifikateId);
        entry.setZeugnisseId(zeugnisseId);
        BewerbungseintragEntity saved = bewerbungseintragRepository.save(entry);

        if (withCoverLetter) {
            DocumentEntity coverLetter = createDocument(
                    companyName.toLowerCase().replace(" ", "-") + "-anschreiben.pdf",
                    DocumentType.ANSCHREIBEN,
                    DocumentScope.APPLICATION,
                    saved.getBewerbungseintragId()
            );
            saved.setAnschreibenId(coverLetter.getDocumentId());
            bewerbungseintragRepository.save(saved);
        }
    }

    private Long ensureGlobalDocument(String fileName, DocumentType type) {
        return documentRepository.findByScopeAndType(DocumentScope.GLOBAL, type).stream()
                .findFirst()
                .map(DocumentEntity::getDocumentId)
                .orElseGet(() -> createDocument(fileName, type, DocumentScope.GLOBAL, null).getDocumentId());
    }

    private DocumentEntity createDocument(String fileName, DocumentType type, DocumentScope scope, Long applicationId) {
        DocumentEntity document = new DocumentEntity();
        document.setFileName(fileName);
        document.setContentType("application/pdf");
        document.setType(type);
        document.setScope(scope);
        document.setApplicationId(applicationId);
        byte[] content = ("Demo content for " + fileName).getBytes(StandardCharsets.UTF_8);
        document.setContent(content);
        document.setSizeBytes((long) content.length);
        return documentRepository.save(document);
    }

    private StatusEntity requireStatus(String titel) {
        return statusRepository.findByTitel(titel)
                .orElseThrow(() -> new IllegalStateException("Status fehlt: " + titel));
    }
}
