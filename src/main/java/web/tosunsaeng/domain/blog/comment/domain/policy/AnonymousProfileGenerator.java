package web.tosunsaeng.domain.blog.comment.domain.policy;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;

@Component
public class AnonymousProfileGenerator {

    static final int AVATAR_SEED_BYTES = 16;
    static final int MAX_REGENERATION_ATTEMPTS = 32;
    static final List<String> ADJECTIVES = List.of(
            "차분한",
            "꼼꼼한",
            "명랑한",
            "용감한",
            "따뜻한");

    private final SecureRandom secureRandom;
    private final AnonymousAvatarImageCatalog avatarCatalog;
    private final AvatarImageUrlResolver avatarImageUrlResolver;

    public AnonymousProfileGenerator(
            SecureRandom secureRandom,
            AnonymousAvatarImageCatalog avatarCatalog,
            AvatarImageUrlResolver avatarImageUrlResolver) {
        if (ADJECTIVES.size() < 2
                || ADJECTIVES.stream().anyMatch(value -> value == null || value.isBlank())
                || ADJECTIVES.stream().distinct().count() != ADJECTIVES.size()) {
            throw new IllegalStateException("익명 프로필 형용사 후보군이 유효하지 않습니다.");
        }
        this.secureRandom = secureRandom;
        this.avatarCatalog = avatarCatalog;
        this.avatarImageUrlResolver = avatarImageUrlResolver;
    }

    public GeneratedProfile generate() {
        return generateCandidate();
    }

    public GeneratedProfile regenerate(
            String currentNickname,
            String currentAvatarSeed,
            String currentAvatarImageKey) {
        String currentAdjective = extractAdjective(currentNickname);
        for (int attempt = 0; attempt < MAX_REGENERATION_ATTEMPTS; attempt++) {
            GeneratedProfile candidate = generateCandidate();
            if (!candidate.adjective().equals(currentAdjective)
                    && !candidate.avatarImageKey().equals(currentAvatarImageKey)
                    && !candidate.avatarSeed().equals(currentAvatarSeed)) {
                return candidate;
            }
        }
        throw new IllegalStateException("새 익명 프로필을 생성할 수 없습니다.");
    }

    private GeneratedProfile generateCandidate() {
        String adjective = ADJECTIVES.get(secureRandom.nextInt(ADJECTIVES.size()));
        List<AnonymousAvatarImageCatalog.AvatarOption> options = avatarCatalog.options();
        AnonymousAvatarImageCatalog.AvatarOption option =
                options.get(secureRandom.nextInt(options.size()));
        byte[] seedBytes = new byte[AVATAR_SEED_BYTES];
        secureRandom.nextBytes(seedBytes);
        String avatarSeed = Base64.getUrlEncoder().withoutPadding().encodeToString(seedBytes);
        String nickname = adjective + " " + option.noun();
        String avatarImageUrl = avatarImageUrlResolver.resolve(option.imageKey());
        return new GeneratedProfile(
                adjective,
                nickname,
                avatarSeed,
                option.imageKey(),
                avatarImageUrl);
    }

    private String extractAdjective(String nickname) {
        if (nickname == null) {
            return "";
        }
        int separatorIndex = nickname.indexOf(' ');
        return separatorIndex > 0 ? nickname.substring(0, separatorIndex) : nickname;
    }

    public record GeneratedProfile(
            String adjective,
            String nickname,
            String avatarSeed,
            String avatarImageKey,
            String avatarImageUrl) {
    }
}
