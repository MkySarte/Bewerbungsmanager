package de.mkysarte.bewerbungsmanager.common.security;

import de.mkysarte.bewerbungsmanager.bewerbungscontainer.entity.BewerbungscontainerEntity;
import de.mkysarte.bewerbungsmanager.bewerbungscontainer.repository.BewerbungscontainerRepository;
import de.mkysarte.bewerbungsmanager.firma.entity.FirmaEntity;
import de.mkysarte.bewerbungsmanager.firma.repository.FirmaRepository;
import de.mkysarte.bewerbungsmanager.status.entity.StatusEntity;
import de.mkysarte.bewerbungsmanager.status.repository.StatusRepository;
import de.mkysarte.bewerbungsmanager.stellenausschreibung.entity.StellenausschreibungEntity;
import de.mkysarte.bewerbungsmanager.stellenausschreibung.repository.StellenausschreibungRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ReferenceDataBootstrapService {

    private static final String DEFAULT_CONTAINER_NAME = "Standardcontainer";

    private final BewerbungscontainerRepository bewerbungscontainerRepository;
    private final FirmaRepository firmaRepository;
    private final StellenausschreibungRepository stellenausschreibungRepository;
    private final StatusRepository statusRepository;

    public ReferenceDataBootstrapService(
            BewerbungscontainerRepository bewerbungscontainerRepository,
            FirmaRepository firmaRepository,
            StellenausschreibungRepository stellenausschreibungRepository,
            StatusRepository statusRepository
    ) {
        this.bewerbungscontainerRepository = bewerbungscontainerRepository;
        this.firmaRepository = firmaRepository;
        this.stellenausschreibungRepository = stellenausschreibungRepository;
        this.statusRepository = statusRepository;
    }

    @Transactional
    public void ensureMinimumReferenceData(Long userId) {
        ensureContainer(userId);
        ensureStatus("ENTWURF", "Initialer Bearbeitungsstatus");
        ensureStatus("ABGESCHICKT", "Bewerbung wurde abgeschickt");
        ensureStatus("ABSAGE", "Bewerbung wurde abgelehnt");
        ensureStatus("ERFOLG", "Bewerbung erfolgreich");

        ensureFirmaAndStelle();
    }

    private void ensureContainer(Long userId) {
        BewerbungscontainerEntity existing = bewerbungscontainerRepository.findByUserId(userId).orElse(null);
        if (existing != null) {
            if (existing.getName() == null || existing.getName().isBlank()) {
                existing.setName(DEFAULT_CONTAINER_NAME);
                bewerbungscontainerRepository.save(existing);
            }
            return;
        }
        BewerbungscontainerEntity created = new BewerbungscontainerEntity();
        created.setUserId(userId);
        created.setName(DEFAULT_CONTAINER_NAME);
        bewerbungscontainerRepository.save(created);
    }

    private void ensureStatus(String titel, String beschreibung) {
        if (statusRepository.existsByTitel(titel)) {
            return;
        }
        StatusEntity status = new StatusEntity();
        status.setTitel(titel);
        status.setBeschreibung(beschreibung);
        statusRepository.save(status);
    }

    private void ensureFirmaAndStelle() {
        List<FirmaEntity> firmen = firmaRepository.findAll();
        FirmaEntity firma;
        if (firmen.isEmpty()) {
            firma = new FirmaEntity();
            firma.setName("Musterfirma GmbH");
            firma.setKontaktPerson("Recruiting Team");
            firma.setEmail("jobs@example.org");
            firma = firmaRepository.save(firma);
        } else {
            firma = firmen.getFirst();
        }

        if (!stellenausschreibungRepository.findAll().isEmpty()) {
            return;
        }

        StellenausschreibungEntity stelle = new StellenausschreibungEntity();
        stelle.setFirmaId(firma.getFirmaId());
        stelle.setTitel("Initiativbewerbung");
        stelle.setUrl("https://example.org/jobs");
        stelle.setOrt("Remote");
        stelle.setRemoteAnteil("100%");
        stelle.setBeschreibung("Automatisch erzeugte Referenz-Stellenausschreibung");
        stellenausschreibungRepository.save(stelle);
    }
}
