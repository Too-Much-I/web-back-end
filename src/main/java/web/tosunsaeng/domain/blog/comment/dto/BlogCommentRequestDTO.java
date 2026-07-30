package web.tosunsaeng.domain.blog.comment.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class BlogCommentRequestDTO {

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateCommentRequest {
        private JsonNode content;
        private String website;
    }
}
