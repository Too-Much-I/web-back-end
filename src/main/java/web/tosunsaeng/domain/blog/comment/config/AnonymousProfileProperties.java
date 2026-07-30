package web.tosunsaeng.domain.blog.comment.config;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Getter
@Setter
@ConfigurationProperties(prefix = "blog.anonymous-profile")
public class AnonymousProfileProperties implements InitializingBean {

    private static final Pattern IMAGE_KEY_PATTERN =
            Pattern.compile("^character-image/[^/?#]+$");

    private String avatarBaseUrl = "";
    private List<AvatarOption> avatarOptions = new ArrayList<>();

    @Override
    public void afterPropertiesSet() {
        validateBaseUrl(avatarBaseUrl);
        validateAvatarOptions(avatarOptions);
    }

    public static void validateBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank() || !baseUrl.equals(baseUrl.strip())) {
            throw new IllegalStateException(
                    "BLOG_ANONYMOUS_AVATAR_BASE_URL은 비어 있지 않은 HTTPS URL이어야 합니다.");
        }

        URI uri;
        try {
            uri = URI.create(baseUrl);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "BLOG_ANONYMOUS_AVATAR_BASE_URL 형식이 올바르지 않습니다.", exception);
        }

        if (!"https".equalsIgnoreCase(uri.getScheme())
                || uri.getHost() == null
                || uri.getHost().isBlank()
                || uri.getRawUserInfo() != null
                || uri.getRawQuery() != null
                || uri.getRawFragment() != null) {
            throw new IllegalStateException(
                    "BLOG_ANONYMOUS_AVATAR_BASE_URL은 user-info, query, fragment가 없는 HTTPS URL이어야 합니다.");
        }
    }

    public static void validateAvatarOptions(List<AvatarOption> options) {
        if (options == null || options.size() < 2) {
            throw new IllegalStateException("avatar option은 두 개 이상이어야 합니다.");
        }

        Set<String> nouns = new HashSet<>();
        Set<String> imageKeys = new HashSet<>();
        for (AvatarOption option : options) {
            if (option == null) {
                throw new IllegalStateException("avatar option은 null일 수 없습니다.");
            }
            String noun = option.getNoun();
            String imageKey = option.getImageKey();
            if (noun == null || noun.isBlank() || !noun.equals(noun.strip())) {
                throw new IllegalStateException("avatar option noun은 비어 있을 수 없습니다.");
            }
            if (!isValidImageKey(imageKey)) {
                throw new IllegalStateException(
                        "avatar imageKey는 character-image/{filename} 상대 경로여야 합니다.");
            }
            if (!nouns.add(noun)) {
                throw new IllegalStateException("avatar option noun은 중복될 수 없습니다.");
            }
            if (!imageKeys.add(imageKey)) {
                throw new IllegalStateException("avatar option imageKey는 중복될 수 없습니다.");
            }
        }
    }

    public static boolean isValidImageKey(String imageKey) {
        if (imageKey == null
                || imageKey.isBlank()
                || !imageKey.equals(imageKey.strip())
                || imageKey.startsWith("/")
                || imageKey.contains("..")) {
            return false;
        }
        String lowerCaseKey = imageKey.toLowerCase(Locale.ROOT);
        return !lowerCaseKey.startsWith("http://")
                && !lowerCaseKey.startsWith("https://")
                && IMAGE_KEY_PATTERN.matcher(imageKey).matches();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AvatarOption {
        private String noun;
        private String imageKey;
    }
}
