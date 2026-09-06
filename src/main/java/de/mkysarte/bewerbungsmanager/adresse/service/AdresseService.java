package de.mkysarte.bewerbungsmanager.adresse.service;

import de.mkysarte.bewerbungsmanager.common.exception.AppException;
import de.mkysarte.bewerbungsmanager.adresse.dto.AdresseResponse;
import de.mkysarte.bewerbungsmanager.adresse.dto.CreateAdresseRequest;
import de.mkysarte.bewerbungsmanager.adresse.entity.AdresseEntity;
import de.mkysarte.bewerbungsmanager.adresse.repository.AdresseRepository;
import de.mkysarte.bewerbungsmanager.firma.repository.FirmaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AdresseService {

    private final AdresseRepository adresseRepository;
    private final FirmaRepository firmaRepository;

    public AdresseService(AdresseRepository adresseRepository, FirmaRepository firmaRepository) {
        this.adresseRepository = adresseRepository;
        this.firmaRepository = firmaRepository;
    }

    @Transactional
    public AdresseResponse createAdresse(CreateAdresseRequest request) {
        if (!firmaRepository.existsById(request.firmaId())) {
            throw AppException.badRequest("Firma existiert nicht");
        }
        if (adresseRepository.existsByFirmaId(request.firmaId())) {
            throw AppException.conflict("Adresse für Firma existiert bereits");
        }

        AdresseEntity entity = new AdresseEntity();
        entity.setFirmaId(request.firmaId());
        entity.setStrasse(request.strasse());
        entity.setHausnummer(request.hausnummer());
        entity.setPlz(request.plz());
        entity.setOrt(request.ort());
        entity.setLand(request.land());

        AdresseEntity saved = adresseRepository.save(entity);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public AdresseResponse getAdresseById(Long adresseId) {
        AdresseEntity entity = adresseRepository.findById(adresseId)
                .orElseThrow(() -> AppException.notFound("Adresse nicht gefunden"));
        return toResponse(entity);
    }

    @Transactional(readOnly = true)
    public List<AdresseResponse> getAllAdressen() {
        return adresseRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional
    public void deleteAdresse(Long adresseId) {
        if (!adresseRepository.existsById(adresseId)) {
            throw AppException.notFound("Adresse nicht gefunden");
        }
        adresseRepository.deleteById(adresseId);
    }

    private AdresseResponse toResponse(AdresseEntity entity) {
        return new AdresseResponse(
                entity.getAdresseId(),
                entity.getFirmaId(),
                entity.getStrasse(),
                entity.getHausnummer(),
                entity.getPlz(),
                entity.getOrt(),
                entity.getLand(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
