package web.tosunsaeng.domain.newsletter.domain.sender;

public interface NewsletterEmailSender {

    NewsletterEmailSendResult send(NewsletterEmailMessage message);
}
