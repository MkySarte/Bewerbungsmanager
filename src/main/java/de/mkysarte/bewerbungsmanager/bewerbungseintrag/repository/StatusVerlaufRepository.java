package de.mkysarte.bewerbungsmanager.bewerbungseintrag.repository;

import de.mkysarte.bewerbungsmanager.bewerbungseintrag.entity.StatusVerlaufEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StatusVerlaufRepository extends JpaRepository<StatusVerlaufEntity, Long> {

    /**
     * Verlauf einer Bewerbung, neueste Änderung zuerst.
     * Der erste Treffer zu einem Statustitel ist damit der aktuellste.
     */
    List<StatusVerlaufEntity> findByBewerbungseintragIdOrderByGeaendertAmDesc(Long bewerbungseintragId);

    void deleteByBewerbungseintragId(Long bewerbungseintragId);
}
