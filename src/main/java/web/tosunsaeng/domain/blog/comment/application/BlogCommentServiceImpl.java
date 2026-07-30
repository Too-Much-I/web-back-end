package web.tosunsaeng.domain.blog.comment.application;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import web.tosunsaeng.domain.blog.comment.converter.BlogCommentConverter;
import web.tosunsaeng.domain.blog.comment.domain.entity.AnonymousVisitor;
import web.tosunsaeng.domain.blog.comment.domain.entity.BlogComment;
import web.tosunsaeng.domain.blog.comment.domain.enums.CommentStatus;
import web.tosunsaeng.domain.blog.comment.domain.policy.CommentValidator;
import web.tosunsaeng.domain.blog.comment.domain.repository.BlogCommentRepository;
import web.tosunsaeng.domain.blog.comment.dto.BlogCommentRequestDTO;
import web.tosunsaeng.domain.blog.comment.dto.BlogCommentResponseDTO;
import web.tosunsaeng.domain.blog.comment.exception.BlogCommentException;
import web.tosunsaeng.domain.blog.comment.exception.CommentValidationException;
import web.tosunsaeng.domain.blog.domain.entity.BlogPost;
import web.tosunsaeng.domain.blog.domain.policy.BlogPostSlugPolicy;
import web.tosunsaeng.domain.blog.domain.repository.BlogPostRepository;
import web.tosunsaeng.global.error.code.status.ErrorStatus;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

@Service
public class BlogCommentServiceImpl implements BlogCommentService {

    static final int MAX_PAGE_SIZE = 100;

    private final BlogPostRepository blogPostRepository;
    private final BlogCommentRepository blogCommentRepository;
    private final AnonymousVisitorService anonymousVisitorService;
    private final CommentValidator commentValidator;
    private final BlogCommentConverter blogCommentConverter;
    private final Clock clock;

    public BlogCommentServiceImpl(
            BlogPostRepository blogPostRepository,
            BlogCommentRepository blogCommentRepository,
            AnonymousVisitorService anonymousVisitorService,
            CommentValidator commentValidator,
            BlogCommentConverter blogCommentConverter,
            Clock clock) {
        this.blogPostRepository = blogPostRepository;
        this.blogCommentRepository = blogCommentRepository;
        this.anonymousVisitorService = anonymousVisitorService;
        this.commentValidator = commentValidator;
        this.blogCommentConverter = blogCommentConverter;
        this.clock = clock;
    }

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
            String rawToken) {
        Instant now = clock.instant();
        Optional<BlogPost> publicPost = findPublicPost(slug, now);
        CommentValidator.ValidationResult validation = commentValidator.validate(
                request == null ? null : request.getContent(),
                publicPost.isPresent());
        if (validation.hasViolations()) {
            throw new CommentValidationException(
                    ErrorStatus._COMMENT_VALIDATION_FAILED,
                    validation.violations());
        }

        AnonymousVisitorService.VisitorResolution visitorResolution =
                anonymousVisitorService.resolve(rawToken, now);
        AnonymousVisitor visitor = visitorResolution.visitor();
        BlogComment comment = BlogComment.builder()
                .postId(publicPost.orElseThrow().getId())
                .anonymousVisitorId(visitor.getId())
                .nickname(visitor.getNickname())
                .avatarSeed(visitor.getAvatarSeed())
                .avatarImageKey(visitor.getAvatarImageKey())
                .content(validation.normalizedContent())
                .status(CommentStatus.VISIBLE)
                .createdAt(now)
                .updatedAt(now)
                .hiddenAt(null)
                .hiddenReason(null)
                .build();
        BlogComment savedComment = blogCommentRepository.save(comment);
        return new CreatedCommentSession(
                blogCommentConverter.toCreatedCommentResult(savedComment),
                visitorResolution.rawTokenToSet());
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
