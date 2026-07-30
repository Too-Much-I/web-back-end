package web.tosunsaeng.domain.blog.comment.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import web.tosunsaeng.domain.blog.comment.domain.entity.AnonymousVisitor;
import web.tosunsaeng.domain.blog.comment.domain.entity.BlogComment;
import web.tosunsaeng.domain.blog.comment.domain.enums.CommentStatus;
import web.tosunsaeng.domain.blog.comment.domain.policy.AnonymousProfileGenerator;
import web.tosunsaeng.domain.blog.comment.domain.policy.AnonymousTokenManager;
import web.tosunsaeng.domain.blog.comment.domain.repository.AnonymousVisitorRepository;
import web.tosunsaeng.domain.blog.comment.exception.BlogCommentException;
import web.tosunsaeng.global.error.code.status.ErrorStatus;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnonymousVisitorServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-07-30T02:00:00Z");
    private static final String RAW_TOKEN = "raw-token-value-not-written-to-document";
    private static final String TOKEN_HASH = "hmac-token-hash";

    @Mock
    private AnonymousVisitorRepository anonymousVisitorRepository;

    @Mock
    private AnonymousTokenManager tokenManager;

    @Mock
    private AnonymousProfileGenerator profileGenerator;

    private AnonymousVisitorServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AnonymousVisitorServiceImpl(
                anonymousVisitorRepository,
                tokenManager,
                profileGenerator);
    }

    @Test
    void missingCookieCreatesVisitorWithHashAndNewCookieToken() {
        stubNewVisitor(RAW_TOKEN, TOKEN_HASH, generatedProfile("차분한", "수달", "old-seed"));
        when(anonymousVisitorRepository.save(any(AnonymousVisitor.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AnonymousVisitorService.VisitorResolution result = service.resolve(null, NOW);

        ArgumentCaptor<AnonymousVisitor> visitorCaptor =
                ArgumentCaptor.forClass(AnonymousVisitor.class);
        verify(anonymousVisitorRepository).save(visitorCaptor.capture());
        AnonymousVisitor saved = visitorCaptor.getValue();
        assertThat(saved.getTokenHash()).isEqualTo(TOKEN_HASH).isNotEqualTo(RAW_TOKEN);
        assertThat(saved.getNickname()).isEqualTo("차분한 수달");
        assertThat(saved.getAvatarSeed()).isEqualTo("old-seed");
        assertThat(saved.getAvatarImageKey())
                .isEqualTo("character-image/example-otter-v1.webp");
        assertThat(saved.getCreatedAt()).isEqualTo(NOW);
        assertThat(saved.getLastSeenAt()).isEqualTo(NOW);
        assertThat(result.rawTokenToSet()).isEqualTo(RAW_TOKEN);
        assertThat(result.hasNewCookie()).isTrue();
        assertThat(result.visitor()).isSameAs(saved);
    }

    @Test
    void prepareNewVisitorDoesNotPersistBeforeAdmission() {
        stubNewVisitor(RAW_TOKEN, TOKEN_HASH, generatedProfile("차분한", "수달", "seed"));

        AnonymousVisitorService.PreparedVisitor prepared = service.prepare(null, NOW);

        assertThat(prepared.newVisitor()).isTrue();
        assertThat(prepared.rawTokenToSet()).isEqualTo(RAW_TOKEN);
        assertThat(prepared.visitor().getTokenHash()).isEqualTo(TOKEN_HASH);
        verify(anonymousVisitorRepository, never()).save(any());
    }

    @Test
    void prepareExistingVisitorDoesNotTouchUntilCommit() {
        Instant previousSeenAt = NOW.minusSeconds(60);
        AnonymousVisitor existing = visitor(
                "visitor-1",
                TOKEN_HASH,
                "차분한 수달",
                "seed",
                "character-image/example-otter-v1.webp",
                previousSeenAt);
        when(tokenManager.isValidRawToken(RAW_TOKEN)).thenReturn(true);
        when(tokenManager.hash(RAW_TOKEN)).thenReturn(TOKEN_HASH);
        when(anonymousVisitorRepository.findByTokenHash(TOKEN_HASH))
                .thenReturn(Optional.of(existing));
        when(anonymousVisitorRepository.save(existing)).thenReturn(existing);

        AnonymousVisitorService.PreparedVisitor prepared = service.prepare(RAW_TOKEN, NOW);

        assertThat(prepared.newVisitor()).isFalse();
        assertThat(existing.getLastSeenAt()).isEqualTo(previousSeenAt);
        verify(anonymousVisitorRepository, never()).save(any());

        service.commit(prepared, NOW);

        assertThat(existing.getLastSeenAt()).isEqualTo(NOW);
        verify(anonymousVisitorRepository).save(existing);
    }

    @Test
    void sameValidCookieResolvesSameVisitorAndTouchesLastSeen() {
        AnonymousVisitor existing = visitor(
                "visitor-1",
                TOKEN_HASH,
                "차분한 수달",
                "old-seed",
                "character-image/example-otter-v1.webp",
                NOW.minusSeconds(60));
        when(tokenManager.isValidRawToken(RAW_TOKEN)).thenReturn(true);
        when(tokenManager.hash(RAW_TOKEN)).thenReturn(TOKEN_HASH);
        when(anonymousVisitorRepository.findByTokenHash(TOKEN_HASH))
                .thenReturn(Optional.of(existing));
        when(anonymousVisitorRepository.save(existing)).thenReturn(existing);

        AnonymousVisitorService.VisitorResolution result = service.resolve(RAW_TOKEN, NOW);

        assertThat(result.visitor()).isSameAs(existing);
        assertThat(result.visitor().getId()).isEqualTo("visitor-1");
        assertThat(result.visitor().getNickname()).isEqualTo("차분한 수달");
        assertThat(result.visitor().getLastSeenAt()).isEqualTo(NOW);
        assertThat(result.rawTokenToSet()).isNull();
        assertThat(result.hasNewCookie()).isFalse();
        verify(profileGenerator, never()).generate();
        verify(tokenManager, never()).createToken();
    }

    @Test
    void invalidCookieIsReplacedWithoutHashLookup() {
        when(tokenManager.isValidRawToken("malformed-cookie")).thenReturn(false);
        stubNewVisitor(RAW_TOKEN, TOKEN_HASH, generatedProfile("차분한", "수달", "seed"));
        when(anonymousVisitorRepository.save(any(AnonymousVisitor.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AnonymousVisitorService.VisitorResolution result =
                service.resolve("malformed-cookie", NOW);

        assertThat(result.rawTokenToSet()).isEqualTo(RAW_TOKEN);
        verify(tokenManager, never()).hash("malformed-cookie");
        verify(anonymousVisitorRepository, never()).findByTokenHash(any());
    }

    @Test
    void validCookieWithoutStoredVisitorIsReplaced() {
        when(tokenManager.isValidRawToken("valid-but-missing")).thenReturn(true);
        when(tokenManager.hash("valid-but-missing")).thenReturn("missing-hash");
        when(anonymousVisitorRepository.findByTokenHash("missing-hash"))
                .thenReturn(Optional.empty());
        stubNewVisitor(RAW_TOKEN, TOKEN_HASH, generatedProfile("차분한", "수달", "seed"));
        when(anonymousVisitorRepository.save(any(AnonymousVisitor.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AnonymousVisitorService.VisitorResolution result =
                service.resolve("valid-but-missing", NOW);

        assertThat(result.rawTokenToSet()).isEqualTo(RAW_TOKEN);
        verify(anonymousVisitorRepository).findByTokenHash("missing-hash");
    }

    @Test
    void regenerationUpdatesAllProfileFieldsWithoutChangingVisitorIdentity() {
        AnonymousVisitor existing = visitor(
                "visitor-1",
                TOKEN_HASH,
                "차분한 수달",
                "old-seed",
                "character-image/example-otter-v1.webp",
                NOW.minusSeconds(60));
        BlogComment existingCommentSnapshot = BlogComment.builder()
                .id("comment-1")
                .postId("post-1")
                .anonymousVisitorId("visitor-1")
                .nickname(existing.getNickname())
                .avatarSeed(existing.getAvatarSeed())
                .avatarImageKey(existing.getAvatarImageKey())
                .content("정상 댓글")
                .status(CommentStatus.VISIBLE)
                .createdAt(NOW.minusSeconds(30))
                .updatedAt(NOW.minusSeconds(30))
                .build();
        when(tokenManager.isValidRawToken(RAW_TOKEN)).thenReturn(true);
        when(tokenManager.hash(RAW_TOKEN)).thenReturn(TOKEN_HASH);
        when(anonymousVisitorRepository.findByTokenHash(TOKEN_HASH))
                .thenReturn(Optional.of(existing));
        when(profileGenerator.regenerate(
                "차분한 수달",
                "old-seed",
                "character-image/example-otter-v1.webp"))
                .thenReturn(generatedProfile("명랑한", "펭귄", "new-seed"));
        when(anonymousVisitorRepository.save(existing)).thenReturn(existing);

        AnonymousVisitorService.VisitorResolution result = service.regenerate(RAW_TOKEN, NOW);

        assertThat(result.visitor().getId()).isEqualTo("visitor-1");
        assertThat(result.visitor().getNickname()).isEqualTo("명랑한 펭귄");
        assertThat(result.visitor().getAvatarSeed()).isEqualTo("new-seed");
        assertThat(result.visitor().getAvatarImageKey())
                .isEqualTo("character-image/example-penguin-v1.webp");
        assertThat(result.visitor().getLastSeenAt()).isEqualTo(NOW);
        assertThat(result.rawTokenToSet()).isNull();
        assertThat(existingCommentSnapshot.getNickname()).isEqualTo("차분한 수달");
        assertThat(existingCommentSnapshot.getAvatarSeed()).isEqualTo("old-seed");
        assertThat(existingCommentSnapshot.getAvatarImageKey())
                .isEqualTo("character-image/example-otter-v1.webp");
    }

    @Test
    void regenerationWithoutValidCookieCreatesVisitorAndCookie() {
        stubNewVisitor(RAW_TOKEN, TOKEN_HASH, generatedProfile("명랑한", "펭귄", "seed"));
        when(anonymousVisitorRepository.save(any(AnonymousVisitor.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AnonymousVisitorService.VisitorResolution result = service.regenerate(null, NOW);

        assertThat(result.rawTokenToSet()).isEqualTo(RAW_TOKEN);
        assertThat(result.visitor().getNickname()).isEqualTo("명랑한 펭귄");
        verify(profileGenerator, never()).regenerate(any(), any(), any());
    }

    @Test
    void retriesUniqueTokenCollisionWithFreshTokenAndProfile() {
        AnonymousTokenManager.AnonymousToken firstToken =
                new AnonymousTokenManager.AnonymousToken("raw-1", "hash-1");
        AnonymousTokenManager.AnonymousToken secondToken =
                new AnonymousTokenManager.AnonymousToken("raw-2", "hash-2");
        when(tokenManager.createToken()).thenReturn(firstToken, secondToken);
        when(profileGenerator.generate()).thenReturn(
                generatedProfile("차분한", "수달", "seed-1"),
                generatedProfile("명랑한", "펭귄", "seed-2"));
        when(anonymousVisitorRepository.save(any(AnonymousVisitor.class)))
                .thenThrow(new DuplicateKeyException("token hash collision"))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AnonymousVisitorService.VisitorResolution result = service.resolve(null, NOW);

        assertThat(result.rawTokenToSet()).isEqualTo("raw-2");
        assertThat(result.visitor().getTokenHash()).isEqualTo("hash-2");
        assertThat(result.visitor().getNickname()).isEqualTo("명랑한 펭귄");
        verify(anonymousVisitorRepository, times(2)).save(any(AnonymousVisitor.class));
    }

    @Test
    void failsInternallyAfterBoundedTokenCollisionRetries() {
        when(tokenManager.createToken()).thenReturn(
                new AnonymousTokenManager.AnonymousToken("raw", "hash"));
        when(profileGenerator.generate())
                .thenReturn(generatedProfile("차분한", "수달", "seed"));
        when(anonymousVisitorRepository.save(any(AnonymousVisitor.class)))
                .thenThrow(new DuplicateKeyException("token hash collision"));

        assertInternalError(() -> service.resolve(null, NOW));

        verify(anonymousVisitorRepository, times(5)).save(any(AnonymousVisitor.class));
    }

    @Test
    void convertsProfileGenerationFailureToGenericInternalError() {
        when(tokenManager.createToken()).thenReturn(
                new AnonymousTokenManager.AnonymousToken("raw", "hash"));
        when(profileGenerator.generate()).thenThrow(new IllegalStateException("candidate failure"));

        assertInternalError(() -> service.resolve(null, NOW));

        verifyNoInteractions(anonymousVisitorRepository);
    }

    private void stubNewVisitor(
            String rawToken,
            String tokenHash,
            AnonymousProfileGenerator.GeneratedProfile profile) {
        when(tokenManager.createToken())
                .thenReturn(new AnonymousTokenManager.AnonymousToken(rawToken, tokenHash));
        when(profileGenerator.generate()).thenReturn(profile);
    }

    private AnonymousProfileGenerator.GeneratedProfile generatedProfile(
            String adjective,
            String noun,
            String seed) {
        String key = noun.equals("수달")
                ? "character-image/example-otter-v1.webp"
                : "character-image/example-penguin-v1.webp";
        return new AnonymousProfileGenerator.GeneratedProfile(
                adjective,
                adjective + " " + noun,
                seed,
                key,
                "https://cdn.example.test/" + key);
    }

    private AnonymousVisitor visitor(
            String id,
            String tokenHash,
            String nickname,
            String seed,
            String imageKey,
            Instant lastSeenAt) {
        return AnonymousVisitor.builder()
                .id(id)
                .tokenHash(tokenHash)
                .nickname(nickname)
                .avatarSeed(seed)
                .avatarImageKey(imageKey)
                .createdAt(NOW.minusSeconds(120))
                .lastSeenAt(lastSeenAt)
                .build();
    }

    private void assertInternalError(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(
                        BlogCommentException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(ErrorStatus._INTERNAL_SERVER_ERROR));
    }
}
