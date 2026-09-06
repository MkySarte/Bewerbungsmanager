package de.mkysarte.bewerbungsmanager.stellenausschreibung.repository;

import de.mkysarte.bewerbungsmanager.stellenausschreibung.entity.StellenausschreibungEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StellenausschreibungRepository extends JpaRepository<StellenausschreibungEntity, Long> {

    Optional<StellenausschreibungEntity> findFirstByFirmaIdAndTitelIgnoreCase(Long firmaId, String titel);
}
