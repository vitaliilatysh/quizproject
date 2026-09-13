package ua.nure.latysh.quizzes.api.auth;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class RefreshTokenHasher {
    private final String algorithm;

    @Autowired
    public RefreshTokenHasher() {
        this("SHA-256");
    }

    RefreshTokenHasher(String algorithm) {
        this.algorithm = algorithm;
    }

    public String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance(algorithm)
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Refresh token hashing is unavailable", exception);
        }
    }

    public boolean matches(String token, String expectedHash) {
        return MessageDigest.isEqual(
                hash(token).getBytes(StandardCharsets.US_ASCII),
                expectedHash.getBytes(StandardCharsets.US_ASCII));
    }
}
