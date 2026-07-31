package web.tosunsaeng.domain.comment.api.support;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class ClientIpResolver {

    public String resolve(HttpServletRequest request) {
        String remoteAddress = Objects.requireNonNull(request, "request").getRemoteAddr();
        if (remoteAddress == null || remoteAddress.isBlank()) {
            throw new IllegalStateException("client remote address를 확인할 수 없습니다.");
        }
        return remoteAddress;
    }
}
