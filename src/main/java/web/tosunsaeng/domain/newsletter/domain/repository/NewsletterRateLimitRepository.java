package web.tosunsaeng.domain.newsletter.domain.repository;

import web.tosunsaeng.domain.newsletter.domain.enums.NewsletterRateLimitScope;
import web.tosunsaeng.domain.newsletter.domain.policy.NewsletterRateLimitKeyFactory;

import java.util.List;

public interface NewsletterRateLimitRepository {

    AdmissionResult admit(NewsletterRateLimitKeyFactory.RateLimitKeys keys);

    record Blocker(NewsletterRateLimitScope scope, long ttlSeconds) {
    }

    record AdmissionResult(boolean allowed, List<Blocker> blockers) {

        public AdmissionResult {
            blockers = List.copyOf(blockers);
            if (allowed && !blockers.isEmpty()) {
                throw new IllegalArgumentException("허용 결과에는 blocker가 있을 수 없습니다.");
            }
            if (!allowed && blockers.isEmpty()) {
                throw new IllegalArgumentException("차단 결과에는 blocker가 필요합니다.");
            }
        }

        public static AdmissionResult allowedResult() {
            return new AdmissionResult(true, List.of());
        }

        public static AdmissionResult denied(List<Blocker> blockers) {
            return new AdmissionResult(false, blockers);
        }
    }
}
