package web.tosunsaeng.domain.blog.comment.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import web.tosunsaeng.domain.blog.comment.domain.policy.AnonymousAvatarImageCatalog;
import web.tosunsaeng.domain.blog.comment.domain.policy.AvatarImageUrlResolver;

import java.security.SecureRandom;

@Configuration(proxyBeanMethods = false)
public class AnonymousProfileConfig {

    @Bean
    public SecureRandom anonymousSecureRandom() {
        return new SecureRandom();
    }

    @Bean
    public AnonymousAvatarImageCatalog anonymousAvatarImageCatalog(
            AnonymousProfileProperties properties) {
        return new AnonymousAvatarImageCatalog(properties.getAvatarOptions());
    }

    @Bean
    public AvatarImageUrlResolver avatarImageUrlResolver(
            AnonymousProfileProperties properties) {
        return new AvatarImageUrlResolver(properties.getAvatarBaseUrl());
    }
}
