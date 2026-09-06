package de.mkysarte.bewerbungsmanager.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Der Benutzer dieser Datenbank — pro Datei genau einer.
 *
 * <p>Hier steht bewusst <b>kein</b> Passwort und kein Hash. Die Zugangskontrolle ist die
 * Verschlüsselung selbst: Wer die Datei öffnen kann, hat sein Passwort bereits am
 * Schlüssel-Tresor bewiesen. Ein zusätzlicher Hash wäre eine zweite Wahrheitsquelle, die
 * nichts absichert, aber leicht für die eigentliche Prüfung gehalten wird.
 *
 * <p>Die Zeile dient der fachlichen Zuordnung (Bewerbungen, Container, Dokumente hängen an
 * der {@code userId}) und dem angezeigten Namen.
 */
@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "username", nullable = false, unique = true, length = 100)
    private String username;

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

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
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
