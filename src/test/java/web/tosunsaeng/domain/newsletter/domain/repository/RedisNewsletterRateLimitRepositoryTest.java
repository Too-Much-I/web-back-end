package web.tosunsaeng.domain.newsletter.domain.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import web.tosunsaeng.domain.newsletter.config.NewsletterRateLimitProperties;
import web.tosunsaeng.domain.newsletter.domain.enums.NewsletterRateLimitScope;
import web.tosunsaeng.domain.newsletter.domain.policy.NewsletterRateLimitKeyFactory;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisNewsletterRateLimitRepositoryTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private RedisScript<List> script;

    private RedisNewsletterRateLimitRepository repository;
    private NewsletterRateLimitKeyFactory.RateLimitKeys keys;

    @BeforeEach
    void setUp() {
        NewsletterRateLimitProperties properties = new NewsletterRateLimitProperties();
        properties.setSecret("test-only-newsletter-rate-secret-at-least-32-bytes");
        properties.afterPropertiesSet();
        repository = new RedisNewsletterRateLimitRepository(
                redisTemplate, script, properties);
        keys = new NewsletterRateLimitKeyFactory().create("safe_ip_hash");
    }

    @Test
    void executesAtomicTwoWindowContractWithApprovedLimits() {
        stub(List.of("ALLOWED"));

        NewsletterRateLimitRepository.AdmissionResult result = repository.admit(keys);

        assertThat(result.allowed()).isTrue();
        verify(redisTemplate).execute(
                script,
                keys.asList(),
                "10", "600",
                "30", "86400");
    }

    @Test
    void decodesAllBlockersForApplicationMaximumTtlSelection() {
        stub(List.of("DENIED", "IP_MEDIUM", 25L, "IP_DAILY", "600"));

        NewsletterRateLimitRepository.AdmissionResult result = repository.admit(keys);

        assertThat(result.blockers()).containsExactly(
                new NewsletterRateLimitRepository.Blocker(
                        NewsletterRateLimitScope.IP_MEDIUM, 25),
                new NewsletterRateLimitRepository.Blocker(
                        NewsletterRateLimitScope.IP_DAILY, 600));
    }

    @Test
    void rejectsMissingMalformedAndNegativeScriptResults() {
        stub(null);
        assertThatThrownBy(() -> repository.admit(keys))
                .isInstanceOf(IllegalStateException.class);

        stub(List.of("DENIED", "UNKNOWN", "10"));
        assertThatThrownBy(() -> repository.admit(keys))
                .isInstanceOf(IllegalStateException.class);

        stub(List.of("DENIED", "IP_DAILY", "-1"));
        assertThatThrownBy(() -> repository.admit(keys))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void luaContractChecksBeforeMutationAndSetsTtlOnlyOnCreation() throws Exception {
        String lua = resource("redis/newsletter-subscribe-rate-limit.lua");

        assertThat(lua)
                .contains("if #blockers > 0 then")
                .contains("redis.call('INCR', KEYS[index])")
                .contains("if count == 1 then")
                .contains("redis.call('EXPIRE', KEYS[index], windows[index])")
                .containsOnlyOnce("redis.call('EXPIRE', KEYS[index], windows[index])")
                .contains("if ttl < 1 then")
                .contains("NEWSLETTER_COUNTER_TTL_INVALID")
                .doesNotContain("DECR")
                .doesNotContain("email")
                .doesNotContain("X-Forwarded-For");
        assertThat(lua.indexOf("if #blockers > 0 then"))
                .isLessThan(lua.indexOf("redis.call('INCR', KEYS[index])"));
    }

    @SuppressWarnings("unchecked")
    private void stub(List<?> result) {
        when(redisTemplate.execute(
                eq(script),
                anyList(),
                any(), any(), any(), any()))
                .thenReturn((List) result);
    }

    private String resource(String path) throws Exception {
        try (var input = new ClassPathResource(path).getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
