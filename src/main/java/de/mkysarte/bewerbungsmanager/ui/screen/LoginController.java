package de.mkysarte.bewerbungsmanager.ui.screen;

import de.mkysarte.bewerbungsmanager.common.AppPaths;
import de.mkysarte.bewerbungsmanager.common.InitialAccess;
import de.mkysarte.bewerbungsmanager.common.crypto.DbSession;
import de.mkysarte.bewerbungsmanager.common.crypto.InvalidPasswordException;
import de.mkysarte.bewerbungsmanager.common.crypto.VaultException;
import de.mkysarte.bewerbungsmanager.common.crypto.VaultService;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;

import java.util.function.Consumer;

/**
 * Anmeldebildschirm — läuft <b>vor</b> dem Spring-Context.
 *
 * <p>Bewusst kein Spring-Bean: Ohne entschlüsselte Datenbank gibt es weder Repositories noch
 * einen {@code PasswordEncoder}. Die Prüfung des Passworts geschieht deshalb nicht mehr gegen
 * die {@code users}-Tabelle — die läge ja selbst in der verschlüsselten Datei —, sondern durch
 * das Öffnen des Schlüssel-Tresors: <b>Wer den Tresor aufbekommt, kennt das Passwort.</b> Der
 * BCrypt-Hash in der Datenbank bleibt als zusätzliche Prüfung nach dem Öffnen bestehen.
 *
 * <p>Die Anmeldung entscheidet, welche der beiden Datenbanken geöffnet wird:
 * der Initial-Zugang öffnet {@code demo.db}, das eingerichtete Konto {@code data.db}.
 */
public class LoginController {

    private final VaultService vaultService;
    private final Consumer<DbSession> onUnlocked;

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label errorLabel;
    @FXML private Button loginButton;
    @FXML private Label initialAccessLabel;

    @FXML private StackPane forgotOverlay;
    @FXML private TextField masterPasswordField;
    @FXML private PasswordField newPasswordField;
    @FXML private Label forgotErrorLabel;
    @FXML private Button forgotSubmitButton;

    public LoginController(VaultService vaultService, Consumer<DbSession> onUnlocked) {
        this.vaultService = vaultService;
        this.onUnlocked = onUnlocked;
    }

    @FXML
    public void initialize() {
        initialAccessLabel.setText(InitialAccess.USERNAME + "  /  " + InitialAccess.PASSWORD);
        usernameField.requestFocus();
    }

    // =====================================================================
    //  Anmelden
    // =====================================================================

    @FXML
    public void handleLogin() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();

        if (username.isBlank() || password.isBlank()) {
            showError("Bitte Benutzername und Passwort eingeben.");
            return;
        }

        setFormDisabled(true);
        hideError();
        try {
            DbSession session = InitialAccess.isInitialUsername(username)
                    ? openDemoSession(password)
                    : openRealSession(username, password);
            if (session != null) {
                onUnlocked.accept(session);
            }
        } finally {
            setFormDisabled(false);
        }
    }

    /**
     * Demo-Sitzung: feste Zugangsdaten, feste Datei, abgeleiteter Schlüssel.
     * Schützt nichts und soll das auch nicht — in {@code demo.db} stehen nur Musterdaten.
     */
    private DbSession openDemoSession(String password) {
        if (!InitialAccess.PASSWORD.equals(password)) {
            failLogin("Passwort des Demo-Zugangs ist falsch.");
            return null;
        }
        String key = VaultService.toHex(VaultService.deriveDemoKey(InitialAccess.PASSWORD));
        return new DbSession(true, InitialAccess.USERNAME, AppPaths.demoDatabase(), key);
    }

    /** Echte Sitzung: Tresor mit dem Passwort öffnen, damit {@code data.db} lesbar wird. */
    private DbSession openRealSession(String username, String password) {
        if (!vaultService.exists()) {
            showError("Es ist noch kein eigenes Konto eingerichtet. "
                    + "Melde dich mit dem Demo-Zugang an und richte dort dein Konto ein.");
            return null;
        }
        if (!vaultService.username().equalsIgnoreCase(username)) {
            failLogin("Benutzername oder Passwort falsch.");
            return null;
        }
        try {
            byte[] databaseKey = vaultService.unlockWithPassword(password);
            return new DbSession(false, vaultService.username(),
                    AppPaths.realDatabase(), VaultService.toHex(databaseKey));
        } catch (InvalidPasswordException e) {
            failLogin("Benutzername oder Passwort falsch.");
            return null;
        } catch (VaultException e) {
            showError(e.getMessage());
            return null;
        }
    }

    private void failLogin(String message) {
        showError(message);
        passwordField.clear();
        passwordField.requestFocus();
    }

    // =====================================================================
    //  Passwort vergessen (Masterpasswort aus der PDF)
    // =====================================================================

    @FXML
    public void handleForgotPassword() {
        masterPasswordField.clear();
        newPasswordField.clear();
        hideForgotError();
        forgotOverlay.setVisible(true);
        forgotOverlay.setManaged(true);
        masterPasswordField.requestFocus();
    }

    @FXML
    public void handleCloseForgotPassword() {
        forgotOverlay.setVisible(false);
        forgotOverlay.setManaged(false);
    }

    @FXML
    public void handleSubmitForgotPassword() {
        hideForgotError();
        forgotSubmitButton.setDisable(true);
        try {
            if (!vaultService.exists()) {
                showForgotError("Es ist noch kein eigenes Konto eingerichtet.");
                return;
            }
            String masterPassword = masterPasswordField.getText().trim();
            String newPassword = newPasswordField.getText();
            if (masterPassword.isBlank()) {
                showForgotError("Bitte das Masterpasswort eingeben.");
                return;
            }
            if (newPassword == null || newPassword.length() < 6) {
                showForgotError("Das neue Passwort muss mindestens 6 Zeichen haben.");
                return;
            }

            // Der Schlüssel selbst bleibt unverändert — nur seine Verpackung wird
            // mit dem neuen Passwort neu geschrieben. Die Daten bleiben erhalten.
            byte[] databaseKey = vaultService.unlockWithMasterPassword(masterPassword);
            vaultService.changePassword(databaseKey, newPassword);

            handleCloseForgotPassword();
            showError("Passwort wurde geändert. Bitte neu anmelden.");
        } catch (InvalidPasswordException e) {
            showForgotError(e.getMessage());
        } catch (VaultException e) {
            showForgotError(e.getMessage());
        } finally {
            forgotSubmitButton.setDisable(false);
        }
    }

    // =====================================================================
    //  Anzeige
    // =====================================================================

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void hideError() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }

    private void showForgotError(String message) {
        forgotErrorLabel.setText(message);
        forgotErrorLabel.setVisible(true);
        forgotErrorLabel.setManaged(true);
    }

    private void hideForgotError() {
        forgotErrorLabel.setVisible(false);
        forgotErrorLabel.setManaged(false);
    }

    private void setFormDisabled(boolean disabled) {
        usernameField.setDisable(disabled);
        passwordField.setDisable(disabled);
        loginButton.setDisable(disabled);
    }
}
