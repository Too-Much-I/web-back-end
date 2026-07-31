package web.tosunsaeng.domain.comment.api.support;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import web.tosunsaeng.domain.comment.config.AnonymousSessionProperties;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class AnonymousCookieFactory {

    public static final String COOKIE_NAME = "anon_session";

    private final AnonymousSessionProperties properties;

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
