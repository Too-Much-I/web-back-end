package web.tosunsaeng.global.contract;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class BlogMvpApiContractTest {

    @Autowired
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    void finalPublicAndInternalMappingsExist() {
        assertMappingsExist(
                mapping(RequestMethod.GET, "/api/posts"),
                mapping(RequestMethod.GET, "/api/posts/{slug}"),
                mapping(RequestMethod.GET, "/api/posts/search"),
                mapping(RequestMethod.GET, "/api/posts/{slug}/comments"),
                mapping(RequestMethod.POST, "/api/posts/{slug}/comments"),
                mapping(RequestMethod.POST, "/api/comments/nickname/regenerate"),
                mapping(RequestMethod.POST, "/api/newsletter/subscribe"),
                mapping(RequestMethod.POST, "/api/newsletter/unsubscribe"),
                mapping(
                        RequestMethod.POST,
                        "/api/newsletter/one-click-unsubscribe/{token}"),
                mapping(RequestMethod.GET, "/internal/comments"),
                mapping(RequestMethod.PATCH, "/internal/comments/{commentId}/hide"),
                mapping(RequestMethod.PATCH, "/internal/comments/{commentId}/restore"),
                mapping(RequestMethod.POST, "/internal/newsletter/posts/{postId}/test"),
                mapping(RequestMethod.POST, "/internal/newsletter/posts/{postId}/cancel"),
                mapping(RequestMethod.POST, "/internal/newsletter/posts/{postId}/retry"));
    }

    @Test
    void forbiddenProductMappingsDoNotExist() {
        assertMappingsDoNotExist(
                mapping(RequestMethod.POST, "/api/posts"),
                mapping(RequestMethod.PUT, "/api/posts/{slug}"),
                mapping(RequestMethod.PATCH, "/api/posts/{slug}"),
                mapping(RequestMethod.DELETE, "/api/posts/{slug}"),
                mapping(RequestMethod.PATCH, "/api/comments/{commentId}"),
                mapping(RequestMethod.DELETE, "/api/comments/{commentId}"),
                mapping(RequestMethod.DELETE, "/internal/comments/{commentId}"),
                mapping(RequestMethod.GET, "/api/newsletter/verify"),
                mapping(RequestMethod.POST, "/api/newsletter/verify"),
                mapping(RequestMethod.GET, "/api/newsletter/unsubscribe"),
                mapping(
                        RequestMethod.GET,
                        "/api/newsletter/one-click-unsubscribe/{token}"));
    }

    @Test
    void examsSummaryAndSingleQuestionContractsRemainMapped() {
        assertMappingsExist(
                mapping(RequestMethod.GET, "/api/v1/exams/{examId}/summary"),
                mapping(RequestMethod.GET, "/api/v1/exams/{examId}/questions"));
    }

    @Test
    void oneClickMappingAcceptsOnlyApprovedFormMediaTypes() {
        RequestMappingInfo oneClick = handlerMapping.getHandlerMethods().keySet().stream()
                .filter(info -> hasMapping(
                        info,
                        RequestMethod.POST,
                        "/api/newsletter/one-click-unsubscribe/{token}"))
                .findFirst()
                .orElseThrow();

        assertThat(oneClick.getConsumesCondition().getConsumableMediaTypes())
                .containsExactlyInAnyOrder(
                        MediaType.APPLICATION_FORM_URLENCODED,
                        MediaType.MULTIPART_FORM_DATA);
    }

    private void assertMappingsExist(ApiMapping... expected) {
        assertThat(actualMappings()).contains(expected);
    }

    private void assertMappingsDoNotExist(ApiMapping... forbidden) {
        assertThat(actualMappings()).doesNotContain(forbidden);
    }

    private Set<ApiMapping> actualMappings() {
        return handlerMapping.getHandlerMethods().entrySet().stream()
                .filter(entry -> entry.getValue().getBeanType().getName()
                        .startsWith("web.tosunsaeng.domain."))
                .flatMap(entry -> entry.getKey().getPatternValues().stream()
                        .flatMap(path -> entry.getKey()
                                .getMethodsCondition()
                                .getMethods()
                                .stream()
                                .map(method -> mapping(method, path))))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private boolean hasMapping(
            RequestMappingInfo info,
            RequestMethod method,
            String path) {
        return info.getPatternValues().contains(path)
                && info.getMethodsCondition().getMethods().contains(method);
    }

    private ApiMapping mapping(RequestMethod method, String path) {
        return new ApiMapping(method, path);
    }

    private record ApiMapping(RequestMethod method, String path) {
    }
}
