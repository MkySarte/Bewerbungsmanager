package de.mkysarte.bewerbungsmanager.ui;

import javafx.scene.Parent;
import org.springframework.stereotype.Component;

/**
 * Verwaltet den hellen/dunklen Farbmodus der Desktop-App.
 * Persistent über Screens hinweg (Spring Singleton).
 */
@Component
public class ThemeService {

    private boolean dark = false;

    public boolean isDark() {
        return dark;
    }

    public void toggle() {
        dark = !dark;
    }

    /** Wendet den aktuellen Theme-Zustand auf einen Root-Node an. */
    public void apply(Parent root) {
        if (dark) {
            if (!root.getStyleClass().contains("dark")) {
                root.getStyleClass().add("dark");
            }
        } else {
            root.getStyleClass().remove("dark");
        }
    }
}
