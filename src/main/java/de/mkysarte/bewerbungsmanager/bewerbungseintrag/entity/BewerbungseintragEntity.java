package de.mkysarte.bewerbungsmanager.bewerbungseintrag.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "bewerbungseintrag")
public class BewerbungseintragEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "bewerbungseintrag_id")
    private Long bewerbungseintragId;

    @Column(name = "bewerbungscontainer_id", nullable = false)
    private Long bewerbungscontainerId;

    @Column(name = "firma_id", nullable = false)
    private Long firmaId;

    @Column(name = "stellenausschreibung_id", nullable = false)
    private Long stellenausschreibungId;

    @Column(name = "status_id", nullable = false)
    private Long statusId;

    @Column(name = "notiz")
    private String notiz;

    @Column(name = "url")
    private String url;

    @Column(name = "lebenslauf_id")
    private Long lebenslaufId;

    @Column(name = "zertifikate_id")
    private Long zertifikateId;

    @Column(name = "zeugnisse_id")
    private Long zeugnisseId;

    @Column(name = "anschreiben_id")
    private Long anschreibenId;

    /**
     * Soll für diese Bewerbung eine zusammengefuehrte PDF erzeugt werden können?
     *
     * <p>Bewusst als {@code Boolean} und nullable: SQLite kann einer Tabelle mit vorhandenen
     * Zeilen keine NOT-NULL-Spalte ohne Default hinzufügen. Bestehende Bewerbungen haben hier
     * also {@code null}, was wie {@code false} behandelt wird.
     */
    @Column(name = "pdf_bundle_gewuenscht")
    private Boolean pdfBundleGewuenscht;

    /**
     * Nachfassfrist in Tagen nur für diese Bewerbung.
     * {@code null} bedeutet: den global eingestellten Standard verwenden.
     */
    @Column(name = "nachfass_frist_tage")
    private Integer nachfassFristTage;

    /**
     * Rhythmus der Entwurfs-Erinnerung, als Name des Enums.
     * {@code null} bedeutet: den global eingestellten Standard verwenden.
     */
    @Column(name = "entwurf_erinnerung_intervall", length = 20)
    private String entwurfErinnerungIntervall;

    /**
     * Wann zuletzt eine Benachrichtigung zu dieser Bewerbung herausging.
     *
     * <p>Ohne diesen Merker würde ein "täglich" bei jedem Programmstart erneut feuern -
     * und die Erinnerung damit genau das Gegenteil von hilfreich.
     */
    @Column(name = "letzte_erinnerung_am")
    private LocalDate letzteErinnerungAm;

    @Column(name = "erstellt_am")
    private LocalDate erstelltAm;

    @Column(name = "aktualisiert_am")
    private LocalDate aktualisiertAm;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getBewerbungseintragId() {
        return bewerbungseintragId;
    }

    public void setBewerbungseintragId(Long bewerbungseintragId) {
        this.bewerbungseintragId = bewerbungseintragId;
    }

    public Long getBewerbungscontainerId() {
        return bewerbungscontainerId;
    }

    public void setBewerbungscontainerId(Long bewerbungscontainerId) {
        this.bewerbungscontainerId = bewerbungscontainerId;
    }

    public Long getFirmaId() {
        return firmaId;
    }

    public void setFirmaId(Long firmaId) {
        this.firmaId = firmaId;
    }

    public Long getStellenausschreibungId() {
        return stellenausschreibungId;
    }

    public void setStellenausschreibungId(Long stellenausschreibungId) {
        this.stellenausschreibungId = stellenausschreibungId;
    }

    public Long getStatusId() {
        return statusId;
    }

    public void setStatusId(Long statusId) {
        this.statusId = statusId;
    }

    public String getNotiz() {
        return notiz;
    }

    public void setNotiz(String notiz) {
        this.notiz = notiz;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Long getLebenslaufId() {
        return lebenslaufId;
    }

    public void setLebenslaufId(Long lebenslaufId) {
        this.lebenslaufId = lebenslaufId;
    }

    public Long getZertifikateId() {
        return zertifikateId;
    }

    public void setZertifikateId(Long zertifikateId) {
        this.zertifikateId = zertifikateId;
    }

    public Long getZeugnisseId() {
        return zeugnisseId;
    }

    public void setZeugnisseId(Long zeugnisseId) {
        this.zeugnisseId = zeugnisseId;
    }

    public Long getAnschreibenId() {
        return anschreibenId;
    }

    public void setAnschreibenId(Long anschreibenId) {
        this.anschreibenId = anschreibenId;
    }

    public LocalDate getErstelltAm() {
        return erstelltAm;
    }

    public void setErstelltAm(LocalDate erstelltAm) {
        this.erstelltAm = erstelltAm;
    }

    public LocalDate getAktualisiertAm() {
        return aktualisiertAm;
    }

    public void setAktualisiertAm(LocalDate aktualisiertAm) {
        this.aktualisiertAm = aktualisiertAm;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    /** {@code null} (Altbestand) zählt als "nicht gewünscht". */
    public boolean isPdfBundleGewuenscht() {
        return Boolean.TRUE.equals(pdfBundleGewuenscht);
    }

    public void setPdfBundleGewuenscht(boolean pdfBundleGewuenscht) {
        this.pdfBundleGewuenscht = pdfBundleGewuenscht;
    }

    public Integer getNachfassFristTage() {
        return nachfassFristTage;
    }

    public void setNachfassFristTage(Integer nachfassFristTage) {
        this.nachfassFristTage = nachfassFristTage;
    }

    public String getEntwurfErinnerungIntervall() {
        return entwurfErinnerungIntervall;
    }

    public void setEntwurfErinnerungIntervall(String entwurfErinnerungIntervall) {
        this.entwurfErinnerungIntervall = entwurfErinnerungIntervall;
    }

    public LocalDate getLetzteErinnerungAm() {
        return letzteErinnerungAm;
    }

    public void setLetzteErinnerungAm(LocalDate letzteErinnerungAm) {
        this.letzteErinnerungAm = letzteErinnerungAm;
    }
}
