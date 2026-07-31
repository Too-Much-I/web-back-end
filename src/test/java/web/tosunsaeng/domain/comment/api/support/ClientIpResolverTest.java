package web.tosunsaeng.domain.comment.api.support;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClientIpResolverTest {

    private final ClientIpResolver resolver = new ClientIpResolver();

    @Test
    void usesRemoteAddressAndIgnoresSpoofedForwardedHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.10");
        request.addHeader("X-Forwarded-For", "198.51.100.99");

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.10");
    }

    @Test
    void rejectsMissingRemoteAddressWithoutFallback() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(" ");

        assertThatThrownBy(() -> resolver.resolve(request))
                .isInstanceOf(IllegalStateException.class);
    }
}
