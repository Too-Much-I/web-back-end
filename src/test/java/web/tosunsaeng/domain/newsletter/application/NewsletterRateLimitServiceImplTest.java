package web.tosunsaeng.domain.newsletter.application;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.RedisConnectionFailureException;
import web.tosunsaeng.domain.newsletter.config.NewsletterRateLimitProperties;
import web.tosunsaeng.domain.newsletter.domain.enums.NewsletterRateLimitScope;
import web.tosunsaeng.domain.newsletter.domain.policy.NewsletterRateLimitHasher;
import web.tosunsaeng.domain.newsletter.domain.policy.NewsletterRateLimitKeyFactory;
import web.tosunsaeng.domain.newsletter.domain.policy.NewsletterRedisFailureClassifier;
import web.tosunsaeng.domain.newsletter.domain.repository.NewsletterRateLimitRepository;
import web.tosunsaeng.domain.newsletter.exception.NewsletterException;
import web.tosunsaeng.domain.newsletter.exception.NewsletterRateLimitException;
import web.tosunsaeng.global.error.code.status.ErrorStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NewsletterRateLimitServiceImplTest {

    private static final String RAW_IP = "203.0.113.10";

    @Mock
    private NewsletterRateLimitRepository repository;

    private NewsletterRateLimitProperties properties;
    private NewsletterRateLimitServiceImpl service;

    @BeforeEach
    void setUp() {
        properties = properties(true);
        service = service(properties);
    }

    @Test
    void disabledRateLimitDoesNotHashOrCallRedis() {
        NewsletterRateLimitProperties disabled = properties(false);
        NewsletterRateLimitHasher hasher = org.mockito.Mockito.mock(
                NewsletterRateLimitHasher.class);
        NewsletterRateLimitServiceImpl disabledService =
                new NewsletterRateLimitServiceImpl(
                        disabled,
                        hasher,
                        new NewsletterRateLimitKeyFactory(),
                        repository,
                        new NewsletterRedisFailureClassifier());

        disabledService.check(RAW_IP);

        verify(hasher, never()).hashIp(any());
        verify(repository, never()).admit(any());
    }

    @Test
    void allowedRequestPasses() {
        when(repository.admit(any()))
                .thenReturn(NewsletterRateLimitRepository.AdmissionResult.allowedResult());

        assertThatCode(() -> service.check(RAW_IP)).doesNotThrowAnyException();

        verify(repository).admit(any());
    }

    @Test
    void blockedRequestUsesMaximumTtlAndDailyTiePriority() {
        when(repository.admit(any())).thenReturn(
                NewsletterRateLimitRepository.AdmissionResult.denied(List.of(
                        new NewsletterRateLimitRepository.Blocker(
                                NewsletterRateLimitScope.IP_MEDIUM, 600),
                        new NewsletterRateLimitRepository.Blocker(
                                NewsletterRateLimitScope.IP_DAILY, 600))));

        assertThatThrownBy(() -> service.check(RAW_IP))
                .isInstanceOfSatisfying(NewsletterRateLimitException.class, exception -> {
                    assertThat(exception.getRetryAfterSeconds()).isEqualTo(600);
                    assertThat(exception.getLimitScope())
                            .isEqualTo(NewsletterRateLimitScope.IP_DAILY);
                });
    }

    @Test
    void zeroRedisTtlIsReturnedAsMinimumOneSecond() {
        when(repository.admit(any())).thenReturn(
                NewsletterRateLimitRepository.AdmissionResult.denied(List.of(
                        new NewsletterRateLimitRepository.Blocker(
                                NewsletterRateLimitScope.IP_MEDIUM, 0))));

        assertThatThrownBy(() -> service.check(RAW_IP))
                .isInstanceOfSatisfying(NewsletterRateLimitException.class, exception ->
                        assertThat(exception.getRetryAfterSeconds()).isEqualTo(1));
    }

    @Test
    void connectionFailureFailsOpenWithSanitizedWarning() {
        String sensitiveMessage = RAW_IP + " user@example.com token-value";
        when(repository.admit(any())).thenThrow(
                new RedisConnectionFailureException(sensitiveMessage));
        Logger logger = (Logger) LoggerFactory.getLogger(
                NewsletterRateLimitServiceImpl.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            assertThatCode(() -> service.check(RAW_IP)).doesNotThrowAnyException();
            assertThat(appender.list)
                    .extracting(ILoggingEvent::getFormattedMessage)
                    .allSatisfy(message -> assertThat(message)
                            .doesNotContain(RAW_IP)
                            .doesNotContain("user@example.com")
                            .doesNotContain("token-value"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void applicationAndLuaErrorsAreNotFailOpen() {
        when(repository.admit(any()))
                .thenThrow(new IllegalStateException("bad lua result"));

        assertThatThrownBy(() -> service.check(RAW_IP))
                .isInstanceOfSatisfying(NewsletterException.class, exception ->
                        assertThat(exception.getCode())
                                .isEqualTo(ErrorStatus._INTERNAL_SERVER_ERROR));
    }

    @Test
    void missingIpIsAnInternalFailureAndNeverReachesRedis() {
        assertThatThrownBy(() -> service.check(" "))
                .isInstanceOfSatisfying(NewsletterException.class, exception ->
                        assertThat(exception.getCode())
                                .isEqualTo(ErrorStatus._INTERNAL_SERVER_ERROR));
        verify(repository, never()).admit(any());
    }

    private NewsletterRateLimitServiceImpl service(
            NewsletterRateLimitProperties sourceProperties) {
        return new NewsletterRateLimitServiceImpl(
                sourceProperties,
                new NewsletterRateLimitHasher(sourceProperties),
                new NewsletterRateLimitKeyFactory(),
                repository,
                new NewsletterRedisFailureClassifier());
    }

    private NewsletterRateLimitProperties properties(boolean enabled) {
        NewsletterRateLimitProperties properties = new NewsletterRateLimitProperties();
        properties.setSecret(enabled
                ? "test-only-newsletter-rate-secret-at-least-32-bytes"
                : "");
        properties.setEnabled(enabled);
        properties.afterPropertiesSet();
        return properties;
    }
}
