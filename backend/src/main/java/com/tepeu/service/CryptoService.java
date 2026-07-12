package com.tepeu.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.EnumSet;
import java.util.Set;

/**
 * AES-256-GCM encryption for secrets at rest (Product-Spec §7.4).
 *
 * <p>Master key source (auto key file): on first run a 32-byte key is generated and
 * base64-encoded to {@code <user.home>/.tepeu/master.key} (owner-only perms where the
 * filesystem supports POSIX; on Windows it inherits the user-private home ACL). Override the
 * path via {@code tepeu.security.master-key-file}.
 *
 * <p>Encrypted values are self-contained: {@code "enc:v1:" + base64(iv || ciphertext||tag)}.
 * {@link #decrypt(String)} passes through any value that does not carry the prefix, so legacy
 * plaintext rows keep working until re-saved.
 */
@Service
public class CryptoService {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_BITS = 256;
    private static final int KEY_BYTES = KEY_BITS / 8;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final String PREFIX = "enc:v1:";

    private final SecretKey masterKey;
    private final SecureRandom random = new SecureRandom();

    public CryptoService(@Value("${tepeu.security.master-key-file}") String masterKeyFile) {
        this.masterKey = loadOrGenerate(Paths.get(masterKeyFile));
    }

    private SecretKey loadOrGenerate(Path path) {
        try {
            if (Files.exists(path)) {
                byte[] decoded = Base64.getDecoder().decode(Files.readString(path).trim());
                if (decoded.length != KEY_BYTES) {
                    throw new IllegalStateException(
                            "Master key file corrupt: expected " + KEY_BYTES
                                    + " bytes, got " + decoded.length + " (" + path + ")");
                }
                return new SecretKeySpec(decoded, ALGORITHM);
            }
            KeyGenerator keyGen = KeyGenerator.getInstance(ALGORITHM);
            keyGen.init(KEY_BITS, random);
            SecretKey generated = keyGen.generateKey();
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            Files.writeString(path, Base64.getEncoder().encodeToString(generated.getEncoded()));
            restrictPermissions(path);
            return generated;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize crypto master key from " + path, e);
        }
    }

    private void restrictPermissions(Path path) {
        // Best-effort owner-only perms (POSIX). No-op on Windows where the home dir ACL
        // already scopes access to the user.
        try {
            Set<PosixFilePermission> perms = EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE);
            Files.setPosixFilePermissions(path, perms);
        } catch (UnsupportedOperationException | java.io.IOException ignore) {
            // Non-POSIX filesystem or permission change not supported — rely on default ACL.
        }
    }

    /** Encrypt plaintext → "enc:v1:&lt;base64(iv|ct|tag)&gt;". null/empty returned as-is. */
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            return plaintext;
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, masterKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ct, 0, combined, iv.length, ct.length);
            return PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("Failed to encrypt secret", e);
        }
    }

    /** Decrypt a value produced by {@link #encrypt(String)}. Non-prefixed values pass through. */
    public String decrypt(String stored) {
        if (stored == null || stored.isEmpty() || !stored.startsWith(PREFIX)) {
            return stored;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            byte[] iv = new byte[IV_BYTES];
            byte[] ct = new byte[combined.length - IV_BYTES];
            System.arraycopy(combined, 0, iv, 0, IV_BYTES);
            System.arraycopy(combined, IV_BYTES, ct, 0, ct.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, masterKey, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to decrypt secret", e);
        }
    }

    /** Mask a secret for display: {@code first(3) + "••••" + last(4)}. null/empty → null; short → "••••". */
    public static String mask(String key) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        int len = key.length();
        if (len <= 8) {
            return "••••";
        }
        return key.substring(0, 3) + "••••" + key.substring(len - 4);
    }
}
