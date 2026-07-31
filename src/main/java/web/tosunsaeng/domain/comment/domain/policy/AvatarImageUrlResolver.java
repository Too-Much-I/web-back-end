package web.tosunsaeng.domain.comment.domain.policy;

import org.springframework.web.util.UriUtils;
import web.tosunsaeng.domain.comment.config.AnonymousProfileProperties;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Collectors;

public class AvatarImageUrlResolver {

    private final String normalizedBaseUrl;

    public AvatarImageUrlResolver(String baseUrl) {
        AnonymousProfileProperties.validateBaseUrl(baseUrl);
        this.normalizedBaseUrl = removeTrailingSlashes(baseUrl);
    }

    public String resolve(String imageKey) {
        if (!AnonymousProfileProperties.isValidImageKey(imageKey)) {
            throw new IllegalArgumentException("유효하지 않은 avatar imageKey입니다.");
        }
        String encodedKey = Arrays.stream(imageKey.split("/", -1))
                .map(segment -> UriUtils.encodePathSegment(segment, StandardCharsets.UTF_8))
                .collect(Collectors.joining("/"));
        return normalizedBaseUrl + "/" + encodedKey;
    }

    private String removeTrailingSlashes(String value) {
        int endIndex = value.length();
        while (endIndex > 0 && value.charAt(endIndex - 1) == '/') {
            endIndex--;
        }
        return value.substring(0, endIndex);
    }
}
