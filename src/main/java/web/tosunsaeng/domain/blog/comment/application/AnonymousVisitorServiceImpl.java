package web.tosunsaeng.domain.blog.comment.application;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import web.tosunsaeng.domain.blog.comment.domain.entity.AnonymousVisitor;
import web.tosunsaeng.domain.blog.comment.domain.policy.AnonymousProfileGenerator;
import web.tosunsaeng.domain.blog.comment.domain.policy.AnonymousTokenManager;
import web.tosunsaeng.domain.blog.comment.domain.repository.AnonymousVisitorRepository;
import web.tosunsaeng.domain.blog.comment.exception.BlogCommentException;
import web.tosunsaeng.global.error.code.status.ErrorStatus;

import java.time.Instant;
import java.util.Optional;

@Service
public class AnonymousVisitorServiceImpl implements AnonymousVisitorService {

    static final int MAX_TOKEN_COLLISION_ATTEMPTS = 5;

    private final AnonymousVisitorRepository anonymousVisitorRepository;
    private final AnonymousTokenManager tokenManager;
    private final AnonymousProfileGenerator profileGenerator;

    public AnonymousVisitorServiceImpl(
            AnonymousVisitorRepository anonymousVisitorRepository,
            AnonymousTokenManager tokenManager,
            AnonymousProfileGenerator profileGenerator) {
        this.anonymousVisitorRepository = anonymousVisitorRepository;
        this.tokenManager = tokenManager;
        this.profileGenerator = profileGenerator;
    }

    @Override
    public VisitorResolution resolve(String rawToken, Instant now) {
        Optional<AnonymousVisitor> existingVisitor = findExistingVisitor(rawToken);
        if (existingVisitor.isPresent()) {
            AnonymousVisitor visitor = existingVisitor.get();
            visitor.touch(now);
            return new VisitorResolution(anonymousVisitorRepository.save(visitor), null);
        }
        return createVisitor(now);
    }

    @Override
    public VisitorResolution regenerate(String rawToken, Instant now) {
        Optional<AnonymousVisitor> existingVisitor = findExistingVisitor(rawToken);
        if (existingVisitor.isEmpty()) {
            return createVisitor(now);
        }

        AnonymousVisitor visitor = existingVisitor.get();
        AnonymousProfileGenerator.GeneratedProfile profile;
        try {
            profile = profileGenerator.regenerate(
                    visitor.getNickname(),
                    visitor.getAvatarSeed(),
                    visitor.getAvatarImageKey());
        } catch (IllegalStateException exception) {
            throw new BlogCommentException(ErrorStatus._INTERNAL_SERVER_ERROR);
        }
        visitor.updateProfile(
                profile.nickname(),
                profile.avatarSeed(),
                profile.avatarImageKey(),
                now);
        return new VisitorResolution(anonymousVisitorRepository.save(visitor), null);
    }

    private Optional<AnonymousVisitor> findExistingVisitor(String rawToken) {
        if (!tokenManager.isValidRawToken(rawToken)) {
            return Optional.empty();
        }
        String tokenHash = tokenManager.hash(rawToken);
        return anonymousVisitorRepository.findByTokenHash(tokenHash);
    }

    private VisitorResolution createVisitor(Instant now) {
        for (int attempt = 0; attempt < MAX_TOKEN_COLLISION_ATTEMPTS; attempt++) {
            AnonymousTokenManager.AnonymousToken token = tokenManager.createToken();
            AnonymousProfileGenerator.GeneratedProfile profile;
            try {
                profile = profileGenerator.generate();
            } catch (IllegalStateException exception) {
                throw new BlogCommentException(ErrorStatus._INTERNAL_SERVER_ERROR);
            }
            AnonymousVisitor visitor = AnonymousVisitor.builder()
                    .tokenHash(token.tokenHash())
                    .nickname(profile.nickname())
                    .avatarSeed(profile.avatarSeed())
                    .avatarImageKey(profile.avatarImageKey())
                    .createdAt(now)
                    .lastSeenAt(now)
                    .build();
            try {
                AnonymousVisitor savedVisitor = anonymousVisitorRepository.save(visitor);
                return new VisitorResolution(savedVisitor, token.rawToken());
            } catch (DuplicateKeyException exception) {
                // tokenHash unique 충돌 시 새 token과 profile로 제한 재시도한다.
            }
        }
        throw new BlogCommentException(ErrorStatus._INTERNAL_SERVER_ERROR);
    }
}
