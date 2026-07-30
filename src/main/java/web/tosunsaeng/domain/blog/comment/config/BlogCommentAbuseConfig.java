package web.tosunsaeng.domain.blog.comment.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.security.MessageDigest;
import java.util.List;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(BlogCommentAbuseProperties.class)
public class BlogCommentAbuseConfig {

    @Bean
    public InitializingBean blogCommentSecretSeparationValidator(
            BlogCommentAbuseProperties abuseProperties,
            AnonymousSessionProperties anonymousSessionProperties) {
        return () -> {
            if (abuseProperties.isEnabled()
                    && MessageDigest.isEqual(
                    abuseProperties.secretBytes(),
                    anonymousSessionProperties.tokenSecretBytes())) {
                throw new IllegalStateException(
                        "댓글 rate limit secret은 익명 token secret과 달라야 합니다.");
            }
        };
    }

    @Bean
    @Qualifier("blogCommentAdmissionScript")
    @SuppressWarnings({"rawtypes", "unchecked"})
    public RedisScript<List> blogCommentAdmissionScript() {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("redis/blog-comment-admission.lua"));
        script.setResultType(List.class);
        return script;
    }

    @Bean
    @Qualifier("blogCommentDuplicateReleaseScript")
    public RedisScript<Long> blogCommentDuplicateReleaseScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(
                "redis/blog-comment-duplicate-release.lua"));
        script.setResultType(Long.class);
        return script;
    }
}
