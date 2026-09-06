package de.mkysarte.bewerbungsmanager;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Spring Boot Konfigurationsklasse.
 *
 * Wird als Quelle für den ComponentScan und die Auto-Konfiguration verwendet.
 * Die eigentliche main()-Methode liegt in BewerbungsmanagerApp (JavaFX Entry Point).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class BewerbungsmanagerDesktopApplication {
}
