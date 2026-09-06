package de.mkysarte.bewerbungsmanager.unterlagen.service;

import de.mkysarte.bewerbungsmanager.common.exception.AppException;
import de.mkysarte.bewerbungsmanager.unterlagen.dto.CreateUnterlagenRequest;
import de.mkysarte.bewerbungsmanager.unterlagen.dto.UnterlagenResponse;
import de.mkysarte.bewerbungsmanager.unterlagen.entity.UnterlagenEntity;
import de.mkysarte.bewerbungsmanager.unterlagen.repository.UnterlagenRepository;
import de.mkysarte.bewerbungsmanager.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UnterlagenService {

    private final UnterlagenRepository unterlagenRepository;
    private final UserRepository userRepository;

    public UnterlagenService(UnterlagenRepository unterlagenRepository, UserRepository userRepository) {
        this.unterlagenRepository = unterlagenRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public UnterlagenResponse createUnterlagen(CreateUnterlagenRequest request) {
        if (!userRepository.existsById(request.userId())) {
            throw AppException.badRequest("User existiert nicht");
        }
        if (unterlagenRepository.existsByUserId(request.userId())) {
            throw AppException.conflict("Unterlagen für User existieren bereits");
        }

        UnterlagenEntity entity = new UnterlagenEntity();
        entity.setUserId(request.userId());
        entity.setLebenslaufFile(request.lebenslaufFile());
        entity.setZeugnisseFile(request.zeugnisseFile());
        entity.setZertifikateFile(request.zertifikateFile());

        UnterlagenEntity saved = unterlagenRepository.save(entity);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public UnterlagenResponse getUnterlagenById(Long unterlagenId) {
        UnterlagenEntity unterlagen = unterlagenRepository.findById(unterlagenId)
                .orElseThrow(() -> AppException.notFound("Unterlagen nicht gefunden"));
        return toResponse(unterlagen);
    }

    @Transactional(readOnly = true)
    public List<UnterlagenResponse> getAllUnterlagen() {
        return unterlagenRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional
    public void deleteUnterlagen(Long unterlagenId) {
        if (!unterlagenRepository.existsById(unterlagenId)) {
            throw AppException.notFound("Unterlagen nicht gefunden");
        }
        unterlagenRepository.deleteById(unterlagenId);
    }

    private UnterlagenResponse toResponse(UnterlagenEntity entity) {
        return new UnterlagenResponse(
                entity.getUnterlagenId(),
                entity.getUserId(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
