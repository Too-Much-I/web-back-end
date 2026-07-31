package web.tosunsaeng.global.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeConfigurationSecurityTest {

    @Test
    void mainConfigurationHasNoJwtOrSentryFallbackAndSafeSwitchDefaults()
            throws IOException {
        PropertySource<?> properties = load("application.yml");

        assertThat(properties.getProperty("jwt.secret"))
                .isEqualTo("${JWT_SECRET_KEY:}");
        assertThat(properties.getProperty("sentry.dsn"))
                .isEqualTo("${SENTRY_DSN:}");
        assertThat(properties.getProperty("sentry.send-default-pii"))
                .isEqualTo(false);
        assertThat(properties.getProperty("newsletter.delivery.sending-enabled"))
                .isEqualTo("${NEWSLETTER_SENDING_ENABLED:false}");
        assertThat(properties.getProperty("newsletter.delivery.test-sending-enabled"))
                .isEqualTo("${NEWSLETTER_TEST_SENDING_ENABLED:false}");
        assertThat(properties.getProperty("internal.api.enabled"))
                .isEqualTo("${INTERNAL_API_ENABLED:false}");
    }

    @Test
    void testProfileUsesExplicitNonProductionJwtAndDisablesSentryPii()
            throws IOException {
        PropertySource<?> properties = load("application-test.yml");

        assertThat(properties.getProperty("jwt.secret"))
                .isEqualTo("test-only-jwt-secret-at-least-32-bytes");
        assertThat(properties.getProperty("sentry.dsn")).isEqualTo("");
        assertThat(properties.getProperty("sentry.enabled")).isEqualTo(false);
        assertThat(properties.getProperty("sentry.send-default-pii"))
                .isEqualTo(false);
    }

    private PropertySource<?> load(String resource) throws IOException {
        return new YamlPropertySourceLoader()
                .load(resource, new ClassPathResource(resource))
                .getFirst();
    }
}
