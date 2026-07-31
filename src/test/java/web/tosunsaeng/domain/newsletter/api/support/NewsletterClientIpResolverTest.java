package web.tosunsaeng.domain.newsletter.api.support;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NewsletterClientIpResolverTest {

    private final NewsletterClientIpResolver resolver =
            new NewsletterClientIpResolver();

    @Test
    void usesRemoteAddrAndIgnoresUntrustedForwardedHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.7");
        request.addHeader("X-Forwarded-For", "203.0.113.10, 10.0.0.1");

        assertThat(resolver.resolve(request)).isEqualTo("10.0.0.7");
    }

    @Test
    void rejectsMissingRemoteAddress() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(" ");

        assertThatThrownBy(() -> resolver.resolve(request))
                .isInstanceOf(IllegalStateException.class);
    }
}
