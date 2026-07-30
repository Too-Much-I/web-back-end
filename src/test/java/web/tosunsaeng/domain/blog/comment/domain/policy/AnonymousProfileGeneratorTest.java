package web.tosunsaeng.domain.blog.comment.domain.policy;

import org.junit.jupiter.api.Test;
import web.tosunsaeng.domain.blog.comment.config.AnonymousProfileConfig;
import web.tosunsaeng.domain.blog.comment.config.AnonymousProfileProperties;

import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.Deque;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnonymousProfileGeneratorTest {

    @Test
    void productionConfigurationProvidesSecureRandomSource() {
        assertThat(new AnonymousProfileConfig().anonymousSecureRandom())
                .isExactlyInstanceOf(SecureRandom.class);
    }

    @Test
    void adjectiveCandidatesAreImmutableNonBlankAndUnique() {
        assertThat(AnonymousProfileGenerator.ADJECTIVES)
                .hasSizeGreaterThanOrEqualTo(2)
                .doesNotHaveDuplicates()
                .allMatch(value -> value != null && !value.isBlank());
        assertThatThrownBy(() -> AnonymousProfileGenerator.ADJECTIVES.add("새로운"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void generatesNicknameFromAdjectiveAndSameAvatarOptionNoun() {
        StubSecureRandom random = new StubSecureRandom()
                .addInt(0)
                .addInt(0)
                .addBytes(bytes(1));
        AnonymousProfileGenerator generator = generator(random);

        AnonymousProfileGenerator.GeneratedProfile profile = generator.generate();

        assertThat(profile.adjective()).isEqualTo("차분한");
        assertThat(profile.nickname()).isEqualTo("차분한 수달");
        assertThat(profile.avatarImageKey()).isEqualTo("character-image/example-otter-v1.webp");
        assertThat(profile.avatarImageUrl())
                .isEqualTo("https://cdn.example.test/character-image/example-otter-v1.webp");
    }

    @Test
    void avatarSeedUsesIndependentSixteenRandomBytesAndBase64Url() {
        byte[] seed = bytes(17);
        StubSecureRandom random = new StubSecureRandom()
                .addInt(1)
                .addInt(1)
                .addBytes(seed);

        AnonymousProfileGenerator.GeneratedProfile profile = generator(random).generate();

        assertThat(Base64.getUrlDecoder().decode(profile.avatarSeed()))
                .containsExactly(seed)
                .hasSize(16);
        assertThat(profile.avatarSeed()).doesNotContain("=");
        assertThat(profile.avatarSeed()).doesNotContain(profile.nickname());
        assertThat(profile.avatarImageKey()).doesNotContain(profile.avatarSeed());
    }

    @Test
    void regenerationChangesAdjectiveOptionAndSeedAfterBoundedRetry() {
        byte[] currentSeedBytes = bytes(0);
        String currentSeed = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(currentSeedBytes);
        StubSecureRandom random = new StubSecureRandom()
                .addInt(0).addInt(0).addBytes(currentSeedBytes)
                .addInt(1).addInt(1).addBytes(bytes(9));

        AnonymousProfileGenerator.GeneratedProfile profile = generator(random).regenerate(
                "차분한 수달",
                currentSeed,
                "character-image/example-otter-v1.webp");

        assertThat(profile.adjective()).isEqualTo("꼼꼼한");
        assertThat(profile.nickname()).isEqualTo("꼼꼼한 펭귄");
        assertThat(profile.avatarImageKey()).isEqualTo("character-image/example-penguin-v1.webp");
        assertThat(profile.avatarSeed()).isNotEqualTo(currentSeed);
    }

    @Test
    void regenerationFailsInsteadOfReturningCurrentProfileAfterRetryLimit() {
        byte[] currentSeedBytes = bytes(0);
        String currentSeed = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(currentSeedBytes);
        RepeatingSecureRandom random = new RepeatingSecureRandom(0, currentSeedBytes);

        assertThatIllegalStateException().isThrownBy(() -> generator(random).regenerate(
                "차분한 수달",
                currentSeed,
                "character-image/example-otter-v1.webp"));
    }

    private AnonymousProfileGenerator generator(SecureRandom random) {
        AnonymousAvatarImageCatalog catalog = new AnonymousAvatarImageCatalog(List.of(
                option("수달", "character-image/example-otter-v1.webp"),
                option("펭귄", "character-image/example-penguin-v1.webp")));
        return new AnonymousProfileGenerator(
                random,
                catalog,
                new AvatarImageUrlResolver("https://cdn.example.test"));
    }

    private AnonymousProfileProperties.AvatarOption option(String noun, String imageKey) {
        return new AnonymousProfileProperties.AvatarOption(noun, imageKey);
    }

    private byte[] bytes(int firstValue) {
        byte[] bytes = new byte[16];
        bytes[0] = (byte) firstValue;
        return bytes;
    }

    private static class StubSecureRandom extends SecureRandom {
        private final Deque<Integer> integers = new ArrayDeque<>();
        private final Deque<byte[]> byteValues = new ArrayDeque<>();

        StubSecureRandom addInt(int value) {
            integers.add(value);
            return this;
        }

        StubSecureRandom addBytes(byte[] value) {
            byteValues.add(value.clone());
            return this;
        }

        @Override
        public int nextInt(int bound) {
            return Math.floorMod(integers.removeFirst(), bound);
        }

        @Override
        public void nextBytes(byte[] bytes) {
            byte[] value = byteValues.removeFirst();
            System.arraycopy(value, 0, bytes, 0, bytes.length);
        }
    }

    private static class RepeatingSecureRandom extends SecureRandom {
        private final int integer;
        private final byte[] byteValue;

        RepeatingSecureRandom(int integer, byte[] byteValue) {
            this.integer = integer;
            this.byteValue = byteValue.clone();
        }

        @Override
        public int nextInt(int bound) {
            return Math.floorMod(integer, bound);
        }

        @Override
        public void nextBytes(byte[] bytes) {
            System.arraycopy(byteValue, 0, bytes, 0, bytes.length);
        }
    }
}
