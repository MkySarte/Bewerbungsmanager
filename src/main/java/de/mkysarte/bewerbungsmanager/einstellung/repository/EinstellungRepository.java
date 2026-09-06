package de.mkysarte.bewerbungsmanager.einstellung.repository;

import de.mkysarte.bewerbungsmanager.einstellung.entity.EinstellungEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EinstellungRepository extends JpaRepository<EinstellungEntity, String> {
}
