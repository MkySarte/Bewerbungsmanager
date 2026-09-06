package de.mkysarte.bewerbungsmanager.session;

import de.mkysarte.bewerbungsmanager.common.AppPaths;
import de.mkysarte.bewerbungsmanager.common.InitialAccess;
import de.mkysarte.bewerbungsmanager.common.crypto.DbSession;
import de.mkysarte.bewerbungsmanager.common.crypto.InvalidPasswordException;
import de.mkysarte.bewerbungsmanager.common.crypto.VaultException;
import de.mkysarte.bewerbungsmanager.common.crypto.VaultService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Sitzungs- und Kontoverwaltung der Desktop-App.
 *
 * <p>Die eigentliche Anmeldung findet nicht mehr hier statt, sondern im
 * {@code LoginController} vor dem Spring-Start — die Datenbank muss ja entschlüsselt sein,
 * bevor es diesen Service überhaupt gibt. Übrig bleiben die Aufgaben, die eine geöffnete
 * Datenbank voraussetzen: Konto einrichten, Passwort ändern, Konto löschen.
 *
 * <p>Es gibt genau ein echtes Konto. Der Initial-/Demo-Zugang lebt in einer eigenen
 * Datenbankdatei und taucht hier nie als zweiter Benutzer auf.
 */
@Slf4j
@Service
public class DesktopSessionService {

    private static final int MIN_PASSWORD_LENGTH = 6;

    private final CurrentUserHolder currentUserHolder;
    private final MasterPasswordPdfService masterPasswordPdfService;
    private final DbSession dbSession;
    private final VaultService vaultService;

    public DesktopSessionService(
            CurrentUserHolder currentUserHolder,
            MasterPasswordPdfService masterPasswordPdfService,
            DbSession dbSession
    ) {
        this.currentUserHolder = currentUserHolder;
        this.masterPasswordPdfService = masterPasswordPdfService;
        this.dbSession = dbSession;
        this.vaultService = new VaultService(AppPaths.dataDirectory());
    }

    /** Läuft gerade die Demo (Musterdaten) statt des echten Kontos? */
    public boolean isCurrentSessionDemo() {
        return dbSession.demo();
    }

    /** Ist bereits ein echtes Konto eingerichtet? */
    public boolean isAccountConfigured() {
        return vaultService.exists();
    }

    public void logout() {
        log.info("Abmelden: {}", currentUserHolder.getUsername());
        currentUserHolder.clear();
    }

    // =====================================================================
    //  Konto einrichten
    // =====================================================================

    /**
     * Richtet das eine echte Konto ein.
     *
     * <p>Läuft aus der Demo heraus und legt eine <b>eigene, leere</b> Datenbank an: Die
     * Musterdaten bleiben in {@code demo.db} und wandern ausdrücklich nicht mit — ein frisches
     * Konto startet leer. Angelegt werden hier nur der Schlüssel-Tresor und die leere Datei;
     * Benutzerzeile und Referenzdaten entstehen beim nächsten Start in der neuen Datenbank.
     *
     * @return Zugangsdaten samt Masterpasswort-PDF, die der Nutzer sichern muss
     */
    public CreatedUserCredentials completeSetup(String username, String password) {
        String trimmed = username == null ? "" : username.trim();
        if (trimmed.isBlank()) {
            throw new IllegalArgumentException("Benutzername darf nicht leer sein.");
        }
        if (InitialAccess.isInitialUsername(trimmed)) {
            throw new IllegalArgumentException(
                    "\"" + trimmed + "\" ist für den Demo-Zugang reserviert. Bitte einen anderen Namen wählen.");
        }
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException(
                    "Passwort muss mindestens " + MIN_PASSWORD_LENGTH + " Zeichen haben.");
        }
        if (vaultService.exists()) {
            throw new IllegalArgumentException("Es ist bereits ein Konto eingerichtet.");
        }

        // Eine alte, verwaiste Datenbankdatei ohne Tresor wäre unlesbar - weg damit,
        // sonst scheitert der erste Start des neuen Kontos daran.
        deleteQuietly(AppPaths.realDatabase());

        String masterPassword = masterPasswordPdfService.generateMasterPassword();
        vaultService.create(trimmed, password, masterPassword);
        log.info("Konto eingerichtet: {}", trimmed);

        byte[] pdf = masterPasswordPdfService.createPdf(trimmed, masterPassword);
        return new CreatedUserCredentials(
                trimmed,
                masterPassword,
                pdf,
                "masterpasswort-" + trimmed + ".pdf"
        );
    }

    /**
     * Löscht das echte Konto vollständig — Datenbank und Schlüssel-Tresor.
     *
     * <p>Nur aus der Demo heraus erreichbar und der einzige Weg zu einem Neuanfang, wenn
     * Passwort und Masterpasswort verloren sind. Die Daten sind danach endgültig weg: ohne
     * den Tresor ist die verschlüsselte Datei nicht mehr zu öffnen.
     */
    public void deleteRealAccount() {
        if (!dbSession.demo()) {
            throw new IllegalStateException("Das eigene Konto kann nur aus der Demo heraus gelöscht werden.");
        }
        deleteQuietly(AppPaths.realDatabase());
        deleteQuietly(Path.of(AppPaths.realDatabase() + "-wal"));
        deleteQuietly(Path.of(AppPaths.realDatabase() + "-shm"));
        vaultService.delete();
        log.warn("Echtes Konto wurde gelöscht (Datenbank und Schlüssel-Tresor).");
    }

    // =====================================================================
    //  Passwort
    // =====================================================================

    /**
     * Ändert das Passwort des angemeldeten Kontos.
     *
     * <p>Geändert wird ausschließlich die Passwort-Verpackung im Tresor. Der
     * Datenbankschlüssel bleibt derselbe — die Datenbank wird <b>nicht</b> umgeschlüsselt,
     * und das Masterpasswort gilt unverändert weiter.
     */
    public void changePassword(String currentPassword, String newPassword) {
        if (dbSession.demo()) {
            throw new IllegalStateException("Im Demo-Zugang kann das Passwort nicht geändert werden.");
        }
        if (newPassword == null || newPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException(
                    "Neues Passwort muss mindestens " + MIN_PASSWORD_LENGTH + " Zeichen haben.");
        }

        byte[] databaseKey;
        try {
            databaseKey = vaultService.unlockWithPassword(currentPassword);
        } catch (InvalidPasswordException e) {
            throw new IllegalArgumentException("Aktuelles Passwort ist falsch");
        }

        vaultService.changePassword(databaseKey, newPassword);
        log.info("Passwort geändert für: {}", currentUserHolder.getUsername());
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            throw new VaultException("Datei konnte nicht gelöscht werden: " + path, e);
        }
    }

    public record CreatedUserCredentials(
            String username,
            String masterPassword,
            byte[] pdfBytes,
            String pdfFileName
    ) {
    }
}
