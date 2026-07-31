package web.tosunsaeng.domain.newsletter.domain.policy;

import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import web.tosunsaeng.domain.newsletter.exception.NewsletterException;
import web.tosunsaeng.global.error.code.status.ErrorStatus;

import java.util.Locale;

@Component
@RequiredArgsConstructor
public class NewsletterEmailNormalizer {

    static final int MAX_EMAIL_CODE_POINTS = 254;

    private final Validator validator;

    public String normalize(String rawEmail) {
        if (rawEmail == null) {
            throw new NewsletterException(ErrorStatus._NEWSLETTER_EMAIL_REQUIRED);
        }
        String normalized = rawEmail.strip().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new NewsletterException(ErrorStatus._NEWSLETTER_EMAIL_REQUIRED);
        }
        if (normalized.codePointCount(0, normalized.length()) > MAX_EMAIL_CODE_POINTS) {
            throw new NewsletterException(ErrorStatus._NEWSLETTER_EMAIL_TOO_LONG);
        }
        if (!validator.validate(new EmailCandidate(normalized)).isEmpty()) {
            throw new NewsletterException(ErrorStatus._NEWSLETTER_EMAIL_INVALID);
        }
        return normalized;
    }

    private record EmailCandidate(@Email String value) {
    }
}
