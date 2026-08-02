package com.tepeu.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AES-GCM 加解密往返与明文透传。
 */
class CryptoServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void encryptDecrypt_roundTrip() {
        Path keyFile = tempDir.resolve("master.key");
        CryptoService crypto = new CryptoService(keyFile.toString());

        String plain = "sk-test-secret-key-12345";
        String enc = crypto.encrypt(plain);
        assertNotNull(enc);
        assertTrue(enc.startsWith("enc:v1:"));
        assertNotEquals(plain, enc);
        assertEquals(plain, crypto.decrypt(enc));
    }

    @Test
    void decrypt_passthroughPlaintext() {
        Path keyFile = tempDir.resolve("master.key");
        CryptoService crypto = new CryptoService(keyFile.toString());
        assertEquals("legacy-plain", crypto.decrypt("legacy-plain"));
        assertNull(crypto.decrypt(null));
        assertEquals("", crypto.decrypt(""));
    }

    @Test
    void mask_shortAndLong() {
        assertEquals("••••", CryptoService.mask("short"));
        assertEquals("abc••••wxyz", CryptoService.mask("abcdefghwxyz"));
        assertNull(CryptoService.mask(null));
    }

    @Test
    void sameKeyFile_reloadsSameKey() {
        Path keyFile = tempDir.resolve("shared.key");
        CryptoService a = new CryptoService(keyFile.toString());
        String enc = a.encrypt("same-key-value");
        CryptoService b = new CryptoService(keyFile.toString());
        assertEquals("same-key-value", b.decrypt(enc));
    }
}
