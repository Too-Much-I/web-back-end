package web.tosunsaeng.domain.newsletter.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import web.tosunsaeng.domain.newsletter.config.NewsletterRateLimitProperties;
import web.tosunsaeng.domain.newsletter.domain.policy.NewsletterRateLimitHasher;
import web.tosunsaeng.domain.newsletter.domain.policy.NewsletterRateLimitKeyFactory;
import web.tosunsaeng.domain.newsletter.domain.policy.NewsletterRedisFailureClassifier;
import web.tosunsaeng.domain.newsletter.domain.repository.NewsletterRateLimitRepository;
import web.tosunsaeng.domain.newsletter.exception.NewsletterException;
import web.tosunsaeng.domain.newsletter.exception.NewsletterRateLimitException;
import web.tosunsaeng.global.error.code.status.ErrorStatus;

import java.util.Comparator;

@Slf4j
@Service
@RequiredArgsConstructor
public class NewsletterRateLimitServiceImpl implements NewsletterRateLimitService {

    private final NewsletterRateLimitProperties properties;
    private final NewsletterRateLimitHasher hasher;
    private final NewsletterRateLimitKeyFactory keyFactory;
    private final NewsletterRateLimitRepository repository;
    private final NewsletterRedisFailureClassifier failureClassifier;

    @Override
    public void check(String clientIp) {
        if (!properties.isEnabled()) {
            return;
        }

        NewsletterRateLimitKeyFactory.RateLimitKeys keys;
        try {
            keys = keyFactory.create(hasher.hashIp(clientIp));
        } catch (RuntimeException exception) {
            log.error(
                    "event=newsletter.subscribe.redis.failure operation=preparation exceptionType={}",
                    exception.getClass().getName());
            throw new NewsletterException(ErrorStatus._INTERNAL_SERVER_ERROR);
        }

        NewsletterRateLimitRepository.AdmissionResult result;
        try {
            result = repository.admit(keys);
        } catch (RuntimeException exception) {
            if (failureClassifier.isConnectivityFailure(exception)) {
                log.warn(
                        "event=newsletter.subscribe.redis.degraded operation=admission exceptionType={}",
                        exception.getClass().getName());
                return;
            }
            log.error(
                    "event=newsletter.subscribe.redis.failure operation=admission exceptionType={}",
                    exception.getClass().getName());
            throw new NewsletterException(ErrorStatus._INTERNAL_SERVER_ERROR);
        }

        if (result.allowed()) {
            return;
        }
        NewsletterRateLimitRepository.Blocker blocker = result.blockers().stream()
                .sorted(Comparator
                        .comparingLong(NewsletterRateLimitRepository.Blocker::ttlSeconds)
                        .reversed()
                        .thenComparingInt(value -> value.scope().getTiePriority()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("newsletter rate limit blocker가 없습니다."));
        throw new NewsletterRateLimitException(
                Math.max(1L, blocker.ttlSeconds()),
                blocker.scope());
    }
}
