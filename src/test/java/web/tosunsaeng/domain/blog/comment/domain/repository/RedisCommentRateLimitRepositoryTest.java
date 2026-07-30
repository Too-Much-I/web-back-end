package web.tosunsaeng.domain.blog.comment.domain.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import web.tosunsaeng.domain.blog.comment.config.BlogCommentAbuseProperties;
import web.tosunsaeng.domain.blog.comment.domain.enums.CommentLimitScope;
import web.tosunsaeng.domain.blog.comment.domain.policy.CommentRateLimitKeyFactory;

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
class RedisCommentRateLimitRepositoryTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private RedisScript<List> admissionScript;

    @Mock
    private RedisScript<Long> releaseScript;

    private RedisCommentRateLimitRepository repository;
    private CommentRateLimitKeyFactory.RateLimitKeys keys;

    @BeforeEach
    void setUp() {
        BlogCommentAbuseProperties properties = new BlogCommentAbuseProperties();
        properties.setSecret("test-only-comment-rate-limit-secret-at-least-32-bytes");
        properties.afterPropertiesSet();
        repository = new RedisCommentRateLimitRepository(
                redisTemplate,
                admissionScript,
                releaseScript,
                properties);
        keys = new CommentRateLimitKeyFactory().create(
                "visitor_hash",
                "ip_hash",
                "post-id",
                "content_hash");
    }

    @Test
    void executesOneAdmissionScriptWithApprovedKeyAndWindowContract() {
        stubAdmission(List.of("ALLOWED"));

        CommentRateLimitRepository.AdmissionResult result =
                repository.admit(keys, "reservation-owner");

        assertThat(result.allowed()).isTrue();
        assertThat(result.blockers()).isEmpty();
        verify(redisTemplate).execute(
                admissionScript,
                keys.asList(),
                "1", "10",
                "5", "600",
                "20", "86400",
                "10", "600",
                "50", "86400",
                "600",
                "reservation-owner");
    }

    @Test
    void decodesEveryReturnedBlockerWithoutChoosingPolicyInRepository() {
        stubAdmission(List.of(
                "DENIED",
                "VISITOR_SHORT", 7L,
                "DUPLICATE", "590",
                "IP_DAILY", 120L));

        CommentRateLimitRepository.AdmissionResult result =
                repository.admit(keys, "reservation-owner");

        assertThat(result.allowed()).isFalse();
        assertThat(result.blockers()).containsExactly(
                new CommentRateLimitRepository.Blocker(
                        CommentLimitScope.VISITOR_SHORT, 7),
                new CommentRateLimitRepository.Blocker(
                        CommentLimitScope.DUPLICATE, 590),
                new CommentRateLimitRepository.Blocker(
                        CommentLimitScope.IP_DAILY, 120));
    }

    @Test
    void rejectsMissingMalformedOrNegativeScriptResults() {
        stubAdmission(null);
        assertThatThrownBy(() -> repository.admit(keys, "owner"))
                .isInstanceOf(IllegalStateException.class);

        stubAdmission(List.of("DENIED", "UNKNOWN", "10"));
        assertThatThrownBy(() -> repository.admit(keys, "owner"))
                .isInstanceOf(IllegalStateException.class);

        stubAdmission(List.of("DENIED", "DUPLICATE", "-1"));
        assertThatThrownBy(() -> repository.admit(keys, "owner"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void duplicateCleanupUsesOwnerCheckedLuaResult() {
        when(redisTemplate.execute(releaseScript, List.of(keys.duplicate()), "owner"))
                .thenReturn(1L, 0L);

        assertThat(repository.releaseDuplicate(keys.duplicate(), "owner")).isTrue();
        assertThat(repository.releaseDuplicate(keys.duplicate(), "owner")).isFalse();

        verify(redisTemplate, org.mockito.Mockito.times(2)).execute(
                releaseScript,
                List.of(keys.duplicate()),
                "owner");
    }

    @Test
    void luaResourcesEncodeAtomicNoPartialMutationAndOwnerCleanupContract()
            throws Exception {
        String admission = resource("redis/blog-comment-admission.lua");
        String release = resource("redis/blog-comment-duplicate-release.lua");

        assertThat(admission)
                .contains("if #blockers > 0 then")
                .contains("redis.call('INCR', KEYS[index])")
                .contains("if count == 1 then")
                .contains("redis.call('EXPIRE', KEYS[index], windows[index])")
                .containsOnlyOnce("redis.call('EXPIRE', KEYS[index], windows[index])")
                .contains("if ttl < 1 then")
                .contains("BLOG_COMMENT_COUNTER_TTL_INVALID")
                .contains("'NX'")
                .contains("ARGV[12]")
                .doesNotContain("DECR");
        assertThat(admission.indexOf("if #blockers > 0 then"))
                .isLessThan(admission.indexOf("redis.call('INCR', KEYS[index])"));
        assertThat(release)
                .contains("current_owner == ARGV[1]")
                .contains("redis.call('DEL', KEYS[1])");
    }

    @SuppressWarnings("unchecked")
    private void stubAdmission(List<?> result) {
        when(redisTemplate.execute(
                eq(admissionScript),
                anyList(),
                any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any()))
                .thenReturn((List) result);
    }

    private String resource(String path) throws Exception {
        try (var input = new ClassPathResource(path).getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
