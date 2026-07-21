package server.auth;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Salted SHA-256 hashing. Not meant to compete with bcrypt/argon2 in a
 * production system, but it means passwords are never stored (or logged)
 * in plaintext, which is the property that matters for this assignment.
 */
public final class PasswordHasher {
    private final SecureRandom random = new SecureRandom();

    public String generateSalt() {
        byte[] saltBytes = new byte[16];
        random.nextBytes(saltBytes);
        return Base64.getEncoder().encodeToString(saltBytes);
    }

    public String hash(String password, String salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(Base64.getDecoder().decode(salt));
            byte[] hashed = digest.digest(password.getBytes("UTF-8"));
            return Base64.getEncoder().encodeToString(hashed);
        } catch (NoSuchAlgorithmException | java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException("Unable to hash password", e);
        }
    }

    public boolean matches(String rawPassword, String salt, String expectedHash) {
        return hash(rawPassword, salt).equals(expectedHash);
    }
}
