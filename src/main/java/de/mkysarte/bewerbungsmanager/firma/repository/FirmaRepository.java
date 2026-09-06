package de.mkysarte.bewerbungsmanager.firma.repository;

import de.mkysarte.bewerbungsmanager.firma.entity.FirmaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FirmaRepository extends JpaRepository<FirmaEntity, Long> {

    Optional<FirmaEntity> findFirstByNameIgnoreCase(String name);
}
