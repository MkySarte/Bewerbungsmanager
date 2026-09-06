package de.mkysarte.bewerbungsmanager.bewerbungscontainer.service;

import de.mkysarte.bewerbungsmanager.common.exception.AppException;
import de.mkysarte.bewerbungsmanager.bewerbungscontainer.dto.BewerbungscontainerResponse;
import de.mkysarte.bewerbungsmanager.bewerbungscontainer.dto.CreateBewerbungscontainerRequest;
import de.mkysarte.bewerbungsmanager.bewerbungscontainer.entity.BewerbungscontainerEntity;
import de.mkysarte.bewerbungsmanager.bewerbungscontainer.repository.BewerbungscontainerRepository;
import de.mkysarte.bewerbungsmanager.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class BewerbungscontainerService {

    private static final String DEFAULT_CONTAINER_NAME = "Standardcontainer";

    private final BewerbungscontainerRepository bewerbungscontainerRepository;
    private final UserRepository userRepository;

    public BewerbungscontainerService(
            BewerbungscontainerRepository bewerbungscontainerRepository,
            UserRepository userRepository
    ) {
        this.bewerbungscontainerRepository = bewerbungscontainerRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public BewerbungscontainerResponse createContainer(CreateBewerbungscontainerRequest request) {
        if (!userRepository.existsById(request.userId())) {
            throw AppException.badRequest("User existiert nicht");
        }
        if (bewerbungscontainerRepository.existsByUserId(request.userId())) {
            throw AppException.conflict("Container für User existiert bereits");
        }

        BewerbungscontainerEntity entity = new BewerbungscontainerEntity();
        entity.setUserId(request.userId());
        entity.setName(DEFAULT_CONTAINER_NAME);

        BewerbungscontainerEntity saved = bewerbungscontainerRepository.save(entity);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public BewerbungscontainerResponse getContainerById(Long containerId) {
        BewerbungscontainerEntity entity = bewerbungscontainerRepository.findById(containerId)
                .orElseThrow(() -> AppException.notFound("Container nicht gefunden"));
        return toResponse(entity);
    }

    @Transactional(readOnly = true)
    public List<BewerbungscontainerResponse> getAllContainer() {
        return bewerbungscontainerRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional
    public void deleteContainer(Long containerId) {
        if (!bewerbungscontainerRepository.existsById(containerId)) {
            throw AppException.notFound("Container nicht gefunden");
        }
        bewerbungscontainerRepository.deleteById(containerId);
    }

    private BewerbungscontainerResponse toResponse(BewerbungscontainerEntity entity) {
        return new BewerbungscontainerResponse(
                entity.getBewerbungscontainerId(),
                entity.getUserId(),
                entity.getName(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
