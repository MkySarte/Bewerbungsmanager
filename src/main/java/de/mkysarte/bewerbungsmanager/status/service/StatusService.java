package de.mkysarte.bewerbungsmanager.status.service;

import de.mkysarte.bewerbungsmanager.common.exception.AppException;
import de.mkysarte.bewerbungsmanager.status.dto.CreateStatusRequest;
import de.mkysarte.bewerbungsmanager.status.dto.PatchStatusRequest;
import de.mkysarte.bewerbungsmanager.status.dto.StatusResponse;
import de.mkysarte.bewerbungsmanager.status.dto.UpdateStatusRequest;
import de.mkysarte.bewerbungsmanager.status.entity.StatusEntity;
import de.mkysarte.bewerbungsmanager.status.repository.StatusRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class StatusService {

    private final StatusRepository statusRepository;

    public StatusService(StatusRepository statusRepository) {
        this.statusRepository = statusRepository;
    }

    @Transactional
    public StatusResponse createStatus(CreateStatusRequest request) {
        if (statusRepository.existsByTitel(request.titel())) {
            throw AppException.conflict("Status-Titel ist bereits vorhanden");
        }

        StatusEntity entity = new StatusEntity();
        entity.setTitel(request.titel());
        entity.setBeschreibung(request.beschreibung());

        StatusEntity saved = statusRepository.save(entity);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public StatusResponse getStatusById(Long statusId) {
        StatusEntity status = statusRepository.findById(statusId)
                .orElseThrow(() -> AppException.notFound("Status nicht gefunden"));
        return toResponse(status);
    }

    @Transactional(readOnly = true)
    public List<StatusResponse> getAllStatus() {
        return statusRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public StatusResponse updateStatus(Long statusId, UpdateStatusRequest request) {
        StatusEntity status = statusRepository.findById(statusId)
                .orElseThrow(() -> AppException.notFound("Status nicht gefunden"));

        if (!status.getTitel().equals(request.titel()) && statusRepository.existsByTitel(request.titel())) {
            throw AppException.conflict("Status-Titel ist bereits vorhanden");
        }

        status.setTitel(request.titel());
        status.setBeschreibung(request.beschreibung());

        StatusEntity updated = statusRepository.save(status);
        return toResponse(updated);
    }

    @Transactional
    public StatusResponse patchStatus(Long statusId, PatchStatusRequest request) {
        StatusEntity status = statusRepository.findById(statusId)
                .orElseThrow(() -> AppException.notFound("Status nicht gefunden"));

        boolean hasTitel = request.titel() != null;
        boolean hasBeschreibung = request.beschreibung() != null;
        if (!hasTitel && !hasBeschreibung) {
            throw AppException.badRequest("Mindestens ein Feld für PATCH ist erforderlich");
        }

        if (hasTitel) {
            if (request.titel().isBlank()) {
                throw AppException.badRequest("Titel darf nicht leer sein");
            }
            if (!status.getTitel().equals(request.titel()) && statusRepository.existsByTitel(request.titel())) {
                throw AppException.conflict("Status-Titel ist bereits vorhanden");
            }
            status.setTitel(request.titel());
        }

        if (hasBeschreibung) {
            status.setBeschreibung(request.beschreibung());
        }

        StatusEntity updated = statusRepository.save(status);
        return toResponse(updated);
    }

    @Transactional
    public void deleteStatus(Long statusId) {
        if (!statusRepository.existsById(statusId)) {
            throw AppException.notFound("Status nicht gefunden");
        }
        statusRepository.deleteById(statusId);
    }

    private StatusResponse toResponse(StatusEntity status) {
        return new StatusResponse(
                status.getStatusId(),
                status.getTitel(),
                status.getBeschreibung(),
                status.getCreatedAt(),
                status.getUpdatedAt()
        );
    }
}
