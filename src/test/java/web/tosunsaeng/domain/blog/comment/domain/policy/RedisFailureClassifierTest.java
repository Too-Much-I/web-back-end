package web.tosunsaeng.domain.blog.comment.domain.policy;

import io.lettuce.core.RedisCommandTimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;

import static org.assertj.core.api.Assertions.assertThat;

class RedisFailureClassifierTest {

    private final RedisFailureClassifier classifier = new RedisFailureClassifier();

    @Test
    void classifiesConnectionAndTimeoutFailuresThroughCauseChain() {
        assertThat(classifier.isConnectivityFailure(
                new RedisConnectionFailureException("unavailable"))).isTrue();
        assertThat(classifier.isConnectivityFailure(
                new RuntimeException(new RedisCommandTimeoutException("timeout"))))
                .isTrue();
    }

    @Test
    void doesNotFailOpenForLuaContractOrApplicationErrors() {
        assertThat(classifier.isConnectivityFailure(
                new RedisSystemException("script error", new IllegalStateException())))
                .isFalse();
        assertThat(classifier.isConnectivityFailure(new NullPointerException()))
                .isFalse();
        assertThat(classifier.isConnectivityFailure(new IllegalStateException()))
                .isFalse();
    }
}
