package web.tosunsaeng.domain.blog.comment.api.support;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import web.tosunsaeng.domain.blog.comment.config.AnonymousSessionProperties;

import java.time.Duration;

@Component
public class AnonymousCookieFactory {

    public static final String COOKIE_NAME = "anon_session";

    private final AnonymousSessionProperties properties;

    public AnonymousCookieFactory(AnonymousSessionProperties properties) {
        this.properties = properties;
    }

    public ResponseCookie create(String rawToken) {
        return ResponseCookie.from(COOKIE_NAME, rawToken)
                .httpOnly(true)
                .secure(properties.isCookieSecure())
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ofDays(properties.getCookieMaxAgeDays()))
                .build();
    }
}
