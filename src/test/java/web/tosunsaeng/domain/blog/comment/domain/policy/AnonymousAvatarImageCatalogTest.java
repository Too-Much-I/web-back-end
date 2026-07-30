package web.tosunsaeng.domain.blog.comment.domain.policy;

import org.junit.jupiter.api.Test;
import web.tosunsaeng.domain.blog.comment.config.AnonymousProfileProperties;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnonymousAvatarImageCatalogTest {

    @Test
    void keepsNounAndImageKeyAsOneImmutableOption() {
        List<AnonymousProfileProperties.AvatarOption> configured = validOptions();

        AnonymousAvatarImageCatalog catalog = new AnonymousAvatarImageCatalog(configured);

        assertThat(catalog.options())
                .containsExactly(
                        new AnonymousAvatarImageCatalog.AvatarOption(
                                "수달", "character-image/example-otter-v1.webp"),
                        new AnonymousAvatarImageCatalog.AvatarOption(
                                "펭귄", "character-image/example-penguin-v1.webp"));
        assertThatThrownBy(() -> catalog.options().add(
                new AnonymousAvatarImageCatalog.AvatarOption("여우", "character-image/fox.webp")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsFewerThanTwoOptions() {
        assertThatIllegalStateException().isThrownBy(() ->
                new AnonymousAvatarImageCatalog(List.of(option("수달", "character-image/otter.webp"))));
    }

    @Test
    void rejectsBlankNoun() {
        assertInvalidOptions(List.of(
                option(" ", "character-image/otter.webp"),
                option("펭귄", "character-image/penguin.webp")));
    }

    @Test
    void rejectsBlankImageKey() {
        assertInvalidOptions(List.of(
                option("수달", ""),
                option("펭귄", "character-image/penguin.webp")));
    }

    @Test
    void rejectsLeadingSlashImageKey() {
        assertInvalidKey("/character-image/otter.webp");
    }

    @Test
    void rejectsPathTraversalImageKey() {
        assertInvalidKey("character-image/../secret.webp");
    }

    @Test
    void rejectsAbsoluteHttpAndHttpsImageKeys() {
        assertInvalidKey("http://example.com/otter.webp");
        assertInvalidKey("https://example.com/otter.webp");
    }

    @Test
    void rejectsImageKeyOutsideCharacterImagePrefix() {
        assertInvalidKey("other-image/otter.webp");
        assertInvalidKey("character-image/nested/otter.webp");
    }

    @Test
    void rejectsDuplicateNoun() {
        assertInvalidOptions(List.of(
                option("수달", "character-image/otter.webp"),
                option("수달", "character-image/otter-v2.webp")));
    }

    @Test
    void rejectsDuplicateImageKey() {
        assertInvalidOptions(List.of(
                option("수달", "character-image/shared.webp"),
                option("펭귄", "character-image/shared.webp")));
    }

    @Test
    void copiesConfigurationInsteadOfKeepingMutableList() {
        List<AnonymousProfileProperties.AvatarOption> configured = new ArrayList<>(validOptions());
        AnonymousAvatarImageCatalog catalog = new AnonymousAvatarImageCatalog(configured);

        configured.clear();

        assertThat(catalog.options()).hasSize(2);
    }

    private void assertInvalidKey(String key) {
        assertInvalidOptions(List.of(
                option("수달", key),
                option("펭귄", "character-image/penguin.webp")));
    }

    private void assertInvalidOptions(List<AnonymousProfileProperties.AvatarOption> options) {
        assertThatIllegalStateException().isThrownBy(() ->
                new AnonymousAvatarImageCatalog(options));
    }

    private List<AnonymousProfileProperties.AvatarOption> validOptions() {
        return List.of(
                option("수달", "character-image/example-otter-v1.webp"),
                option("펭귄", "character-image/example-penguin-v1.webp"));
    }

    private AnonymousProfileProperties.AvatarOption option(String noun, String imageKey) {
        return new AnonymousProfileProperties.AvatarOption(noun, imageKey);
    }
}
