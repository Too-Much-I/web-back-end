package web.tosunsaeng.domain.newsletter.domain.policy;

import io.lettuce.core.RedisCommandTimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;

import static org.assertj.core.api.Assertions.assertThat;

class NewsletterRedisFailureClassifierTest {

    private final NewsletterRedisFailureClassifier classifier =
            new NewsletterRedisFailureClassifier();

    @Test
    void classifiesConnectionAndTimeoutFailuresThroughCauseChain() {
        assertThat(classifier.isConnectivityFailure(
                new RedisConnectionFailureException("unavailable"))).isTrue();
        assertThat(classifier.isConnectivityFailure(
                new RuntimeException(new RedisCommandTimeoutException("timeout"))))
                .isTrue();
    }

    @Test
    void doesNotClassifyApplicationAndLuaContractErrorsAsConnectivity() {
        assertThat(classifier.isConnectivityFailure(new NullPointerException())).isFalse();
        assertThat(classifier.isConnectivityFailure(
                new IllegalStateException("invalid script result"))).isFalse();
    }
}
