package com.tepeu.os.session.memory;

import com.tepeu.os.session.ContentStore;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** 内存 CAS：digest = sha256 hex；进程内，非发行默认。 */
public final class InMemoryContentStore implements ContentStore {

    private final Map<String, byte[]> blobs = new ConcurrentHashMap<>();

    @Override
    public String put(byte[] content) {
        Objects.requireNonNull(content, "content");
        String digest = sha256Hex(content);
        blobs.putIfAbsent(digest, Arrays.copyOf(content, content.length));
        return digest;
    }

    @Override
    public Optional<byte[]> get(String digest) {
        if (digest == null) {
            return Optional.empty();
        }
        byte[] found = blobs.get(digest);
        return found == null ? Optional.empty() : Optional.of(Arrays.copyOf(found, found.length));
    }

    void putKnown(String digest, byte[] content) {
        blobs.put(digest, Arrays.copyOf(content, content.length));
    }

    Map<String, byte[]> snapshot() {
        return Map.copyOf(blobs);
    }

    static String sha256Hex(byte[] content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 required", e);
        }
    }
}
