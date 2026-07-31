package web.tosunsaeng.domain.comment.domain.repository;

import web.tosunsaeng.domain.comment.domain.enums.CommentLimitScope;
import web.tosunsaeng.domain.comment.domain.policy.CommentRateLimitKeyFactory;

import java.util.List;

public interface CommentRateLimitRepository {

    AdmissionResult admit(
            CommentRateLimitKeyFactory.RateLimitKeys keys,
            String reservationOwner);

    boolean releaseDuplicate(String duplicateKey, String reservationOwner);

    record Blocker(CommentLimitScope scope, long ttlSeconds) {
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
