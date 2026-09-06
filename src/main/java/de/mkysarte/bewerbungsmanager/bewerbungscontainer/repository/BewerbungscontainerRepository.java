package de.mkysarte.bewerbungsmanager.bewerbungscontainer.repository;

import de.mkysarte.bewerbungsmanager.bewerbungscontainer.entity.BewerbungscontainerEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BewerbungscontainerRepository extends JpaRepository<BewerbungscontainerEntity, Long> {

    boolean existsByUserId(Long userId);

    Optional<BewerbungscontainerEntity> findByUserId(Long userId);
}
