package de.mkysarte.bewerbungsmanager.adresse.repository;

import de.mkysarte.bewerbungsmanager.adresse.entity.AdresseEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdresseRepository extends JpaRepository<AdresseEntity, Long> {

    boolean existsByFirmaId(Long firmaId);
}
