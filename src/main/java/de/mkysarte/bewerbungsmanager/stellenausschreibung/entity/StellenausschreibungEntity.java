package de.mkysarte.bewerbungsmanager.stellenausschreibung.entity;

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
@Table(name = "stellenausschreibung")
public class StellenausschreibungEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "stellenausschreibung_id")
    private Long stellenausschreibungId;

    @Column(name = "firma_id", nullable = false)
    private Long firmaId;

    @Column(name = "titel", nullable = false, length = 255)
    private String titel;

    @Column(name = "url")
    private String url;

    @Column(name = "veroeffentlicht_am")
    private LocalDate veroeffentlichtAm;

    @Column(name = "gefunden_am")
    private LocalDate gefundenAm;

    @Column(name = "ort", length = 255)
    private String ort;

    @Column(name = "remote_anteil", length = 100)
    private String remoteAnteil;

    @Column(name = "beschreibung")
    private String beschreibung;

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

    public Long getStellenausschreibungId() {
        return stellenausschreibungId;
    }

    public void setStellenausschreibungId(Long stellenausschreibungId) {
        this.stellenausschreibungId = stellenausschreibungId;
    }

    public Long getFirmaId() {
        return firmaId;
    }

    public void setFirmaId(Long firmaId) {
        this.firmaId = firmaId;
    }

    public String getTitel() {
        return titel;
    }

    public void setTitel(String titel) {
        this.titel = titel;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public LocalDate getVeroeffentlichtAm() {
        return veroeffentlichtAm;
    }

    public void setVeroeffentlichtAm(LocalDate veroeffentlichtAm) {
        this.veroeffentlichtAm = veroeffentlichtAm;
    }

    public LocalDate getGefundenAm() {
        return gefundenAm;
    }

    public void setGefundenAm(LocalDate gefundenAm) {
        this.gefundenAm = gefundenAm;
    }

    public String getOrt() {
        return ort;
    }

    public void setOrt(String ort) {
        this.ort = ort;
    }

    public String getRemoteAnteil() {
        return remoteAnteil;
    }

    public void setRemoteAnteil(String remoteAnteil) {
        this.remoteAnteil = remoteAnteil;
    }

    public String getBeschreibung() {
        return beschreibung;
    }

    public void setBeschreibung(String beschreibung) {
        this.beschreibung = beschreibung;
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
}
