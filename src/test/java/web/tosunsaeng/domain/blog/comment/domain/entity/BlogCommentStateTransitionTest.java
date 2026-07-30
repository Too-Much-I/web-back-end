package web.tosunsaeng.domain.blog.comment.domain.entity;

import org.junit.jupiter.api.Test;
import web.tosunsaeng.domain.blog.comment.domain.enums.CommentStatus;
import web.tosunsaeng.domain.blog.comment.domain.enums.HiddenReason;

import static org.assertj.core.api.Assertions.assertThat;

class BlogCommentStateTransitionTest {

    @Test
    void commentStatusContainsNoDeletedState() {
        assertThat(CommentStatus.values()).containsExactly(
                CommentStatus.VISIBLE,
                CommentStatus.PENDING,
                CommentStatus.HIDDEN);
        assertThat(CommentStatus.values())
                .noneMatch(status -> status.name().contains("DELET"));
    }

    @Test
    void hiddenReasonUsesOnlyApprovedOperationalValues() {
        assertThat(HiddenReason.values()).containsExactly(
                HiddenReason.SPAM,
                HiddenReason.ABUSE,
                HiddenReason.ADVERTISEMENT,
                HiddenReason.PERSONAL_INFORMATION,
                HiddenReason.OTHER);
    }
}
