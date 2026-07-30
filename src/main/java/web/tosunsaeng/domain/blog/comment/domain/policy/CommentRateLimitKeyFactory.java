package web.tosunsaeng.domain.blog.comment.domain.policy;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

@Component
public class CommentRateLimitKeyFactory {

    static final String PREFIX = "blog:comment:";
    private static final Pattern SAFE_SEGMENT = Pattern.compile("^[A-Za-z0-9_-]+$");

    public RateLimitKeys create(
            String visitorHash,
            String ipHash,
            String postId,
            String contentHash) {
        requireSafeSegment(visitorHash, "visitorHash");
        requireSafeSegment(ipHash, "ipHash");
        requireSafeSegment(postId, "postId");
        requireSafeSegment(contentHash, "contentHash");

        return new RateLimitKeys(
                PREFIX + "rate:visitor:short:" + visitorHash,
                PREFIX + "rate:visitor:medium:" + visitorHash,
                PREFIX + "rate:visitor:daily:" + visitorHash,
                PREFIX + "rate:ip:medium:" + ipHash,
                PREFIX + "rate:ip:daily:" + ipHash,
                PREFIX + "duplicate:" + visitorHash + ":" + postId + ":" + contentHash);
    }

    private void requireSafeSegment(String value, String name) {
        if (value == null || !SAFE_SEGMENT.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " 형식이 올바르지 않습니다.");
        }
    }

    public record RateLimitKeys(
            String visitorShort,
            String visitorMedium,
            String visitorDaily,
            String ipMedium,
            String ipDaily,
            String duplicate) {

        public List<String> asList() {
            return List.of(
                    visitorShort,
                    visitorMedium,
                    visitorDaily,
                    ipMedium,
                    ipDaily,
                    duplicate);
        }
    }
}
