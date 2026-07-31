package web.tosunsaeng.domain.newsletter.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import web.tosunsaeng.domain.comment.config.AnonymousSessionProperties;
import web.tosunsaeng.domain.comment.config.BlogCommentAbuseProperties;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
        NewsletterUnsubscribeTokenProperties.class,
        NewsletterRateLimitProperties.class
})
public class NewsletterConfig {

    @Bean
    public NewsletterSecretSeparationValidator newsletterSecretSeparationValidator(
            NewsletterUnsubscribeTokenProperties tokenProperties,
            NewsletterRateLimitProperties rateLimitProperties,
            AnonymousSessionProperties anonymousProperties,
            BlogCommentAbuseProperties commentProperties) {
        return () -> {
            List<byte[]> unsubscribeSecrets = tokenProperties.allSecretBytes();
            List<byte[]> otherSecrets = new ArrayList<>();
            otherSecrets.add(anonymousProperties.tokenSecretBytes());
            if (commentProperties.secretBytes().length > 0) {
                otherSecrets.add(commentProperties.secretBytes());
            }
            if (rateLimitProperties.secretBytes().length > 0) {
                otherSecrets.add(rateLimitProperties.secretBytes());
            }
            for (byte[] unsubscribeSecret : unsubscribeSecrets) {
                requireDifferentFromAll(unsubscribeSecret, otherSecrets);
            }
            if (rateLimitProperties.secretBytes().length > 0) {
                requireDifferentFromAll(
                        rateLimitProperties.secretBytes(),
                        List.of(
                                anonymousProperties.tokenSecretBytes(),
                                commentProperties.secretBytes()));
            }
        };
    }

    @Bean
    @Qualifier("newsletterSubscribeRateLimitScript")
    @SuppressWarnings({"rawtypes", "unchecked"})
    public RedisScript<List> newsletterSubscribeRateLimitScript() {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("redis/newsletter-subscribe-rate-limit.lua"));
        script.setResultType(List.class);
        return script;
    }

    private void requireDifferentFromAll(byte[] candidate, List<byte[]> existingSecrets) {
        for (byte[] existing : existingSecrets) {
            if (existing.length > 0 && MessageDigest.isEqual(candidate, existing)) {
                throw new IllegalStateException("newsletter secret은 기존 기능 secret과 달라야 합니다.");
            }
        }
    }

    @FunctionalInterface
    public interface NewsletterSecretSeparationValidator extends InitializingBean {

        @Override
        void afterPropertiesSet();
    }
}
