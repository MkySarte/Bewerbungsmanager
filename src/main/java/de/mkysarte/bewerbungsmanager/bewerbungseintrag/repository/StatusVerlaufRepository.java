package de.mkysarte.bewerbungsmanager.bewerbungseintrag.repository;

import de.mkysarte.bewerbungsmanager.bewerbungseintrag.entity.StatusVerlaufEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StatusVerlaufRepository extends JpaRepository<StatusVerlaufEntity, Long> {

    /**
     * Der Verlauf einer Bewerbung in <b>Einfügereihenfolge</b>, neueste Zeile zuerst. Der erste
     * Treffer zu einem Statustitel ist damit der maßgebliche.
     *
     * <p>Bewusst nicht nach {@code geaendertAm} sortiert: Seit sich Statusdaten nachträglich
     * korrigieren lassen, würde eine Rückdatierung sonst die Reihenfolge umwerfen und bei einem
     * Zyklus (abgeschickt → Entwurf → abgeschickt) eine ältere Zeile nach vorn spülen. Welche
     * Zeile gilt, hängt daran, wann sie geschrieben wurde — nicht daran, welches Datum
     * darin steht.
     */
    List<StatusVerlaufEntity> findByBewerbungseintragIdOrderByStatusVerlaufIdDesc(Long bewerbungseintragId);

    void deleteByBewerbungseintragId(Long bewerbungseintragId);
}
