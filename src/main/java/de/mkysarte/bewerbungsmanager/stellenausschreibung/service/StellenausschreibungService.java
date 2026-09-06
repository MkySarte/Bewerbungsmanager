package de.mkysarte.bewerbungsmanager.stellenausschreibung.service;

import de.mkysarte.bewerbungsmanager.common.exception.AppException;
import de.mkysarte.bewerbungsmanager.firma.repository.FirmaRepository;
import de.mkysarte.bewerbungsmanager.stellenausschreibung.dto.CreateStellenausschreibungRequest;
import de.mkysarte.bewerbungsmanager.stellenausschreibung.dto.StellenausschreibungResponse;
import de.mkysarte.bewerbungsmanager.stellenausschreibung.entity.StellenausschreibungEntity;
import de.mkysarte.bewerbungsmanager.stellenausschreibung.repository.StellenausschreibungRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class StellenausschreibungService {

    private final StellenausschreibungRepository stellenausschreibungRepository;
    private final FirmaRepository firmaRepository;

    public StellenausschreibungService(
            StellenausschreibungRepository stellenausschreibungRepository,
            FirmaRepository firmaRepository
    ) {
        this.stellenausschreibungRepository = stellenausschreibungRepository;
        this.firmaRepository = firmaRepository;
    }

    @Transactional
    public StellenausschreibungResponse createStellenausschreibung(CreateStellenausschreibungRequest request) {
        if (!firmaRepository.existsById(request.firmaId())) {
            throw AppException.badRequest("Firma existiert nicht");
        }

        StellenausschreibungEntity entity = new StellenausschreibungEntity();
        entity.setFirmaId(request.firmaId());
        entity.setTitel(request.titel());
        entity.setUrl(request.url());
        entity.setVeroeffentlichtAm(request.veroeffentlichtAm());
        entity.setGefundenAm(request.gefundenAm());
        entity.setOrt(request.ort());
        entity.setRemoteAnteil(request.remoteAnteil());
        entity.setBeschreibung(request.beschreibung());

        StellenausschreibungEntity saved = stellenausschreibungRepository.save(entity);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public StellenausschreibungResponse getStellenausschreibungById(Long id) {
        StellenausschreibungEntity entity = stellenausschreibungRepository.findById(id)
                .orElseThrow(() -> AppException.notFound("Stellenausschreibung nicht gefunden"));
        return toResponse(entity);
    }

    @Transactional(readOnly = true)
    public List<StellenausschreibungResponse> getAllStellenausschreibungen() {
        return stellenausschreibungRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional
    public void deleteStellenausschreibung(Long id) {
        if (!stellenausschreibungRepository.existsById(id)) {
            throw AppException.notFound("Stellenausschreibung nicht gefunden");
        }
        stellenausschreibungRepository.deleteById(id);
    }

    private StellenausschreibungResponse toResponse(StellenausschreibungEntity entity) {
        return new StellenausschreibungResponse(
                entity.getStellenausschreibungId(),
                entity.getFirmaId(),
                entity.getTitel(),
                entity.getUrl(),
                entity.getVeroeffentlichtAm(),
                entity.getGefundenAm(),
                entity.getOrt(),
                entity.getRemoteAnteil(),
                entity.getBeschreibung(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
