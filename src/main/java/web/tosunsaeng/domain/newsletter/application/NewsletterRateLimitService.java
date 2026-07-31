package web.tosunsaeng.domain.newsletter.application;

public interface NewsletterRateLimitService {

    void check(String clientIp);
}
