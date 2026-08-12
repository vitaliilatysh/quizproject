package ua.nure.latysh.quizzes.security;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

public class PasswordHasher {
    private static final String PREFIX = "pbkdf2-sha256";
    private static final int DEFAULT_ITERATIONS = 600_000;
    private static final int SALT_LENGTH = 16;
    private static final int KEY_LENGTH = 256;

    private final SecureRandom secureRandom;
    private final String algorithm;
    private final int iterations;

    public PasswordHasher() {
        this(new SecureRandom(), "PBKDF2WithHmacSHA256", DEFAULT_ITERATIONS);
    }

    PasswordHasher(SecureRandom secureRandom, String algorithm, int iterations) {
        this.secureRandom = secureRandom;
        this.algorithm = algorithm;
        this.iterations = iterations;
    }

    public String hash(String password) {
        byte[] salt = new byte[SALT_LENGTH];
        secureRandom.nextBytes(salt);
        byte[] hash = derive(password.toCharArray(), salt, iterations);
        return PREFIX + "$" + iterations + "$"
                + Base64.getUrlEncoder().withoutPadding().encodeToString(salt) + "$"
                + Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    }

    public boolean matches(String password, String encodedPassword) {
        if (password == null || !isEncoded(encodedPassword)) {
            return false;
        }
        String[] parts = encodedPassword.split("\\$", -1);
        if (parts.length != 4) {
            return false;
        }
        try {
            int encodedIterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getUrlDecoder().decode(parts[2]);
            byte[] expected = Base64.getUrlDecoder().decode(parts[3]);
            byte[] actual = derive(password.toCharArray(), salt, encodedIterations);
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public boolean matchesLegacy(String password, String storedPassword) {
        if (password == null || storedPassword == null) {
            return false;
        }
        return MessageDigest.isEqual(password.getBytes(StandardCharsets.UTF_8),
                storedPassword.getBytes(StandardCharsets.UTF_8));
    }

    public boolean isEncoded(String password) {
        return password != null && password.startsWith(PREFIX + "$");
    }

    private byte[] derive(char[] password, byte[] salt, int iterationCount) {
        PBEKeySpec specification = new PBEKeySpec(password, salt, iterationCount, KEY_LENGTH);
        try {
            return SecretKeyFactory.getInstance(algorithm).generateSecret(specification).getEncoded();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Password hashing is unavailable", exception);
        } finally {
            specification.clearPassword();
        }
    }
}
