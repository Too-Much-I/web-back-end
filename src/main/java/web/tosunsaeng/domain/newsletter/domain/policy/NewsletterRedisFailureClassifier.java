package web.tosunsaeng.domain.newsletter.domain.policy;

import io.lettuce.core.RedisCommandTimeoutException;
import io.lettuce.core.RedisConnectionException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.stereotype.Component;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

@Component
public class NewsletterRedisFailureClassifier {

    public boolean isConnectivityFailure(Throwable throwable) {
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable current = throwable;
        while (current != null && visited.add(current)) {
            if (current instanceof RedisConnectionFailureException
                    || current instanceof QueryTimeoutException
                    || current instanceof RedisConnectionException
                    || current instanceof RedisCommandTimeoutException
                    || current instanceof ConnectException
                    || current instanceof SocketTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
