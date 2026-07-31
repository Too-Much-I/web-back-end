package web.tosunsaeng.domain.newsletter.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import web.tosunsaeng.domain.newsletter.domain.enums.NewsletterEmailProvider;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "newsletter.delivery")
public class NewsletterDeliveryProperties implements InitializingBean {

    private boolean sendingEnabled = false;
    private NewsletterEmailProvider emailProvider = NewsletterEmailProvider.LOGGING;
    private String fromEmail = "";
    private String fromName = "토선생";
    private String publicBaseUrl = "";
    private String apiBaseUrl = "";
    private int sendDelayMinutes = 15;
    private int batchSize = 100;
    private int workerCount = 2;
    private int queueCapacity = 100;
    private long claimTtlSeconds = 300;
    private long reconciliationDelayMs = 60_000;
    private long campaignDelayMs = 10_000;
    private long deliveryDelayMs = 5_000;
    private long retryDelayMs = 30_000;
    private long completionDelayMs = 30_000;
    private long staleRecoveryDelayMs = 60_000;
    private boolean testSendingEnabled = false;
    private List<String> testRecipientAllowlist = new ArrayList<>();
    private String awsRegion = "ap-northeast-2";

    @Override
    public void afterPropertiesSet() {
        requireRange(sendDelayMinutes, 0, 10_080, "send delay minutes");
        requireRange(batchSize, 1, 1_000, "batch size");
        requireRange(workerCount, 1, 16, "worker count");
        requireRange(queueCapacity, 1, 10_000, "queue capacity");
        requirePositive(claimTtlSeconds, "claim TTL");
        requirePositive(reconciliationDelayMs, "reconciliation delay");
        requirePositive(campaignDelayMs, "campaign delay");
        requirePositive(deliveryDelayMs, "delivery delay");
        requirePositive(retryDelayMs, "retry delay");
        requirePositive(completionDelayMs, "completion delay");
        requirePositive(staleRecoveryDelayMs, "stale recovery delay");
        if (emailProvider == null) {
            throw new IllegalStateException("newsletter email provider가 필요합니다.");
        }
        if (containsLineBreak(fromName)) {
            throw new IllegalStateException("newsletter from name에 줄바꿈을 사용할 수 없습니다.");
        }
        if (testRecipientAllowlist == null) {
            throw new IllegalStateException("newsletter test recipient allowlist는 null일 수 없습니다.");
        }
        testRecipientAllowlist.forEach(recipient -> {
            if (recipient == null || recipient.isBlank() || containsLineBreak(recipient)) {
                throw new IllegalStateException("newsletter test recipient allowlist가 올바르지 않습니다.");
            }
        });
        if (sendingEnabled) {
            if (fromEmail == null || fromEmail.isBlank() || containsLineBreak(fromEmail)) {
                throw new IllegalStateException("NEWSLETTER_FROM_EMAIL이 필요합니다.");
            }
            validateHttpsBaseUrl(publicBaseUrl, "NEWSLETTER_PUBLIC_BASE_URL");
            validateHttpsBaseUrl(apiBaseUrl, "NEWSLETTER_API_BASE_URL");
            if (emailProvider == NewsletterEmailProvider.SES
                    && (awsRegion == null || awsRegion.isBlank())) {
                throw new IllegalStateException("AWS_REGION이 필요합니다.");
            }
        }
    }

    private void validateHttpsBaseUrl(String value, String name) {
        try {
            URI uri = new URI(value == null ? "" : value);
            if (!uri.isAbsolute()
                    || !"https".equalsIgnoreCase(uri.getScheme())
                    || uri.getHost() == null
                    || uri.getQuery() != null
                    || uri.getFragment() != null) {
                throw new IllegalStateException(name + "은 query/fragment 없는 HTTPS 절대 URL이어야 합니다.");
            }
        } catch (URISyntaxException exception) {
            throw new IllegalStateException(name + " 형식이 올바르지 않습니다.");
        }
    }

    private boolean containsLineBreak(String value) {
        return value != null && (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0);
    }

    private void requireRange(long value, long minimum, long maximum, String name) {
        if (value < minimum || value > maximum) {
            throw new IllegalStateException(name + " 범위가 올바르지 않습니다.");
        }
    }

    private void requirePositive(long value, String name) {
        if (value <= 0) {
            throw new IllegalStateException(name + "은 양수여야 합니다.");
        }
    }
}
