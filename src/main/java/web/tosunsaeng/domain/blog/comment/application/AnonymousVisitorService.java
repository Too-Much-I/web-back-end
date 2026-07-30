package web.tosunsaeng.domain.blog.comment.application;

import web.tosunsaeng.domain.blog.comment.domain.entity.AnonymousVisitor;

import java.time.Instant;

public interface AnonymousVisitorService {

    VisitorResolution resolve(String rawToken, Instant now);

    VisitorResolution regenerate(String rawToken, Instant now);

    record VisitorResolution(AnonymousVisitor visitor, String rawTokenToSet) {

        public boolean hasNewCookie() {
            return rawTokenToSet != null;
        }
    }
}
