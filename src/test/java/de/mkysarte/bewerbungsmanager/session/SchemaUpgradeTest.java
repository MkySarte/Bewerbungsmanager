package de.mkysarte.bewerbungsmanager.session;

import de.mkysarte.bewerbungsmanager.bewerbungseintrag.dto.BewerbungseintragResponse;
import de.mkysarte.bewerbungsmanager.bewerbungseintrag.service.BewerbungseintragService;
import de.mkysarte.bewerbungsmanager.common.InitialAccess;
import de.mkysarte.bewerbungsmanager.common.crypto.DbSession;
import de.mkysarte.bewerbungsmanager.common.crypto.VaultService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Sichert den Fall ab, der beim Nachrüsten von Feldern leicht kaputtgeht: eine
 * <b>bereits befüllte</b> Datenbank, die um neue Spalten und Tabellen erweitert wird.
 *
 * <p>Der konkrete Stolperstein: SQLite kann einer Tabelle mit vorhandenen Zeilen keine
 * NOT-NULL-Spalte ohne Default hinzufügen. Genau deshalb ist {@code pdf_bundle_gewuenscht}
 * nullable. Dieser Test baut eine Datenbank im alten Schema auf, befüllt sie und fährt dann
 * den heutigen Spring-Context darüber hoch.
 */
@SpringBootTest(classes = {
        de.mkysarte.bewerbungsmanager.BewerbungsmanagerDesktopApplication.class,
        SchemaUpgradeTest.TestBeans.class
})
class SchemaUpgradeTest {

    private static final Path TEMP_DIR;
    private static final Path DB;

    static {
        try {
            TEMP_DIR = Files.createTempDirectory("bewerbungsmanager-upgrade-test");
            DB = TEMP_DIR.resolve("alt.db");
            legeAltesSchemaAn();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static DbSession session() {
        String key = VaultService.toHex(VaultService.deriveDemoKey(InitialAccess.PASSWORD));
        // Bewusst KEINE Demo-Sitzung: sonst würde der Seeder die Musterdaten dazulegen
        // und der Altbestand ginge in der Menge unter.
        return new DbSession(false, "altnutzer", DB, key);
    }

    /**
     * Legt das Schema an, wie es <b>vor</b> diesen Funktionen aussah: ohne
     * {@code pdf_bundle_gewuenscht}, ohne {@code nachfass_frist_tage}, ohne Verlaufstabelle —
     * und mit einer Zeile darin, denn erst vorhandene Zeilen machen das Nachrüsten heikel.
     */
    private static void legeAltesSchemaAn() throws SQLException {
        try (Connection c = DriverManager.getConnection(session().jdbcUrl());
             Statement s = c.createStatement()) {
            s.execute("""
                    CREATE TABLE bewerbungseintrag (
                        bewerbungseintrag_id INTEGER PRIMARY KEY AUTOINCREMENT,
                        bewerbungscontainer_id INTEGER NOT NULL,
                        firma_id INTEGER NOT NULL,
                        stellenausschreibung_id INTEGER NOT NULL,
                        status_id INTEGER NOT NULL,
                        notiz TEXT, url TEXT,
                        lebenslauf_id INTEGER, zertifikate_id INTEGER,
                        zeugnisse_id INTEGER, anschreiben_id INTEGER,
                        erstellt_am DATE, aktualisiert_am DATE,
                        created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL)
                    """);
            s.execute("""
                    INSERT INTO bewerbungseintrag
                        (bewerbungscontainer_id, firma_id, stellenausschreibung_id, status_id,
                         notiz, created_at, updated_at)
                    VALUES (1, 1, 1, 1, 'Altbestand', '2026-01-01 10:00:00', '2026-01-01 10:00:00')
                    """);
        }
    }

    @DynamicPropertySource
    static void databaseUrl(DynamicPropertyRegistry registry) {
        registry.add("app.db.url", () -> session().jdbcUrl());
    }

    @TestConfiguration
    static class TestBeans {
        @Bean
        DbSession dbSession() {
            return session();
        }

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
    private BewerbungseintragService bewerbungseintragService;

    /**
     * Der eigentliche Test ist, dass der Context überhaupt hochkommt — bei einem fehlerhaften
     * ALTER TABLE würde er das nicht. Zusätzlich muss der Altbestand lesbar bleiben.
     */
    @Test
    void bestehendeDatenbankUeberstehtDieErweiterung() {
        List<BewerbungseintragResponse> alle = bewerbungseintragService.getAllBewerbungseintraege();

        assertEquals(1, alle.size());
        BewerbungseintragResponse alt = alle.getFirst();
        assertEquals("Altbestand", alt.notiz());
    }

    /** Alte Zeilen haben die neuen Felder nicht — das muss als sinnvoller Standard ankommen. */
    @Test
    void altbestandBekommtVernuenftigeStandardwerte() {
        BewerbungseintragResponse alt = bewerbungseintragService.getAllBewerbungseintraege().getFirst();

        assertFalse(alt.pdfBundleGewuenscht(), "fehlender Wert muss als 'nicht gewünscht' gelten");
        assertNull(alt.nachfassFristTage(), "ohne eigene Frist gilt der globale Standard");
        assertNull(alt.entwurfErinnerungIntervall(), "ohne eigenen Rhythmus gilt der globale Standard");
        assertNull(alt.letzteErinnerungAm(), "es wurde noch nie benachrichtigt");
        assertNull(alt.abgeschicktAm(), "ohne Verlaufseintrag gibt es kein Abschickdatum");
        assertNull(alt.absageAm());
        assertNull(alt.erfolgAm());
        assertNotNull(alt.createdAt());
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
                    // Aufraeumen im Temp-Verzeichnis darf den Test nicht scheitern lassen.
                }
            });
        }
    }
}
