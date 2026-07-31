package web.tosunsaeng.domain.comment.application;

import java.util.function.Supplier;

public interface CommentAbusePreventionService {

    Admission admit(
            String visitorTokenHash,
            String postId,
            String normalizedContent,
            Supplier<String> clientIpSupplier);

    void releaseDuplicate(Admission admission);

    record Admission(
            boolean duplicateReserved,
            String duplicateKey,
            String reservationOwner) {

        public static Admission withoutReservation() {
            return new Admission(false, null, null);
        }

        public static Admission reserved(String duplicateKey, String reservationOwner) {
            return new Admission(true, duplicateKey, reservationOwner);
        }
    }
}
