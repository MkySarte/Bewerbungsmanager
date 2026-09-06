package de.mkysarte.bewerbungsmanager.bewerbungseintrag.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Hält fest, wann eine Bewerbung welchen Status bekommen hat.
 *
 * <p>Ohne diesen Verlauf lässt sich nicht sagen, seit wann eine Bewerbung abgeschickt ist —
 * {@code aktualisiertAm} am Eintrag verschiebt sich bei jeder Notizänderung. Erst damit
 * werden „seit 16 Tagen abgeschickt" und ein Absage-Datum überhaupt möglich.
 *
 * <p>Der {@code statusTitel} wird bewusst mitgespeichert statt nur die {@code statusId}:
 * Wird ein Status später umbenannt oder gelöscht, bleibt der Verlauf trotzdem lesbar.
 */
@Entity
@Table(name = "bewerbungseintrag_status_verlauf")
public class StatusVerlaufEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "status_verlauf_id")
    private Long statusVerlaufId;

    @Column(name = "bewerbungseintrag_id", nullable = false)
    private Long bewerbungseintragId;

    @Column(name = "status_id", nullable = false)
    private Long statusId;

    @Column(name = "status_titel", length = 100)
    private String statusTitel;

    @Column(name = "geaendert_am", nullable = false)
    private LocalDateTime geaendertAm;

    @PrePersist
    void prePersist() {
        if (geaendertAm == null) {
            geaendertAm = LocalDateTime.now();
        }
    }

    public Long getStatusVerlaufId() {
        return statusVerlaufId;
    }

    public void setStatusVerlaufId(Long statusVerlaufId) {
        this.statusVerlaufId = statusVerlaufId;
    }

    public Long getBewerbungseintragId() {
        return bewerbungseintragId;
    }

    public void setBewerbungseintragId(Long bewerbungseintragId) {
        this.bewerbungseintragId = bewerbungseintragId;
    }

    public Long getStatusId() {
        return statusId;
    }

    public void setStatusId(Long statusId) {
        this.statusId = statusId;
    }

    public String getStatusTitel() {
        return statusTitel;
    }

    public void setStatusTitel(String statusTitel) {
        this.statusTitel = statusTitel;
    }

    public LocalDateTime getGeaendertAm() {
        return geaendertAm;
    }

    public void setGeaendertAm(LocalDateTime geaendertAm) {
        this.geaendertAm = geaendertAm;
    }
}
