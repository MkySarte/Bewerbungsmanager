package de.mkysarte.bewerbungsmanager.session;

import de.mkysarte.bewerbungsmanager.bewerbungseintrag.repository.BewerbungseintragRepository;
import de.mkysarte.bewerbungsmanager.common.InitialAccess;
import de.mkysarte.bewerbungsmanager.common.crypto.DbSession;
import de.mkysarte.bewerbungsmanager.common.crypto.VaultService;
import de.mkysarte.bewerbungsmanager.firma.repository.FirmaRepository;
import de.mkysarte.bewerbungsmanager.user.repository.UserRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fährt eine echte Demo-Sitzung hoch, so wie es die Anwendung nach der Anmeldung tut:
 * verschlüsselte SQLite-Datei, {@link DbSession} als Bean, Bootstrap und Seeder.
 *
 * <p>Prüft die beiden Zusagen, die sich sonst nur von Hand kontrollieren ließen: dass die
 * Musterdaten tatsächlich entstehen und dass die Datei auf der Platte verschlüsselt ist.
 */
@SpringBootTest(classes = {
        de.mkysarte.bewerbungsmanager.BewerbungsmanagerDesktopApplication.class,
        DemoSessionIntegrationTest.TestBeans.class
})
class DemoSessionIntegrationTest {

    private static final Path TEMP_DIR;
    private static final Path DEMO_DB;

    static {
        try {
            TEMP_DIR = Files.createTempDirectory("bewerbungsmanager-demo-test");
            DEMO_DB = TEMP_DIR.resolve("demo.db");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static DbSession demoSession() {
        String key = VaultService.toHex(VaultService.deriveDemoKey(InitialAccess.PASSWORD));
        return new DbSession(true, InitialAccess.USERNAME, DEMO_DB, key);
    }

    @DynamicPropertySource
    static void databaseUrl(DynamicPropertyRegistry registry) {
        registry.add("app.db.url", () -> demoSession().jdbcUrl());
    }

    @TestConfiguration
    static class TestBeans {
        @Bean
        DbSession dbSession() {
            return demoSession();
        }

        /** Ohne laufende JavaFX-Anwendung gibt es nichts abzumelden. */
        /** Ohne Infobereich im Test - meldet still nichts. */
        @Bean
        de.mkysarte.bewerbungsmanager.benachrichtigung.TrayService trayService() {
            return new de.mkysarte.bewerbungsmanager.benachrichtigung.TrayService();
        }

        @Bean
        de.mkysarte.bewerbungsmanager.ui.AppShell appShell() {
            return () -> {
            };
        }
    }

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private BewerbungseintragRepository bewerbungseintragRepository;
    @Autowired
    private FirmaRepository firmaRepository;

    @Test
    void demoSessionShouldContainExactlyOneUser() {
        assertEquals(1, userRepository.count());
        assertTrue(userRepository.findByUsername(InitialAccess.USERNAME).isPresent());
    }

    @Test
    void demoSessionShouldBeSeededWithSampleApplications() {
        assertEquals(4, bewerbungseintragRepository.count());
    }

    @Test
    void sampleCompaniesShouldUseTheMusterNamespace() {
        // Erfundene, aber real klingende Firmennamen könnten existierende Unternehmen treffen.
        firmaRepository.findAll().forEach(firma -> {
            String name = firma.getName().toLowerCase();
            assertTrue(name.contains("muster") || name.contains("beispiel"),
                    "Demo-Firma ausserhalb des Muster-/Beispiel-Namensraums: " + firma.getName());
            assertTrue(firma.getEmail().endsWith("@example.org"),
                    "Demo-E-Mail ausserhalb von example.org: " + firma.getEmail());
        });
    }

    /** Der eigentliche Punkt der Uebung: auf der Platte darf nichts im Klartext stehen. */
    @Test
    void databaseFileOnDiskShouldBeEncrypted() throws IOException {
        assertTrue(Files.isRegularFile(DEMO_DB), "Datenbankdatei wurde nicht angelegt");

        byte[] bytes = Files.readAllBytes(DEMO_DB);
        String raw = new String(bytes, StandardCharsets.ISO_8859_1);

        assertFalse(raw.startsWith("SQLite format 3"),
                "Die Datei traegt noch den unverschlüsselten SQLite-Header");
        assertFalse(raw.contains("Musterfirma"),
                "Ein Firmenname steht im Klartext in der Datenbankdatei");
        assertFalse(raw.contains("Mustermann"),
                "Ein Personenname steht im Klartext in der Datenbankdatei");
    }

    @AfterAll
    static void cleanUp() throws IOException {
        if (!Files.exists(TEMP_DIR)) {
            return;
        }
        try (var paths = Files.walk(TEMP_DIR)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Aufraeumen im Temp-Verzeichnis darf den Test nicht zum Scheitern bringen.
                }
            });
        }
    }
}
