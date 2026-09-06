package de.mkysarte.bewerbungsmanager.status.repository;

import de.mkysarte.bewerbungsmanager.status.entity.StatusEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StatusRepository extends JpaRepository<StatusEntity, Long> {

    boolean existsByTitel(String titel);

    Optional<StatusEntity> findByTitel(String titel);
}
