package web.tosunsaeng.domain.newsletter.domain.enums;

public enum NewsletterFailureType {
    TRANSIENT_PROVIDER,
    PERMANENT_REQUEST,
    INVALID_EMAIL,
    AUTH_CONFIGURATION,
    APPLICATION_ERROR,
    PROVIDER_RESULT_UNKNOWN
}
