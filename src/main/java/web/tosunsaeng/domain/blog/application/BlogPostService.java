package web.tosunsaeng.domain.blog.application;

import web.tosunsaeng.domain.blog.dto.BlogPostResponseDTO;

public interface BlogPostService {

    BlogPostResponseDTO.PostPageResult getPublicPosts(int page, int size);

    BlogPostResponseDTO.PostDetailResult getPublicPost(String slug);

    BlogPostResponseDTO.PostSearchResult searchPublicPosts(String query, int page, int size);
}
