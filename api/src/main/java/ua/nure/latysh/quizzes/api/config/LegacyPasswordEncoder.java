package ua.nure.latysh.quizzes.api.config;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class LegacyPasswordEncoder implements PasswordEncoder {
    private static final String PREFIX = "pbkdf2-sha256";
    private static final int ITERATIONS = 600_000;
    private static final int SALT_LENGTH = 16;
    private static final int KEY_LENGTH = 256;
    private final SecureRandom secureRandom;
    private final String algorithm;
    private final int iterations;

    public LegacyPasswordEncoder() {
        this(new SecureRandom(), "PBKDF2WithHmacSHA256", ITERATIONS);
    }

    LegacyPasswordEncoder(SecureRandom secureRandom, String algorithm, int iterations) {
        this.secureRandom = secureRandom;
        this.algorithm = algorithm;
        this.iterations = iterations;
    }

    @Override
    public String encode(CharSequence rawPassword) {
        byte[] salt = new byte[SALT_LENGTH];
        secureRandom.nextBytes(salt);
        byte[] hash = derive(rawPassword, salt, iterations);
        return PREFIX + "$" + iterations + "$" +
                Base64.getUrlEncoder().withoutPadding().encodeToString(salt) + "$" +
                Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    }

    /**
     * Reports a stored value that is not in this encoder's format, so Spring
     * Security re-encodes it after a successful login.
     *
     * <p>The legacy schema stored passwords in plain text — {@code VARCHAR(15)}
     * could hold nothing else — and V2 only widened the column, leaving every
     * migrated row as it was. Without this, {@link #matches} keeps accepting
     * those values verbatim and they stay in plain text forever, however often
     * the account signs in. Upgrading on login drains them as people return.
     */
    @Override
    public boolean upgradeEncoding(String encodedPassword) {
        return encodedPassword != null && !encodedPassword.startsWith(PREFIX + "$");
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null) {
            return false;
        }
        if (!encodedPassword.startsWith(PREFIX + "$")) {
            return MessageDigest.isEqual(rawPassword.toString().getBytes(StandardCharsets.UTF_8),
                    encodedPassword.getBytes(StandardCharsets.UTF_8));
        }
        String[] parts = encodedPassword.split("\\$", -1);
        if (parts.length != 4) {
            return false;
        }
        try {
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getUrlDecoder().decode(parts[2]);
            byte[] expected = Base64.getUrlDecoder().decode(parts[3]);
            return MessageDigest.isEqual(expected, derive(rawPassword, salt, iterations));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private byte[] derive(CharSequence password, byte[] salt, int iterations) {
        PBEKeySpec specification = new PBEKeySpec(password.toString().toCharArray(), salt, iterations, KEY_LENGTH);
        try {
            return SecretKeyFactory.getInstance(algorithm)
                    .generateSecret(specification).getEncoded();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Password hashing is unavailable", exception);
        } finally {
            specification.clearPassword();
        }
    }
}
