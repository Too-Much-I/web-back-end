package web.tosunsaeng.domain.newsletter.application;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import web.tosunsaeng.domain.newsletter.converter.NewsletterConverter;
import web.tosunsaeng.domain.newsletter.domain.entity.NewsletterSubscriber;
import web.tosunsaeng.domain.newsletter.domain.enums.NewsletterSubscriberStatus;
import web.tosunsaeng.domain.newsletter.domain.policy.NewsletterEmailNormalizer;
import web.tosunsaeng.domain.newsletter.domain.policy.NewsletterUnsubscribeTokenManager;
import web.tosunsaeng.domain.newsletter.domain.repository.NewsletterSubscriberRepository;
import web.tosunsaeng.domain.newsletter.dto.NewsletterRequestDTO;
import web.tosunsaeng.domain.newsletter.dto.NewsletterResponseDTO;
import web.tosunsaeng.domain.newsletter.exception.NewsletterException;
import web.tosunsaeng.global.error.code.status.ErrorStatus;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class NewsletterServiceImpl implements NewsletterService {

    static final int MAX_STATE_RESOLUTION_ATTEMPTS = 5;

    private final NewsletterSubscriberRepository subscriberRepository;
    private final NewsletterRateLimitService rateLimitService;
    private final NewsletterEmailNormalizer emailNormalizer;
    private final NewsletterUnsubscribeTokenManager tokenManager;
    private final NewsletterConverter converter;
    private final Clock clock;

    @Override
    public NewsletterResponseDTO.StatusResult subscribe(
            NewsletterRequestDTO.SubscribeRequest request,
            String clientIp) {
        rateLimitService.check(clientIp);
        String normalizedEmail = emailNormalizer.normalize(
                request == null ? null : request.getEmail());
        if (request == null || !Boolean.TRUE.equals(request.getConsent())) {
            throw new NewsletterException(ErrorStatus._NEWSLETTER_CONSENT_REQUIRED);
        }

        Instant now = clock.instant();
        for (int attempt = 0; attempt < MAX_STATE_RESOLUTION_ATTEMPTS; attempt++) {
            Optional<NewsletterSubscriber> reactivated =
                    subscriberRepository.reactivateByEmail(normalizedEmail, now);
            if (reactivated.isPresent()) {
                return activeResult();
            }

            Optional<NewsletterSubscriber> existing =
                    subscriberRepository.findByEmail(normalizedEmail);
            if (existing.isPresent()) {
                NewsletterSubscriberStatus status = existing.orElseThrow().getStatus();
                if (status == NewsletterSubscriberStatus.ACTIVE) {
                    return activeResult();
                }
                if (status == NewsletterSubscriberStatus.BOUNCED) {
                    throw new NewsletterException(
                            ErrorStatus._NEWSLETTER_SUBSCRIPTION_UNAVAILABLE);
                }
                continue;
            }

            try {
                subscriberRepository.insert(
                        NewsletterSubscriber.newActive(normalizedEmail, now));
                return activeResult();
            } catch (DuplicateKeyException exception) {
                // 동시 insert의 loser는 다음 반복에서 현재 상태를 다시 판정한다.
            }
        }
        throw new NewsletterException(ErrorStatus._INTERNAL_SERVER_ERROR);
    }

    @Override
    public NewsletterResponseDTO.StatusResult unsubscribe(
            NewsletterRequestDTO.UnsubscribeRequest request) {
        NewsletterUnsubscribeTokenManager.TokenClaims claims = tokenManager.verify(
                request == null ? null : request.getToken());
        Instant now = clock.instant();
        Optional<NewsletterSubscriber> changed =
                subscriberRepository.unsubscribeByIdAndVersion(
                        claims.subscriberId(),
                        claims.subscriberTokenVersion(),
                        now);
        if (changed.isPresent()) {
            return unsubscribedResult();
        }

        Optional<NewsletterSubscriber> existing =
                subscriberRepository.findById(claims.subscriberId());
        if (existing.isPresent()
                && existing.orElseThrow().getStatus()
                == NewsletterSubscriberStatus.UNSUBSCRIBED
                && existing.orElseThrow().getTokenVersion()
                == claims.subscriberTokenVersion()) {
            return unsubscribedResult();
        }
        throw new NewsletterException(
                ErrorStatus._NEWSLETTER_UNSUBSCRIBE_TOKEN_INVALID);
    }

    private NewsletterResponseDTO.StatusResult activeResult() {
        return converter.toStatusResult(NewsletterSubscriberStatus.ACTIVE);
    }

    private NewsletterResponseDTO.StatusResult unsubscribedResult() {
        return converter.toStatusResult(NewsletterSubscriberStatus.UNSUBSCRIBED);
    }
}
