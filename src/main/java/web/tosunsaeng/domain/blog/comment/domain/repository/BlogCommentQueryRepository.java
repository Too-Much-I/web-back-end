package web.tosunsaeng.domain.blog.comment.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import web.tosunsaeng.domain.blog.comment.domain.entity.BlogComment;

public interface BlogCommentQueryRepository {

    Page<BlogComment> findVisibleCommentsByPostId(String postId, Pageable pageable);
}
