package de.mkysarte.bewerbungsmanager.unterlagen.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "unterlagen")
public class UnterlagenEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "unterlagen_id")
    private Long unterlagenId;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "lebenslauf_file")
    private byte[] lebenslaufFile;

    @Column(name = "zeugnisse_file")
    private byte[] zeugnisseFile;

    @Column(name = "zertifikate_file")
    private byte[] zertifikateFile;

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

    public Long getUnterlagenId() {
        return unterlagenId;
    }

    public void setUnterlagenId(Long unterlagenId) {
        this.unterlagenId = unterlagenId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public byte[] getLebenslaufFile() {
        return lebenslaufFile;
    }

    public void setLebenslaufFile(byte[] lebenslaufFile) {
        this.lebenslaufFile = lebenslaufFile;
    }

    public byte[] getZeugnisseFile() {
        return zeugnisseFile;
    }

    public void setZeugnisseFile(byte[] zeugnisseFile) {
        this.zeugnisseFile = zeugnisseFile;
    }

    public byte[] getZertifikateFile() {
        return zertifikateFile;
    }

    public void setZertifikateFile(byte[] zertifikateFile) {
        this.zertifikateFile = zertifikateFile;
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
