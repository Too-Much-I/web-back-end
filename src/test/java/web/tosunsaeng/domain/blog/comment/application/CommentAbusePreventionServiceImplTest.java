package web.tosunsaeng.domain.blog.comment.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import web.tosunsaeng.domain.blog.comment.config.BlogCommentAbuseProperties;
import web.tosunsaeng.domain.blog.comment.domain.enums.CommentLimitScope;
import web.tosunsaeng.domain.blog.comment.domain.policy.CommentRateLimitHasher;
import web.tosunsaeng.domain.blog.comment.domain.policy.CommentRateLimitKeyFactory;
import web.tosunsaeng.domain.blog.comment.domain.policy.RedisFailureClassifier;
import web.tosunsaeng.domain.blog.comment.domain.repository.CommentRateLimitRepository;
import web.tosunsaeng.domain.blog.comment.exception.BlogCommentException;
import web.tosunsaeng.domain.blog.comment.exception.CommentRateLimitException;
import web.tosunsaeng.global.error.code.status.ErrorStatus;

import java.security.SecureRandom;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class CommentAbusePreventionServiceImplTest {

    @Mock
    private CommentRateLimitRepository repository;

    private BlogCommentAbuseProperties properties;
    private CommentAbusePreventionServiceImpl service;

    @BeforeEach
    void setUp() {
        properties = properties(true);
        service = service(properties);
    }

    @Test
    void allowedAdmissionCreatesOpaqueOwnerReservationWithHashedIpAndContent() {
        when(repository.admit(any(), anyString()))
                .thenReturn(CommentRateLimitRepository.AdmissionResult.allowedResult());

        CommentAbusePreventionService.Admission admission = service.admit(
                "visitor_hash",
                "post-id",
                "정규화된 댓글",
                () -> "203.0.113.10");

        assertThat(admission.duplicateReserved()).isTrue();
        assertThat(admission.reservationOwner()).matches("[A-Za-z0-9_-]{43}");
        ArgumentCaptor<CommentRateLimitKeyFactory.RateLimitKeys> keysCaptor =
                ArgumentCaptor.forClass(CommentRateLimitKeyFactory.RateLimitKeys.class);
        verify(repository).admit(keysCaptor.capture(), anyString());
        assertThat(keysCaptor.getValue().asList())
                .noneMatch(key -> key.contains("203.0.113.10"))
                .noneMatch(key -> key.contains("정규화된 댓글"));
    }

    @Test
    void selectsLongestBlockerForRetryAfter() {
        when(repository.admit(any(), anyString())).thenReturn(
                CommentRateLimitRepository.AdmissionResult.denied(List.of(
                        blocker(CommentLimitScope.VISITOR_SHORT, 8),
                        blocker(CommentLimitScope.VISITOR_DAILY, 400),
                        blocker(CommentLimitScope.DUPLICATE, 300))));

        assertThatThrownBy(this::admit)
                .isInstanceOfSatisfying(
                        CommentRateLimitException.class,
                        exception -> {
                            assertThat(exception.getRetryAfterSeconds()).isEqualTo(400);
                            assertThat(exception.getLimitScope())
                                    .isEqualTo(CommentLimitScope.VISITOR_DAILY);
                        });
    }

    @Test
    void equalTtlUsesApprovedStableScopePriority() {
        when(repository.admit(any(), anyString())).thenReturn(
                CommentRateLimitRepository.AdmissionResult.denied(List.of(
                        blocker(CommentLimitScope.VISITOR_SHORT, 60),
                        blocker(CommentLimitScope.IP_MEDIUM, 60),
                        blocker(CommentLimitScope.VISITOR_MEDIUM, 60),
                        blocker(CommentLimitScope.IP_DAILY, 60),
                        blocker(CommentLimitScope.VISITOR_DAILY, 60),
                        blocker(CommentLimitScope.DUPLICATE, 60))));

        assertThatThrownBy(this::admit)
                .isInstanceOfSatisfying(
                        CommentRateLimitException.class,
                        exception -> assertThat(exception.getLimitScope())
                                .isEqualTo(CommentLimitScope.DUPLICATE));
    }

    @Test
    void zeroRedisTtlIsExposedAsMinimumOneSecond() {
        when(repository.admit(any(), anyString())).thenReturn(
                CommentRateLimitRepository.AdmissionResult.denied(List.of(
                        blocker(CommentLimitScope.VISITOR_SHORT, 0))));

        assertThatThrownBy(this::admit)
                .isInstanceOfSatisfying(
                        CommentRateLimitException.class,
                        exception -> assertThat(exception.getRetryAfterSeconds()).isEqualTo(1));
    }

    @Test
    void redisConnectionFailureFailsOpenWithoutReservation() {
        when(repository.admit(any(), anyString()))
                .thenThrow(new RedisConnectionFailureException("unavailable"));

        CommentAbusePreventionService.Admission result = admit();

        assertThat(result.duplicateReserved()).isFalse();
    }

    @Test
    void redisTimeoutAlsoFailsOpenWithoutReservation() {
        when(repository.admit(any(), anyString()))
                .thenThrow(new QueryTimeoutException("timeout"));

        assertThat(admit().duplicateReserved()).isFalse();
    }

    @Test
    void degradedWarningDoesNotLogRawOrHashedIdentifiers(CapturedOutput output) {
        when(repository.admit(any(), anyString()))
                .thenThrow(new RedisConnectionFailureException("unavailable"));

        service.admit(
                "visitor_hash_sensitive",
                "post-id",
                "민감한 댓글 원문",
                () -> "203.0.113.77");

        assertThat(output.getOut() + output.getErr())
                .contains("event=blog.comment.redis.degraded")
                .doesNotContain(
                        "visitor_hash_sensitive",
                        "민감한 댓글 원문",
                        "203.0.113.77",
                        "BLOG_COMMENT_RATE_LIMIT_SECRET");
    }

    @Test
    void luaOrApplicationErrorDoesNotFailOpenAndUsesGenericInternalError() {
        when(repository.admit(any(), anyString()))
                .thenThrow(new IllegalStateException("script contract details"));

        assertThatThrownBy(this::admit)
                .isInstanceOfSatisfying(
                        BlogCommentException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(ErrorStatus._INTERNAL_SERVER_ERROR))
                .hasMessageNotContaining("script contract details");
    }

    @Test
    void disabledPolicySkipsIpResolutionHashingAndRedis() {
        properties = properties(false);
        service = service(properties);
        AtomicBoolean supplierCalled = new AtomicBoolean();

        CommentAbusePreventionService.Admission result = service.admit(
                "visitor_hash",
                "post-id",
                "정상 댓글",
                () -> {
                    supplierCalled.set(true);
                    return "203.0.113.10";
                });

        assertThat(result.duplicateReserved()).isFalse();
        assertThat(supplierCalled).isFalse();
        verifyNoInteractions(repository);
    }

    @Test
    void cleanupUsesReservationOwnerAndConnectionFailureIsBestEffort() {
        CommentAbusePreventionService.Admission admission =
                CommentAbusePreventionService.Admission.reserved("duplicate-key", "owner");
        when(repository.releaseDuplicate("duplicate-key", "owner"))
                .thenThrow(new RedisConnectionFailureException("unavailable"));

        assertThatCode(() -> service.releaseDuplicate(admission)).doesNotThrowAnyException();
        service.releaseDuplicate(CommentAbusePreventionService.Admission.withoutReservation());

        verify(repository).releaseDuplicate("duplicate-key", "owner");
    }

    @Test
    void cleanupImplementationErrorIsNotHidden() {
        CommentAbusePreventionService.Admission admission =
                CommentAbusePreventionService.Admission.reserved("duplicate-key", "owner");
        when(repository.releaseDuplicate("duplicate-key", "owner"))
                .thenThrow(new IllegalStateException("bad release"));

        assertThatThrownBy(() -> service.releaseDuplicate(admission))
                .isInstanceOfSatisfying(
                        BlogCommentException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(ErrorStatus._INTERNAL_SERVER_ERROR));
    }

    private CommentAbusePreventionService.Admission admit() {
        return service.admit(
                "visitor_hash",
                "post-id",
                "정상 댓글",
                () -> "203.0.113.10");
    }

    private CommentRateLimitRepository.Blocker blocker(
            CommentLimitScope scope,
            long ttl) {
        return new CommentRateLimitRepository.Blocker(scope, ttl);
    }

    private CommentAbusePreventionServiceImpl service(
            BlogCommentAbuseProperties currentProperties) {
        return new CommentAbusePreventionServiceImpl(
                currentProperties,
                new CommentRateLimitHasher(currentProperties),
                new CommentRateLimitKeyFactory(),
                repository,
                new RedisFailureClassifier(),
                new FixedSecureRandom());
    }

    private BlogCommentAbuseProperties properties(boolean enabled) {
        BlogCommentAbuseProperties configured = new BlogCommentAbuseProperties();
        configured.setSecret(enabled
                ? "test-only-comment-rate-limit-secret-at-least-32-bytes"
                : "");
        configured.setEnabled(enabled);
        configured.afterPropertiesSet();
        return configured;
    }

    private static class FixedSecureRandom extends SecureRandom {

        @Override
        public void nextBytes(byte[] bytes) {
            for (int index = 0; index < bytes.length; index++) {
                bytes[index] = (byte) (index + 1);
            }
        }
    }
}
