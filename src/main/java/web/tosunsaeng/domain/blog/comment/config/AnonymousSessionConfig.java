package web.tosunsaeng.domain.blog.comment.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
        AnonymousSessionProperties.class,
        AnonymousProfileProperties.class
})
public class AnonymousSessionConfig {
}
