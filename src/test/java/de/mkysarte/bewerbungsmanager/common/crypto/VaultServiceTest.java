package de.mkysarte.bewerbungsmanager.common.crypto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VaultServiceTest {

    private static final String USERNAME = "testnutzer";
    private static final String PASSWORD = "geheim123";
    private static final String MASTER_PASSWORD = "ABCDE-FGHIJ-KLMNO-PQRST";

    @TempDir
    Path dataDir;

    private VaultService vaultService;

    @BeforeEach
    void setUp() {
        vaultService = new VaultService(dataDir);
    }

    @Test
    void existsShouldBeFalseBeforeSetup() {
        assertFalse(vaultService.exists());
    }

    @Test
    void createShouldProduceA256BitKeyAndPersistTheVault() {
        byte[] key = vaultService.create(USERNAME, PASSWORD, MASTER_PASSWORD);

        assertNotNull(key);
        assertEquals(32, key.length);
        assertTrue(vaultService.exists());
        assertEquals(USERNAME, vaultService.username());
    }

    @Test
    void unlockWithPasswordShouldReturnTheSameKey() throws Exception {
        byte[] created = vaultService.create(USERNAME, PASSWORD, MASTER_PASSWORD);

        byte[] unlocked = vaultService.unlockWithPassword(PASSWORD);

        assertArrayEquals(created, unlocked);
    }

    @Test
    void unlockWithMasterPasswordShouldReturnTheSameKey() throws Exception {
        byte[] created = vaultService.create(USERNAME, PASSWORD, MASTER_PASSWORD);

        byte[] unlocked = vaultService.unlockWithMasterPassword(MASTER_PASSWORD);

        assertArrayEquals(created, unlocked);
    }

    @Test
    void wrongPasswordShouldBeRejected() {
        vaultService.create(USERNAME, PASSWORD, MASTER_PASSWORD);

        assertThrows(InvalidPasswordException.class, () -> vaultService.unlockWithPassword("falsch"));
    }

    @Test
    void wrongMasterPasswordShouldBeRejected() {
        vaultService.create(USERNAME, PASSWORD, MASTER_PASSWORD);

        assertThrows(InvalidPasswordException.class, () -> vaultService.unlockWithMasterPassword("falsch"));
    }

    @Test
    void masterPasswordShouldNotWorkAsLoginPassword() {
        vaultService.create(USERNAME, PASSWORD, MASTER_PASSWORD);

        // Die beiden Verpackungen sind getrennt - keine darf die andere öffnen.
        assertThrows(InvalidPasswordException.class, () -> vaultService.unlockWithPassword(MASTER_PASSWORD));
        assertThrows(InvalidPasswordException.class, () -> vaultService.unlockWithMasterPassword(PASSWORD));
    }

    /**
     * Der Kern des Entwurfs: Ein Passwortwechsel verpackt denselben Schlüssel neu,
     * statt die Datenbank umzuschlüsseln. Bliebe der Schlüssel nicht gleich, wären
     * nach jedem Passwortwechsel alle Daten unlesbar.
     */
    @Test
    void changePasswordShouldKeepTheDatabaseKeyUnchanged() throws Exception {
        byte[] original = vaultService.create(USERNAME, PASSWORD, MASTER_PASSWORD);

        vaultService.changePassword(original, "neuesPasswort456");

        assertArrayEquals(original, vaultService.unlockWithPassword("neuesPasswort456"));
        assertThrows(InvalidPasswordException.class, () -> vaultService.unlockWithPassword(PASSWORD));
    }

    /** Das Masterpasswort muss einen Passwortwechsel unbeschadet überstehen. */
    @Test
    void changePasswordShouldLeaveTheMasterPasswordValid() throws Exception {
        byte[] original = vaultService.create(USERNAME, PASSWORD, MASTER_PASSWORD);

        vaultService.changePassword(original, "neuesPasswort456");

        assertArrayEquals(original, vaultService.unlockWithMasterPassword(MASTER_PASSWORD));
    }

    @Test
    void createShouldRefuseASecondAccount() {
        vaultService.create(USERNAME, PASSWORD, MASTER_PASSWORD);

        assertThrows(VaultException.class,
                () -> vaultService.create("zweiter", "anderes123", MASTER_PASSWORD));
    }

    @Test
    void unlockingWithoutAVaultShouldFailClearly() {
        assertThrows(VaultException.class, () -> vaultService.unlockWithPassword(PASSWORD));
    }

    @Test
    void deleteShouldRemoveTheVault() {
        vaultService.create(USERNAME, PASSWORD, MASTER_PASSWORD);

        vaultService.delete();

        assertFalse(vaultService.exists());
    }

    @Test
    void twoVaultsShouldNeverShareAKey() {
        byte[] first = vaultService.create(USERNAME, PASSWORD, MASTER_PASSWORD);
        vaultService.delete();
        byte[] second = vaultService.create(USERNAME, PASSWORD, MASTER_PASSWORD);

        // Gleiches Passwort, trotzdem anderer Schlüssel - er ist zufaellig, nicht abgeleitet.
        assertEquals(32, second.length);
        assertFalse(java.util.Arrays.equals(first, second));
    }

    @Test
    void demoKeyShouldBeDeterministicAndDistinct() {
        byte[] once = VaultService.deriveDemoKey("changeme");
        byte[] twice = VaultService.deriveDemoKey("changeme");
        byte[] other = VaultService.deriveDemoKey("anderes");

        // Deterministisch: die Demo-Datenbank muss bei jedem Start wieder aufgehen.
        assertArrayEquals(once, twice);
        assertEquals(32, once.length);
        assertFalse(java.util.Arrays.equals(once, other));
    }

    @Test
    void toHexShouldProduceLowercasePairs() {
        assertEquals("00ff10", VaultService.toHex(new byte[]{0x00, (byte) 0xFF, 0x10}));
    }
}
