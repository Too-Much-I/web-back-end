package web.tosunsaeng.global.config.security;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderSecurityTest {

    @Test
    void missingBlankAndShortSecretsFailFastWithoutEchoingSecret() {
        assertInvalidSecret(null);
        assertInvalidSecret("");
        assertInvalidSecret("   ");
        assertInvalidSecret("x".repeat(31));
        assertInvalidSecret("가".repeat(10));
    }

    @Test
    void utf8SecretAtLeastThirtyTwoBytesCanSignAndValidateToken() {
        String secret = "가".repeat(11);
        assertThat(secret.getBytes(StandardCharsets.UTF_8)).hasSize(33);
        JwtTokenProvider provider = provider(secret);

        provider.init();
        String token = provider.createToken("security-test-user");

        assertThat(provider.validateToken(token)).isTrue();
        assertThat(provider.getAuthentication(token).getName())
                .isEqualTo("security-test-user");
    }

    private void assertInvalidSecret(String secret) {
        JwtTokenProvider provider = provider(secret);

        assertThatThrownBy(provider::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 byte")
                .satisfies(exception -> {
                    if (secret != null && !secret.isEmpty()) {
                        assertThat(exception.getMessage()).doesNotContain(secret);
                    }
                });
    }

    private JwtTokenProvider provider(String secret) {
        JwtTokenProvider provider = new JwtTokenProvider();
        ReflectionTestUtils.setField(provider, "secretKeyString", secret);
        return provider;
    }
}
