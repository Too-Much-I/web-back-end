package web.tosunsaeng.domain.comment.application;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import web.tosunsaeng.domain.comment.converter.BlogCommentConverter;
import web.tosunsaeng.domain.comment.domain.entity.AnonymousVisitor;
import web.tosunsaeng.domain.comment.domain.entity.BlogComment;
import web.tosunsaeng.domain.comment.domain.enums.CommentStatus;
import web.tosunsaeng.domain.comment.domain.policy.CommentValidator;
import web.tosunsaeng.domain.comment.domain.repository.BlogCommentRepository;
import web.tosunsaeng.domain.comment.dto.BlogCommentRequestDTO;
import web.tosunsaeng.domain.comment.dto.BlogCommentResponseDTO;
import web.tosunsaeng.domain.comment.exception.BlogCommentException;
import web.tosunsaeng.domain.comment.exception.CommentValidationException;
import web.tosunsaeng.domain.blog.domain.entity.BlogPost;
import web.tosunsaeng.domain.blog.domain.policy.BlogPostSlugPolicy;
import web.tosunsaeng.domain.blog.domain.repository.BlogPostRepository;
import web.tosunsaeng.global.error.code.status.ErrorStatus;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class BlogCommentServiceImpl implements BlogCommentService {

    static final int MAX_PAGE_SIZE = 100;
    static final int MAX_TOKEN_COLLISION_ATTEMPTS = 5;

    private final BlogPostRepository blogPostRepository;
    private final BlogCommentRepository blogCommentRepository;
    private final AnonymousVisitorService anonymousVisitorService;
    private final CommentAbusePreventionService commentAbusePreventionService;
    private final CommentValidator commentValidator;
    private final BlogCommentConverter blogCommentConverter;
    private final Clock clock;

    @Override
    public BlogCommentResponseDTO.CommentPageResult getComments(
            String slug,
            int page,
            int size) {
        validatePagination(page, size);
        Instant now = clock.instant();
        BlogPost post = findPublicPost(slug, now)
                .orElseThrow(() -> new BlogCommentException(ErrorStatus._BLOG_POST_NOT_FOUND));
        Page<BlogComment> comments = blogCommentRepository.findVisibleCommentsByPostId(
                post.getId(),
                PageRequest.of(page, size));
        return blogCommentConverter.toCommentPageResult(comments);
    }

    @Override
    public CreatedCommentSession createComment(
            String slug,
            BlogCommentRequestDTO.CreateCommentRequest request,
            String rawToken,
            Supplier<String> clientIpSupplier) {
        if (isHoneypot(request)) {
            return CreatedCommentSession.acceptedRequest();
        }

        CommentValidator.ValidationResult contentValidation = commentValidator.validate(
                request == null ? null : request.getContent(),
                true);
        if (contentValidation.hasViolations()) {
            throwValidation(contentValidation);
        }

        Instant now = clock.instant();
        Optional<BlogPost> publicPost = findPublicPost(slug, now);
        if (publicPost.isEmpty()) {
            throwValidation(commentValidator.validate(
                    request == null ? null : request.getContent(),
                    false));
        }

        AnonymousVisitorService.PreparedVisitor preparedVisitor =
                anonymousVisitorService.prepare(rawToken, now);
        for (int attempt = 0; attempt < MAX_TOKEN_COLLISION_ATTEMPTS; attempt++) {
            CreatedCommentSession result = createWithPreparedVisitor(
                    publicPost.orElseThrow(),
                    contentValidation.normalizedContent(),
                    preparedVisitor,
                    clientIpSupplier,
                    now);
            if (result != null) {
                return result;
            }
            if (attempt + 1 < MAX_TOKEN_COLLISION_ATTEMPTS) {
                preparedVisitor = anonymousVisitorService.prepare(null, now);
            }
        }
        throw new BlogCommentException(ErrorStatus._INTERNAL_SERVER_ERROR);
    }

    private CreatedCommentSession createWithPreparedVisitor(
            BlogPost post,
            String normalizedContent,
            AnonymousVisitorService.PreparedVisitor preparedVisitor,
            Supplier<String> clientIpSupplier,
            Instant now) {
        CommentAbusePreventionService.Admission admission =
                commentAbusePreventionService.admit(
                        preparedVisitor.visitor().getTokenHash(),
                        post.getId(),
                        normalizedContent,
                        clientIpSupplier);
        AnonymousVisitorService.VisitorResolution visitorResolution;
        try {
            visitorResolution = anonymousVisitorService.commit(preparedVisitor, now);
        } catch (DuplicateKeyException exception) {
            releaseReservation(admission, exception);
            if (preparedVisitor.newVisitor()) {
                return null;
            }
            throw new BlogCommentException(ErrorStatus._INTERNAL_SERVER_ERROR);
        } catch (RuntimeException exception) {
            releaseReservation(admission, exception);
            throw exception;
        }

        AnonymousVisitor visitor = visitorResolution.visitor();
        BlogComment comment = newVisibleComment(
                post.getId(),
                visitor,
                normalizedContent,
                now);
        BlogComment savedComment;
        try {
            savedComment = blogCommentRepository.save(comment);
        } catch (RuntimeException exception) {
            releaseReservation(admission, exception);
            throw exception;
        }
        return new CreatedCommentSession(
                blogCommentConverter.toCreatedCommentResult(savedComment),
                visitorResolution.rawTokenToSet());
    }

    private BlogComment newVisibleComment(
            String postId,
            AnonymousVisitor visitor,
            String normalizedContent,
            Instant now) {
        BlogComment comment = BlogComment.builder()
                .postId(postId)
                .anonymousVisitorId(visitor.getId())
                .nickname(visitor.getNickname())
                .avatarSeed(visitor.getAvatarSeed())
                .avatarImageKey(visitor.getAvatarImageKey())
                .content(normalizedContent)
                .status(CommentStatus.VISIBLE)
                .createdAt(now)
                .updatedAt(now)
                .hiddenAt(null)
                .hiddenReason(null)
                .build();
        return comment;
    }

    @Override
    public AnonymousProfileSession regenerateAnonymousProfile(String rawToken) {
        Instant now = clock.instant();
        AnonymousVisitorService.VisitorResolution resolution =
                anonymousVisitorService.regenerate(rawToken, now);
        return new AnonymousProfileSession(
                blogCommentConverter.toAnonymousProfileResult(resolution.visitor()),
                resolution.rawTokenToSet());
    }

    private Optional<BlogPost> findPublicPost(String slug, Instant now) {
        if (!BlogPostSlugPolicy.isValid(slug)) {
            return Optional.empty();
        }
        return blogPostRepository.findPublicPostBySlug(slug, now);
    }

    private boolean isHoneypot(BlogCommentRequestDTO.CreateCommentRequest request) {
        return request != null
                && request.getWebsite() != null
                && !request.getWebsite().strip().isEmpty();
    }

    private void throwValidation(CommentValidator.ValidationResult validation) {
        throw new CommentValidationException(
                ErrorStatus._COMMENT_VALIDATION_FAILED,
                validation.violations());
    }

    private void releaseReservation(
            CommentAbusePreventionService.Admission admission,
            RuntimeException originalException) {
        try {
            commentAbusePreventionService.releaseDuplicate(admission);
        } catch (RuntimeException cleanupException) {
            originalException.addSuppressed(cleanupException);
            throw cleanupException;
        }
    }

    private void validatePagination(int page, int size) {
        if (page < 0) {
            throw new BlogCommentException(ErrorStatus._BLOG_PAGE_NEGATIVE);
        }
        if (size < 1) {
            throw new BlogCommentException(ErrorStatus._BLOG_SIZE_TOO_SMALL);
        }
        if (size > MAX_PAGE_SIZE) {
            throw new BlogCommentException(ErrorStatus._BLOG_SIZE_TOO_LARGE);
        }
    }
}
