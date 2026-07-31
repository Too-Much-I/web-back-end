package web.tosunsaeng.domain.newsletter.domain.policy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import web.tosunsaeng.domain.newsletter.config.NewsletterUnsubscribeTokenProperties;
import web.tosunsaeng.domain.newsletter.domain.entity.NewsletterSubscriber;
import web.tosunsaeng.domain.newsletter.exception.NewsletterException;
import web.tosunsaeng.global.error.code.status.ErrorStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NewsletterUnsubscribeTokenManagerTest {

    private static final Instant NOW = Instant.parse("2026-07-31T01:00:00Z");
    private static final String ACTIVE_SECRET =
            "test-only-newsletter-active-signing-secret-at-least-32-bytes";
    private static final String PREVIOUS_SECRET =
            "test-only-newsletter-previous-signing-secret-at-least-32-bytes";

    @Test
    void createsUrlSafeTokenAndVerifiesApprovedClaims() {
        NewsletterUnsubscribeTokenManager manager = manager(
                properties("active-v1", ACTIVE_SECRET, Map.of()), NOW);

        String token = manager.createToken("subscriber-id", 3);
        NewsletterUnsubscribeTokenManager.TokenClaims claims = manager.verify(token);

        assertThat(token).matches("^[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+$");
        assertThat(token).doesNotContain("=");
        assertThat(claims.formatVersion()).isEqualTo(1);
        assertThat(claims.keyId()).isEqualTo("active-v1");
        assertThat(claims.subscriberId()).isEqualTo("subscriber-id");
        assertThat(claims.subscriberTokenVersion()).isEqualTo(3);
        assertThat(claims.issuedAt()).isEqualTo(NOW);
    }

    @Test
    void rejectsPayloadAndSignatureTamperingWithGenericError() {
        NewsletterUnsubscribeTokenManager manager = manager(
                properties("active-v1", ACTIVE_SECRET, Map.of()), NOW);
        String token = manager.createToken("subscriber-id", 1);
        String[] segments = token.split("\\.");
        String tamperedPayload = flipLastCharacter(segments[0]) + "." + segments[1];
        String tamperedSignature = segments[0] + "." + flipLastCharacter(segments[1]);

        assertInvalid(manager, tamperedPayload);
        assertInvalid(manager, tamperedSignature);
    }

    @Test
    void activeKeyIssuesAndPreviousKeyOnlyVerifiesDuringRotation() {
        NewsletterUnsubscribeTokenManager oldManager = manager(
                properties("previous-v0", PREVIOUS_SECRET, Map.of()), NOW);
        String oldToken = oldManager.createToken("subscriber-id", 2);
        NewsletterUnsubscribeTokenProperties rotated = properties(
                "active-v1",
                ACTIVE_SECRET,
                Map.of("previous-v0", PREVIOUS_SECRET));
        NewsletterUnsubscribeTokenManager rotatedManager = manager(rotated, NOW);

        assertThat(rotatedManager.verify(oldToken).keyId()).isEqualTo("previous-v0");
        assertThat(rotatedManager.verify(
                rotatedManager.createToken("subscriber-id", 2)).keyId())
                .isEqualTo("active-v1");

        NewsletterUnsubscribeTokenManager withoutPrevious = manager(
                properties("active-v1", ACTIVE_SECRET, Map.of()), NOW);
        assertInvalid(withoutPrevious, oldToken);
    }

    @Test
    void tokenDoesNotExpireButRejectsExcessiveFutureIssuedAt() {
        NewsletterUnsubscribeTokenProperties properties = properties(
                "active-v1", ACTIVE_SECRET, Map.of());
        String oldToken = manager(properties, NOW)
                .createToken("subscriber-id", 1);

        assertThat(manager(properties, NOW.plus(Duration.ofDays(3650)))
                .verify(oldToken).subscriberId()).isEqualTo("subscriber-id");

        String futureToken = manager(properties, NOW.plusSeconds(301))
                .createToken("subscriber-id", 1);
        assertInvalid(manager(properties, NOW), futureToken);
    }

    @Test
    void rejectsMalformedOversizedAndWrongSecretWithoutEchoingToken() {
        NewsletterUnsubscribeTokenProperties properties = properties(
                "active-v1", ACTIVE_SECRET, Map.of());
        NewsletterUnsubscribeTokenManager manager = manager(properties, NOW);
        String token = manager.createToken("subscriber-id", 1);

        assertInvalid(manager, null);
        assertInvalid(manager, " ");
        assertInvalid(manager, "not.a.valid.token");
        assertInvalid(manager, "x".repeat(properties.getMaxTokenLength() + 1));
        NewsletterUnsubscribeTokenManager otherSecret = manager(
                properties(
                        "active-v1",
                        "different-newsletter-signing-secret-at-least-32-bytes",
                        Map.of()),
                NOW);
        assertInvalid(otherSecret, token);

        assertThatThrownBy(() -> otherSecret.verify(token))
                .isInstanceOfSatisfying(NewsletterException.class, exception ->
                        assertThat(String.valueOf(exception.getMessage()))
                                .doesNotContain(token));
    }

    @Test
    void subscriberDocumentStoresNeitherRawTokenNorTokenHash() {
        assertThat(Arrays.stream(NewsletterSubscriber.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName))
                .noneMatch(name -> name.equals("unsubscribeToken")
                        || name.equals("unsubscribeTokenHash")
                        || name.equals("verificationToken")
                        || name.equals("verificationTokenHash"));
    }

    private NewsletterUnsubscribeTokenManager manager(
            NewsletterUnsubscribeTokenProperties properties,
            Instant now) {
        return new NewsletterUnsubscribeTokenManager(
                new ObjectMapper(),
                Clock.fixed(now, ZoneOffset.UTC),
                properties);
    }

    private NewsletterUnsubscribeTokenProperties properties(
            String keyId,
            String secret,
            Map<String, String> previousKeys) {
        NewsletterUnsubscribeTokenProperties properties =
                new NewsletterUnsubscribeTokenProperties();
        properties.setKeyId(keyId);
        properties.setSecret(secret);
        properties.setPreviousKeys(previousKeys);
        properties.afterPropertiesSet();
        return properties;
    }

    private void assertInvalid(
            NewsletterUnsubscribeTokenManager manager,
            String token) {
        assertThatThrownBy(() -> manager.verify(token))
                .isInstanceOfSatisfying(NewsletterException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(
                                ErrorStatus._NEWSLETTER_UNSUBSCRIBE_TOKEN_INVALID));
    }

    private String flipLastCharacter(String value) {
        char last = value.charAt(value.length() - 1);
        return value.substring(0, value.length() - 1) + (last == 'A' ? 'B' : 'A');
    }
}
