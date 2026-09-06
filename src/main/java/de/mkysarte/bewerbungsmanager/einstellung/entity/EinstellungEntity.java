package de.mkysarte.bewerbungsmanager.einstellung.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Eine Einstellung als Schlüssel/Wert-Paar.
 *
 * <p>Liegt in der jeweiligen Datenbank — Demo-Zugang und echtes Konto haben dadurch
 * automatisch getrennte Einstellungen, ohne dass das irgendwo geprüft werden müsste.
 */
@Entity
@Table(name = "einstellungen")
public class EinstellungEntity {

    @Id
    @Column(name = "schluessel", length = 100)
    private String schluessel;

    @Column(name = "wert", length = 500)
    private String wert;

    public EinstellungEntity() {
    }

    public EinstellungEntity(String schluessel, String wert) {
        this.schluessel = schluessel;
        this.wert = wert;
    }

    public String getSchluessel() {
        return schluessel;
    }

    public void setSchluessel(String schluessel) {
        this.schluessel = schluessel;
    }

    public String getWert() {
        return wert;
    }

    public void setWert(String wert) {
        this.wert = wert;
    }
}
