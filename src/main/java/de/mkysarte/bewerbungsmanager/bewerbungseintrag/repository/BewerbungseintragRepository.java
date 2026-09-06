package de.mkysarte.bewerbungsmanager.bewerbungseintrag.repository;

import de.mkysarte.bewerbungsmanager.bewerbungseintrag.entity.BewerbungseintragEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BewerbungseintragRepository extends JpaRepository<BewerbungseintragEntity, Long> {
}
