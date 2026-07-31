package web.tosunsaeng.global.config.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InternalApiPropertiesTest {

    @Test
    void defaultsToDisabledAndDoesNotRequireAKey() {
        InternalApiProperties properties = new InternalApiProperties();

        properties.afterPropertiesSet();

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getKey()).isEmpty();
    }

    @Test
    void enabledModeRequiresNonBlankUtf8KeyOfAtLeastThirtyTwoBytes() {
        InternalApiProperties missing = enabledWithKey("");
        InternalApiProperties blank = enabledWithKey("   ");
        InternalApiProperties shortUtf8 = enabledWithKey("가".repeat(10));

        assertInvalid(missing);
        assertInvalid(blank);
        assertInvalid(shortUtf8);

        InternalApiProperties validUtf8 = enabledWithKey("가".repeat(11));
        validUtf8.afterPropertiesSet();
        assertThat(validUtf8.isEnabled()).isTrue();
    }

    @Test
    void enabledModeRejectsLineBreaksAndAbnormallyLongConfiguration() {
        assertInvalid(enabledWithKey("a".repeat(32) + "\r\n"));
        assertInvalid(enabledWithKey("a".repeat(
                InternalApiProperties.MAX_KEY_BYTES + 1)));
    }

    private InternalApiProperties enabledWithKey(String key) {
        InternalApiProperties properties = new InternalApiProperties();
        properties.setEnabled(true);
        properties.setKey(key);
        return properties;
    }

    private void assertInvalid(InternalApiProperties properties) {
        var assertion = assertThatThrownBy(properties::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("INTERNAL_API_KEY 설정이 올바르지 않습니다.");
        if (properties.getKey() != null && !properties.getKey().isEmpty()) {
            assertion.hasMessageNotContaining(properties.getKey());
        }
    }
}
