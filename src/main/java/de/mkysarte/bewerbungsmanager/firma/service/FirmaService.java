package de.mkysarte.bewerbungsmanager.firma.service;

import de.mkysarte.bewerbungsmanager.common.exception.AppException;
import de.mkysarte.bewerbungsmanager.firma.dto.CreateFirmaRequest;
import de.mkysarte.bewerbungsmanager.firma.dto.FirmaResponse;
import de.mkysarte.bewerbungsmanager.firma.entity.FirmaEntity;
import de.mkysarte.bewerbungsmanager.firma.repository.FirmaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class FirmaService {

    private final FirmaRepository firmaRepository;

    public FirmaService(FirmaRepository firmaRepository) {
        this.firmaRepository = firmaRepository;
    }

    @Transactional
    public FirmaResponse createFirma(CreateFirmaRequest request) {
        FirmaEntity entity = new FirmaEntity();
        entity.setName(request.name());
        entity.setKontaktPerson(request.kontaktPerson());
        entity.setTelefon(request.telefon());
        entity.setEmail(request.email());

        FirmaEntity saved = firmaRepository.save(entity);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public FirmaResponse getFirmaById(Long firmaId) {
        FirmaEntity firma = firmaRepository.findById(firmaId)
                .orElseThrow(() -> AppException.notFound("Firma nicht gefunden"));
        return toResponse(firma);
    }

    @Transactional(readOnly = true)
    public List<FirmaResponse> getAllFirmen() {
        return firmaRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void deleteFirma(Long firmaId) {
        if (!firmaRepository.existsById(firmaId)) {
            throw AppException.notFound("Firma nicht gefunden");
        }
        firmaRepository.deleteById(firmaId);
    }

    private FirmaResponse toResponse(FirmaEntity firma) {
        return new FirmaResponse(
                firma.getFirmaId(),
                firma.getName(),
                firma.getKontaktPerson(),
                firma.getTelefon(),
                firma.getEmail(),
                firma.getCreatedAt(),
                firma.getUpdatedAt()
        );
    }
}
