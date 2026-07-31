package web.tosunsaeng.domain.comment.domain.policy;

import org.junit.jupiter.api.Test;
import web.tosunsaeng.domain.comment.api.support.AnonymousCookieFactory;
import web.tosunsaeng.domain.comment.config.AnonymousSessionProperties;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class AnonymousTokenManagerTest {

    private static final String TEST_SECRET =
            "test-only-anonymous-token-secret-at-least-32-bytes";

    @Test
    void createsCryptographicallySizedUrlSafeRawTokenAndHmacHash() throws Exception {
        AnonymousTokenManager manager = manager(new FixedSecureRandom((byte) 7), TEST_SECRET);

        AnonymousTokenManager.AnonymousToken token = manager.createToken();

        assertThat(token.rawToken()).hasSize(43).doesNotContain("=");
        assertThat(Base64.getUrlDecoder().decode(token.rawToken())).hasSize(32);
        assertThat(token.tokenHash())
                .isNotEqualTo(token.rawToken())
                .isEqualTo(expectedHash(token.rawToken(), TEST_SECRET));
    }

    @Test
    void sameRawTokenUsesSameHashAndDifferentSecretChangesHash() {
        String rawToken = manager(new FixedSecureRandom((byte) 1), TEST_SECRET)
                .createToken().rawToken();
        String first = manager(new FixedSecureRandom((byte) 2), TEST_SECRET).hash(rawToken);
        String second = manager(
                new FixedSecureRandom((byte) 3),
                "another-test-only-secret-that-is-at-least-32-bytes").hash(rawToken);

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void validatesOnlyExpectedBase64UrlTokenShape() {
        AnonymousTokenManager manager = manager(new FixedSecureRandom((byte) 1), TEST_SECRET);
        String valid = manager.createToken().rawToken();

        assertThat(manager.isValidRawToken(valid)).isTrue();
        assertThat(manager.isValidRawToken(null)).isFalse();
        assertThat(manager.isValidRawToken("")).isFalse();
        assertThat(manager.isValidRawToken("too-short")).isFalse();
        assertThat(manager.isValidRawToken("!".repeat(43))).isFalse();
    }

    @Test
    void refusesToHashInvalidCookieWithoutEchoingIt() {
        AnonymousTokenManager manager = manager(new FixedSecureRandom((byte) 1), TEST_SECRET);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> manager.hash("raw-invalid-cookie-value"))
                .withMessageNotContaining("raw-invalid-cookie-value");
    }

    @Test
    void rejectsMissingOrShortHmacSecretAtInitialization() {
        assertThatIllegalStateException().isThrownBy(() -> properties("", true, 180));
        assertThatIllegalStateException().isThrownBy(() -> properties("short", true, 180));
    }

    @Test
    void rejectsNonPositiveCookieMaxAgeDays() {
        assertThatIllegalStateException().isThrownBy(() -> properties(TEST_SECRET, true, 0));
    }

    @Test
    void rejectsCookieMaxAgeThatCannotBeConvertedWithoutOverflow() {
        assertThatIllegalStateException().isThrownBy(() ->
                properties(TEST_SECRET, true, Long.MAX_VALUE));
    }

    @Test
    void cookieFactoryUsesApprovedSecurityAttributesAndConfiguredMaxAge() {
        AnonymousSessionProperties properties = properties(TEST_SECRET, false, 30);
        String cookie = new AnonymousCookieFactory(properties).create("token-value").toString();

        assertThat(cookie)
                .contains("anon_session=token-value")
                .contains("Path=/")
                .contains("Max-Age=2592000")
                .contains("HttpOnly")
                .contains("SameSite=Lax")
                .doesNotContain("Secure");

        properties.setCookieSecure(true);
        assertThat(new AnonymousCookieFactory(properties).create("token-value").toString())
                .contains("Secure");
    }

    private AnonymousTokenManager manager(SecureRandom random, String secret) {
        return new AnonymousTokenManager(random, properties(secret, true, 180));
    }

    private AnonymousSessionProperties properties(
            String secret,
            boolean secure,
            long maxAgeDays) {
        AnonymousSessionProperties properties = new AnonymousSessionProperties();
        properties.setTokenSecret(secret);
        properties.setCookieSecure(secure);
        properties.setCookieMaxAgeDays(maxAgeDays);
        properties.afterPropertiesSet();
        return properties;
    }

    private String expectedHash(String rawToken, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal(rawToken.getBytes(StandardCharsets.US_ASCII)));
    }

    private static class FixedSecureRandom extends SecureRandom {
        private final byte value;

        FixedSecureRandom(byte value) {
            this.value = value;
        }

        @Override
        public void nextBytes(byte[] bytes) {
            java.util.Arrays.fill(bytes, value);
        }
    }
}
