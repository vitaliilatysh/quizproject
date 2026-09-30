package ua.nure.latysh.quizzes.api.config;

import org.springframework.beans.factory.annotation.Autowired;
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
    private static final int DEFAULT_ITERATION_COUNT = 600_000;
    private static final int SALT_LENGTH = 16;
    private static final int KEY_LENGTH = 256;
    private final SecureRandom secureRandom;
    private final String algorithm;
    private final int encodingIterationCount;

    @Autowired
    public LegacyPasswordEncoder() {
        this(new SecureRandom(), "PBKDF2WithHmacSHA256", DEFAULT_ITERATION_COUNT);
    }

    LegacyPasswordEncoder(SecureRandom secureRandom, String algorithm, int encodingIterationCount) {
        this.secureRandom = secureRandom;
        this.algorithm = algorithm;
        this.encodingIterationCount = encodingIterationCount;
    }

    @Override
    public String encode(CharSequence rawPassword) {
        byte[] salt = new byte[SALT_LENGTH];
        secureRandom.nextBytes(salt);
        byte[] hash = derive(rawPassword, salt, encodingIterationCount);
        return PREFIX + "$" + encodingIterationCount + "$" +
                Base64.getUrlEncoder().withoutPadding().encodeToString(salt) + "$" +
                Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    }

    /**
     * Reports a stored value weaker than what {@link #encode} produces today, so
     * Spring Security re-encodes it after a successful login.
     *
     * <p>Two kinds of value qualify, and the second is the one this originally
     * missed.
     *
     * <p><strong>Not this format at all.</strong> The legacy schema stored
     * passwords in plain text — {@code VARCHAR(15)} could hold nothing else —
     * and V2 only widened the column, leaving every migrated row as it was.
     * Without this, {@link #matches} keeps accepting those values verbatim and
     * they stay in plain text forever, however often the account signs in.
     *
     * <p><strong>This format, but under-iterated.</strong> {@code matches}
     * deliberately derives with the iteration count written into the stored
     * value, so that a hash produced under an older, cheaper setting still
     * verifies. That is what makes lowering the count survivable — and it is
     * also what makes such a row permanent, because it carries the prefix and
     * the check above therefore called it current. A row at a thousand
     * iterations was verified at a thousand iterations for the rest of its life.
     * Comparing the stored count against the one in force closes that: the row
     * is accepted, and then immediately rewritten at today's cost.
     *
     * <p>An unreadable count is an upgrade too. {@code matches} will refuse the
     * value, so nothing reaches this method for it on a successful login, but
     * saying "current" about something this class cannot parse would be a claim
     * it has no basis for.
     */
    @Override
    public boolean upgradeEncoding(String encodedPassword) {
        if (encodedPassword == null) {
            return false;
        }
        if (!encodedPassword.startsWith(PREFIX + "$")) {
            return true;
        }
        return iterationCountOf(encodedPassword.split("\\$", -1)) < encodingIterationCount;
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
        // A stored value decides its own cost, which is the point — it is how a
        // hash written under a cheaper setting still verifies, and upgradeEncoding
        // is what stops that leniency being permanent. A count at or below zero
        // is not a cheaper setting but a broken row, and PBEKeySpec rejects it
        // with an exception that would read here as a wrong password.
        int encodedIterationCount = iterationCountOf(parts);
        if (encodedIterationCount <= 0) {
            return false;
        }
        try {
            byte[] salt = Base64.getUrlDecoder().decode(parts[2]);
            byte[] expected = Base64.getUrlDecoder().decode(parts[3]);
            return MessageDigest.isEqual(expected, derive(rawPassword, salt, encodedIterationCount));
        } catch (IllegalArgumentException _) {
            return false;
        }
    }

    /**
     * The cost a stored value was produced with, read from the split fields both
     * callers already have.
     *
     * <p>{@code -1} when the value does not have four fields or its cost is not
     * a number — weaker than any configured count, so it asks for an upgrade,
     * and not a cost, so {@code matches} refuses it.
     *
     * <p>Takes the split array rather than the string, and hands back a number
     * rather than the three fields. The first shape duplicated the split in both
     * callers and left the second copy of every check unreachable; the second
     * carried two arrays in a record, whose inherited equals compares references
     * rather than contents. Neither mattered here — nothing compares these — but
     * both are traps to leave lying around, and the gates said so in turn.
     */
    private static int iterationCountOf(String[] parts) {
        if (parts.length != 4) {
            return -1;
        }
        try {
            return Integer.parseInt(parts[1]);
        } catch (NumberFormatException _) {
            return -1;
        }
    }

    private byte[] derive(CharSequence password, byte[] salt, int iterationCount) {
        PBEKeySpec specification = new PBEKeySpec(
                password.toString().toCharArray(), salt, iterationCount, KEY_LENGTH);
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
