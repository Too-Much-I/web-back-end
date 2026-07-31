package web.tosunsaeng.domain.comment.application;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import web.tosunsaeng.domain.comment.domain.entity.AnonymousVisitor;
import web.tosunsaeng.domain.comment.domain.policy.AnonymousProfileGenerator;
import web.tosunsaeng.domain.comment.domain.policy.AnonymousTokenManager;
import web.tosunsaeng.domain.comment.domain.repository.AnonymousVisitorRepository;
import web.tosunsaeng.domain.comment.exception.BlogCommentException;
import web.tosunsaeng.global.error.code.status.ErrorStatus;

import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AnonymousVisitorServiceImpl implements AnonymousVisitorService {

    static final int MAX_TOKEN_COLLISION_ATTEMPTS = 5;

    private final AnonymousVisitorRepository anonymousVisitorRepository;
    private final AnonymousTokenManager tokenManager;
    private final AnonymousProfileGenerator profileGenerator;

    @Override
    public PreparedVisitor prepare(String rawToken, Instant now) {
        Optional<AnonymousVisitor> existingVisitor = findExistingVisitor(rawToken);
        if (existingVisitor.isPresent()) {
            return new PreparedVisitor(existingVisitor.get(), null, false);
        }
        return prepareNewVisitor(now);
    }

    @Override
    public VisitorResolution commit(PreparedVisitor preparedVisitor, Instant now) {
        AnonymousVisitor visitor = preparedVisitor.visitor();
        if (!preparedVisitor.newVisitor()) {
            visitor.touch(now);
        }
        return new VisitorResolution(
                anonymousVisitorRepository.save(visitor),
                preparedVisitor.rawTokenToSet());
    }

    @Override
    public VisitorResolution resolve(String rawToken, Instant now) {
        PreparedVisitor preparedVisitor = prepare(rawToken, now);
        if (!preparedVisitor.newVisitor()) {
            return commit(preparedVisitor, now);
        }
        return commitNewVisitorWithRetry(preparedVisitor, now);
    }

    @Override
    public VisitorResolution regenerate(String rawToken, Instant now) {
        Optional<AnonymousVisitor> existingVisitor = findExistingVisitor(rawToken);
        if (existingVisitor.isEmpty()) {
            return resolve(rawToken, now);
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

    private VisitorResolution commitNewVisitorWithRetry(
            PreparedVisitor firstCandidate,
            Instant now) {
        PreparedVisitor candidate = firstCandidate;
        for (int attempt = 0; attempt < MAX_TOKEN_COLLISION_ATTEMPTS; attempt++) {
            try {
                return commit(candidate, now);
            } catch (DuplicateKeyException exception) {
                // tokenHash unique 충돌 시 새 token과 profile로 제한 재시도한다.
                if (attempt + 1 < MAX_TOKEN_COLLISION_ATTEMPTS) {
                    candidate = prepareNewVisitor(now);
                }
            }
        }
        throw new BlogCommentException(ErrorStatus._INTERNAL_SERVER_ERROR);
    }

    private PreparedVisitor prepareNewVisitor(Instant now) {
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
        return new PreparedVisitor(visitor, token.rawToken(), true);
    }
}
