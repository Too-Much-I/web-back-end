package web.tosunsaeng.domain.comment.domain.policy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class AvatarImageUrlResolverTest {

    @Test
    void combinesCloudFrontBaseUrlAndImageKey() {
        AvatarImageUrlResolver resolver = new AvatarImageUrlResolver("https://cdn.example.test");

        assertThat(resolver.resolve("character-image/example-otter-v1.webp"))
                .isEqualTo("https://cdn.example.test/character-image/example-otter-v1.webp");
    }

    @Test
    void removesTrailingSlashesWithoutCreatingDuplicateSlash() {
        AvatarImageUrlResolver resolver = new AvatarImageUrlResolver("https://cdn.example.test///");

        assertThat(resolver.resolve("character-image/otter.webp"))
                .isEqualTo("https://cdn.example.test/character-image/otter.webp")
                .doesNotContain("test//character");
    }

    @Test
    void encodesFilenamePathSegment() {
        AvatarImageUrlResolver resolver = new AvatarImageUrlResolver("https://cdn.example.test");

        assertThat(resolver.resolve("character-image/수달 이미지.webp"))
                .isEqualTo("https://cdn.example.test/character-image/%EC%88%98%EB%8B%AC%20%EC%9D%B4%EB%AF%B8%EC%A7%80.webp");
    }

    @Test
    void rejectsNonHttpsBaseUrl() {
        assertThatIllegalStateException().isThrownBy(() ->
                new AvatarImageUrlResolver("http://cdn.example.test"));
    }

    @Test
    void rejectsMissingAndMalformedBaseUrl() {
        assertThatIllegalStateException().isThrownBy(() -> new AvatarImageUrlResolver(""));
        assertThatIllegalStateException().isThrownBy(() -> new AvatarImageUrlResolver("not-a-url"));
    }

    @Test
    void rejectsBaseUrlWithQueryFragmentOrUserInfo() {
        assertThatIllegalStateException().isThrownBy(() ->
                new AvatarImageUrlResolver("https://cdn.example.test?token=value"));
        assertThatIllegalStateException().isThrownBy(() ->
                new AvatarImageUrlResolver("https://cdn.example.test#fragment"));
        assertThatIllegalStateException().isThrownBy(() ->
                new AvatarImageUrlResolver("https://user@cdn.example.test"));
    }

    @Test
    void rejectsInvalidObjectKeyAtResolutionBoundary() {
        AvatarImageUrlResolver resolver = new AvatarImageUrlResolver("https://cdn.example.test");

        assertThatIllegalArgumentException().isThrownBy(() ->
                resolver.resolve("https://bucket.example/character-image/otter.webp"));
    }
}
