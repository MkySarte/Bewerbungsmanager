package de.mkysarte.bewerbungsmanager.session;

import org.springframework.stereotype.Component;

/**
 * Hält die userId des aktuell angemeldeten Benutzers (nach Login).
 *
 * Ersetzt SecurityContextHolder / JWT-Claims aus dem Legacy-Backend.
 * Wird in Services injiziert, die die aktuelle UserId benötigen
 * (z.B. BewerbungseintragService.resolveAuthenticatedUserId()).
 */
@Component
public class CurrentUserHolder {

    private volatile Long userId;
    private volatile String username;

    public Long getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public void set(Long userId, String username) {
        this.userId = userId;
        this.username = username;
    }

    public void clear() {
        this.userId = null;
        this.username = null;
    }

    public boolean isLoggedIn() {
        return userId != null;
    }
}
