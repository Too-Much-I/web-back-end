package web.tosunsaeng.global.config.security;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class InternalApiKeyVerifierTest {

    private static final String KEY =
            "test-only-internal-api-key-32-bytes-minimum";

    @Test
    void comparesFixedLengthSha256DigestsAndAcceptsOnlyTheConfiguredKey()
            throws ReflectiveOperationException {
        InternalApiProperties properties = enabledProperties();
        InternalApiKeyVerifier verifier = new InternalApiKeyVerifier(properties);

        assertThat(verifier.matches(KEY)).isTrue();
        assertThat(verifier.matches(KEY + "-wrong")).isFalse();
        assertThat(verifier.matches("short")).isFalse();
        assertThat(verifier.matches(null)).isFalse();

        Field field = InternalApiKeyVerifier.class.getDeclaredField(
                "configuredDigest");
        field.setAccessible(true);
        byte[] digest = (byte[]) field.get(verifier);
        assertThat(digest).hasSize(32);
        assertThat(digest).isNotEqualTo(KEY.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void rejectsAbnormallyLongHeadersWithoutChangingConfiguration() {
        InternalApiKeyVerifier verifier = new InternalApiKeyVerifier(
                enabledProperties());

        assertThat(verifier.matches("x".repeat(
                InternalApiProperties.MAX_KEY_BYTES + 1))).isFalse();
        assertThat(verifier.matches(KEY)).isTrue();
    }

    @Test
    void disabledVerifierNeverAuthenticatesEvenWithTheConfiguredText() {
        InternalApiProperties properties = new InternalApiProperties();
        properties.setEnabled(false);
        properties.setKey(KEY);
        properties.afterPropertiesSet();

        InternalApiKeyVerifier verifier = new InternalApiKeyVerifier(properties);

        assertThat(verifier.isEnabled()).isFalse();
        assertThat(verifier.matches(KEY)).isFalse();
    }

    private InternalApiProperties enabledProperties() {
        InternalApiProperties properties = new InternalApiProperties();
        properties.setEnabled(true);
        properties.setKey(KEY);
        properties.afterPropertiesSet();
        return properties;
    }
}
