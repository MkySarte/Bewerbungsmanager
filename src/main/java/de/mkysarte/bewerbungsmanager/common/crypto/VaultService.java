package de.mkysarte.bewerbungsmanager.common.crypto;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Properties;

/**
 * Schlüssel-Tresor für die verschlüsselte Datenbank.
 *
 * <p>Die Datenbank wird nicht mit dem Passwort des Nutzers verschlüsselt, sondern mit einem
 * zufälligen 32-Byte-Schlüssel. Dieser Schlüssel liegt <b>zweifach verpackt</b> in
 * {@code keys.properties} — einmal mit dem Login-Passwort, einmal mit dem Masterpasswort.
 * Im Klartext steht er nirgends.
 *
 * <p>Der Vorteil dieser Indirektion: Ein Passwortwechsel schreibt nur die eine Verpackung neu.
 * Die Datenbank selbst muss <b>nicht</b> umgeschlüsselt werden — kein Warten, kein Risiko eines
 * halb fertigen Re-Keys, und das Masterpasswort bleibt davon unberührt gültig.
 *
 * <p>Diese Klasse ist bewusst <b>kein</b> Spring-Bean: sie läuft, bevor der Spring-Context
 * existiert, denn ohne den Schlüssel lässt sich die DataSource gar nicht erst aufbauen.
 *
 * <p>Verfahren: PBKDF2-HMAC-SHA256 zur Ableitung, AES-256-GCM zur Verpackung. Beides aus dem
 * JDK — keine zusätzliche Krypto-Abhängigkeit.
 */
public class VaultService {

    /** Der Dateiname liegt neben der Datenbank im Datenverzeichnis. */
    public static final String VAULT_FILENAME = "keys.properties";

    private static final int KEY_BYTES = 32;
    private static final int SALT_BYTES = 16;
    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final int PBKDF2_ITERATIONS = 210_000;

    private static final String KDF = "PBKDF2WithHmacSHA256";
    private static final String CIPHER = "AES/GCM/NoPadding";

    private static final String SLOT_PASSWORD = "password";
    private static final String SLOT_MASTER = "master";

    /**
     * Fester Salt für den Demo-Schlüssel. Der Demo-Zugang schützt nichts — sein Passwort ist
     * öffentlich bekannt und die Datenbank enthält nur Musterdaten. Er benutzt denselben
     * Code-Pfad wie das echte Konto, damit dieser Pfad im Alltag mitgetestet wird.
     */
    private static final byte[] DEMO_SALT = "bewerbungsmanager-demo-salt-v1".getBytes(StandardCharsets.UTF_8);

    private final SecureRandom secureRandom = new SecureRandom();
    private final Path vaultFile;

    public VaultService(Path dataDir) {
        this.vaultFile = dataDir.resolve(VAULT_FILENAME);
    }

    /** Existiert bereits ein echtes Konto? */
    public boolean exists() {
        return Files.isRegularFile(vaultFile);
    }

    /**
     * Legt den Tresor an und liefert den frisch erzeugten Datenbankschlüssel zurück.
     *
     * @throws VaultException wenn bereits ein Tresor existiert — ein zweites echtes Konto
     *                        darf es nie geben
     */
    public byte[] create(String username, String password, String masterPassword) {
        if (exists()) {
            throw new VaultException("Es ist bereits ein Konto eingerichtet.");
        }

        byte[] databaseKey = new byte[KEY_BYTES];
        secureRandom.nextBytes(databaseKey);

        Properties properties = new Properties();
        properties.setProperty("version", "1");
        properties.setProperty("username", username);
        writeSlot(properties, SLOT_PASSWORD, databaseKey, password);
        writeSlot(properties, SLOT_MASTER, databaseKey, masterPassword);
        store(properties);

        return databaseKey;
    }

    /**
     * Benutzername des eingerichteten Kontos.
     *
     * <p>Kein Geheimnis und deshalb unverschlüsselt abgelegt — so kann der Anmeldebildschirm
     * einen falschen Benutzernamen abweisen, bevor überhaupt eine Datenbank geöffnet wird.
     */
    public String username() {
        return required(load(), "username");
    }

    /** Öffnet den Tresor mit dem Login-Passwort. */
    public byte[] unlockWithPassword(String password) throws InvalidPasswordException {
        return unlock(SLOT_PASSWORD, password, "Passwort ist falsch.");
    }

    /** Öffnet den Tresor mit dem Masterpasswort aus der PDF. */
    public byte[] unlockWithMasterPassword(String masterPassword) throws InvalidPasswordException {
        return unlock(SLOT_MASTER, masterPassword, "Masterpasswort ist falsch.");
    }

    /**
     * Setzt ein neues Login-Passwort.
     *
     * <p>Nur die Passwort-Verpackung wird neu geschrieben; der Datenbankschlüssel und die
     * Masterpasswort-Verpackung bleiben unverändert. Die Datenbank wird dabei nicht angefasst.
     */
    public void changePassword(byte[] databaseKey, String newPassword) {
        Properties properties = load();
        writeSlot(properties, SLOT_PASSWORD, databaseKey, newPassword);
        store(properties);
    }

    /**
     * Löscht den Tresor. Ohne ihn ist eine vorhandene Datenbankdatei endgültig unlesbar —
     * nur zusammen mit dem Löschen der Datenbank selbst aufrufen.
     */
    public void delete() {
        try {
            Files.deleteIfExists(vaultFile);
        } catch (IOException e) {
            throw new VaultException("Schlüssel-Tresor konnte nicht gelöscht werden.", e);
        }
    }

    /**
     * Leitet den Demo-Schlüssel deterministisch aus dem festen Demo-Passwort ab.
     * Ohne Tresordatei — es gibt hier nichts zu schützen.
     */
    public static byte[] deriveDemoKey(String demoPassword) {
        return deriveKey(demoPassword, DEMO_SALT, PBKDF2_ITERATIONS);
    }

    /** Datenbankschlüssel als Hex-String, wie ihn die JDBC-URL erwartet. */
    public static String toHex(byte[] key) {
        StringBuilder sb = new StringBuilder(key.length * 2);
        for (byte b : key) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    // =====================================================================
    //  Intern
    // =====================================================================

    private byte[] unlock(String slot, String password, String wrongPasswordMessage)
            throws InvalidPasswordException {
        Properties properties = load();

        byte[] salt = decode(properties, slot + ".salt");
        byte[] iv = decode(properties, slot + ".iv");
        byte[] ciphertext = decode(properties, slot + ".ciphertext");
        int iterations = Integer.parseInt(required(properties, slot + ".iterations"));

        byte[] wrappingKey = deriveKey(password, salt, iterations);
        try {
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(wrappingKey, "AES"),
                    new GCMParameterSpec(GCM_TAG_BITS, iv));
            return cipher.doFinal(ciphertext);
        } catch (GeneralSecurityException e) {
            // Das GCM-Auth-Tag passt nicht — das Passwort war falsch, die Datei ist in Ordnung.
            throw new InvalidPasswordException(wrongPasswordMessage);
        }
    }

    private void writeSlot(Properties properties, String slot, byte[] databaseKey, String password) {
        byte[] salt = new byte[SALT_BYTES];
        secureRandom.nextBytes(salt);
        byte[] iv = new byte[GCM_IV_BYTES];
        secureRandom.nextBytes(iv);

        byte[] wrappingKey = deriveKey(password, salt, PBKDF2_ITERATIONS);
        byte[] ciphertext;
        try {
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(wrappingKey, "AES"),
                    new GCMParameterSpec(GCM_TAG_BITS, iv));
            ciphertext = cipher.doFinal(databaseKey);
        } catch (GeneralSecurityException e) {
            throw new VaultException("Schlüssel konnte nicht verpackt werden.", e);
        }

        Base64.Encoder encoder = Base64.getEncoder();
        properties.setProperty(slot + ".salt", encoder.encodeToString(salt));
        properties.setProperty(slot + ".iv", encoder.encodeToString(iv));
        properties.setProperty(slot + ".ciphertext", encoder.encodeToString(ciphertext));
        properties.setProperty(slot + ".iterations", String.valueOf(PBKDF2_ITERATIONS));
    }

    private static byte[] deriveKey(String password, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BYTES * 8);
            return SecretKeyFactory.getInstance(KDF).generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new VaultException("Schlüsselableitung fehlgeschlagen.", e);
        }
    }

    private Properties load() {
        if (!exists()) {
            throw new VaultException("Es ist noch kein Konto eingerichtet.");
        }
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(vaultFile)) {
            properties.load(in);
        } catch (IOException e) {
            throw new VaultException("Schlüssel-Tresor konnte nicht gelesen werden.", e);
        }
        return properties;
    }

    private void store(Properties properties) {
        try {
            Files.createDirectories(vaultFile.getParent());
            try (OutputStream out = Files.newOutputStream(vaultFile)) {
                properties.store(out, "Bewerbungsmanager - verpackte Datenbankschlüssel. Nicht bearbeiten.");
            }
        } catch (IOException e) {
            throw new VaultException("Schlüssel-Tresor konnte nicht geschrieben werden.", e);
        }
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new VaultException("Schlüssel-Tresor ist unvollständig (fehlt: " + key + ").");
        }
        return value;
    }

    private static byte[] decode(Properties properties, String key) {
        try {
            return Base64.getDecoder().decode(required(properties, key));
        } catch (IllegalArgumentException e) {
            throw new VaultException("Schlüssel-Tresor ist beschädigt (" + key + ").", e);
        }
    }
}
