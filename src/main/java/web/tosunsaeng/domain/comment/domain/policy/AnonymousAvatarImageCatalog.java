package web.tosunsaeng.domain.comment.domain.policy;

import web.tosunsaeng.domain.comment.config.AnonymousProfileProperties;

import java.util.List;

public class AnonymousAvatarImageCatalog {

    private final List<AvatarOption> options;

    public AnonymousAvatarImageCatalog(
            List<AnonymousProfileProperties.AvatarOption> configuredOptions) {
        AnonymousProfileProperties.validateAvatarOptions(configuredOptions);
        this.options = configuredOptions.stream()
                .map(option -> new AvatarOption(option.getNoun(), option.getImageKey()))
                .toList();
    }

    public List<AvatarOption> options() {
        return options;
    }

    public record AvatarOption(String noun, String imageKey) {
    }
}
