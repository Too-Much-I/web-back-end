package web.tosunsaeng.global.config.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

public final class InternalApiKeyVerifier {

    private static final byte[] INVALID_CANDIDATE =
            "invalid-internal-api-key-candidate".getBytes(StandardCharsets.UTF_8);

    private final InternalApiProperties properties;
    private final byte[] configuredDigest;

    public InternalApiKeyVerifier(InternalApiProperties properties) {
        this.properties = properties;
        byte[] configuredBytes = properties.isEnabled()
                ? properties.getKey().getBytes(StandardCharsets.UTF_8)
                : INVALID_CANDIDATE.clone();
        this.configuredDigest = sha256(configuredBytes);
        Arrays.fill(configuredBytes, (byte) 0);
        properties.setKey("");
    }

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    public boolean matches(String candidate) {
        byte[] candidateBytes = candidate == null
                ? new byte[0]
                : candidate.getBytes(StandardCharsets.UTF_8);
        boolean acceptable = candidate != null
                && !candidate.isBlank()
                && candidateBytes.length <= InternalApiProperties.MAX_KEY_BYTES
                && !containsLineBreak(candidate);
        byte[] candidateDigest = sha256(acceptable ? candidateBytes : INVALID_CANDIDATE);
        boolean equal = MessageDigest.isEqual(configuredDigest, candidateDigest);
        Arrays.fill(candidateBytes, (byte) 0);
        Arrays.fill(candidateDigest, (byte) 0);
        return isEnabled() & acceptable & equal;
    }

    private boolean containsLineBreak(String value) {
        return value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0;
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("API Key 검증 알고리즘을 초기화할 수 없습니다.");
        }
    }
}
