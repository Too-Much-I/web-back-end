package web.tosunsaeng.domain.comment.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import web.tosunsaeng.domain.comment.config.BlogCommentAbuseProperties;
import web.tosunsaeng.domain.comment.domain.policy.CommentRateLimitHasher;
import web.tosunsaeng.domain.comment.domain.policy.CommentRateLimitKeyFactory;
import web.tosunsaeng.domain.comment.domain.policy.RedisFailureClassifier;
import web.tosunsaeng.domain.comment.domain.repository.CommentRateLimitRepository;
import web.tosunsaeng.domain.comment.exception.BlogCommentException;
import web.tosunsaeng.domain.comment.exception.CommentRateLimitException;
import web.tosunsaeng.global.error.code.status.ErrorStatus;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Comparator;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommentAbusePreventionServiceImpl implements CommentAbusePreventionService {

    static final int RESERVATION_OWNER_BYTES = 32;

    private final BlogCommentAbuseProperties properties;
    private final CommentRateLimitHasher hasher;
    private final CommentRateLimitKeyFactory keyFactory;
    private final CommentRateLimitRepository rateLimitRepository;
    private final RedisFailureClassifier failureClassifier;
    private final SecureRandom secureRandom;

    @Override
    public Admission admit(
            String visitorTokenHash,
            String postId,
            String normalizedContent,
            Supplier<String> clientIpSupplier) {
        if (!properties.isEnabled()) {
            return Admission.withoutReservation();
        }

        CommentRateLimitKeyFactory.RateLimitKeys keys;
        String reservationOwner;
        try {
            String ipHash = hasher.hashIp(clientIpSupplier.get());
            String contentHash = hasher.hashContent(normalizedContent);
            keys = keyFactory.create(visitorTokenHash, ipHash, postId, contentHash);
            reservationOwner = createReservationOwner();
        } catch (RuntimeException exception) {
            log.error(
                    "event=blog.comment.redis.failure operation=preparation exceptionType={}",
                    exception.getClass().getName());
            throw new BlogCommentException(ErrorStatus._INTERNAL_SERVER_ERROR);
        }

        CommentRateLimitRepository.AdmissionResult result;
        try {
            result = rateLimitRepository.admit(keys, reservationOwner);
        } catch (RuntimeException exception) {
            if (failureClassifier.isConnectivityFailure(exception)) {
                log.warn(
                        "event=blog.comment.redis.degraded operation=admission exceptionType={}",
                        exception.getClass().getName());
                return Admission.withoutReservation();
            }
            log.error(
                    "event=blog.comment.redis.failure operation=admission exceptionType={}",
                    exception.getClass().getName());
            throw new BlogCommentException(ErrorStatus._INTERNAL_SERVER_ERROR);
        }

        if (result.allowed()) {
            return Admission.reserved(keys.duplicate(), reservationOwner);
        }

        CommentRateLimitRepository.Blocker selected = result.blockers().stream()
                .sorted(Comparator
                        .comparingLong(CommentRateLimitRepository.Blocker::ttlSeconds)
                        .reversed()
                        .thenComparingInt(blocker -> blocker.scope().getTiePriority()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("rate limit blocker가 없습니다."));
        throw new CommentRateLimitException(
                Math.max(1L, selected.ttlSeconds()),
                selected.scope());
    }

    @Override
    public void releaseDuplicate(Admission admission) {
        if (admission == null || !admission.duplicateReserved()) {
            return;
        }
        try {
            rateLimitRepository.releaseDuplicate(
                    admission.duplicateKey(),
                    admission.reservationOwner());
        } catch (RuntimeException exception) {
            if (failureClassifier.isConnectivityFailure(exception)) {
                log.warn(
                        "event=blog.comment.redis.degraded operation=duplicate-release exceptionType={}",
                        exception.getClass().getName());
                return;
            }
            log.error(
                    "event=blog.comment.redis.failure operation=duplicate-release exceptionType={}",
                    exception.getClass().getName());
            throw new BlogCommentException(ErrorStatus._INTERNAL_SERVER_ERROR);
        }
    }

    private String createReservationOwner() {
        byte[] bytes = new byte[RESERVATION_OWNER_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
