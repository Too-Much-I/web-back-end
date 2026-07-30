package web.tosunsaeng.domain.blog.comment.domain.policy;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import web.tosunsaeng.domain.blog.comment.domain.enums.CommentRule;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

@Component
public class CommentValidator {

    static final int MIN_CONTENT_CODE_POINTS = 2;
    static final int MAX_CONTENT_CODE_POINTS = 500;

    private static final Pattern HTML_TAG = Pattern.compile(
            "(?is)<\\s*/?\\s*[a-z][^>]*>");
    private static final Pattern HTML_SPECIAL = Pattern.compile(
            "(?is)<!--.*?-->|<!\\s*doctype\\b[^>]*>|<\\?.*?\\?>");
    private static final Pattern EXECUTABLE_HTML = Pattern.compile(
            "(?i)(?:(?<![\\p{L}\\p{N}_-])javascript\\s*:"
                    + "|(?<![\\p{L}\\p{N}_-])on[a-z]+\\s*=)");
    private static final Pattern HTML_ENTITY = Pattern.compile(
            "(?i)&(?:#[0-9]+|#x[0-9a-f]+|[a-z][a-z0-9]+);");

    private static final List<Pattern> MARKDOWN_PATTERNS = List.of(
            Pattern.compile("(?m)^\\s{0,3}(?:```|~~~)"),
            Pattern.compile("(?m)^\\s{0,3}#{1,6}\\s+"),
            Pattern.compile("(?m)^\\s{0,3}>\\s?"),
            Pattern.compile("(?m)^\\s{0,3}(?:[-+*]|[0-9]+[.)])\\s+"),
            Pattern.compile("!?\\[[^]\\r\\n]+]\\([^)]*\\)"),
            Pattern.compile("\\[[^]\\r\\n]+]\\s*\\[[^]\\r\\n]*]"),
            Pattern.compile("(?:\\*\\*[^*\\r\\n]+\\*\\*|__[^_\\r\\n]+__)"),
            Pattern.compile("(?<!\\*)\\*(?![\\s*])[^*\\r\\n]+(?<!\\s)\\*(?!\\*)"),
            Pattern.compile("(?<!_)_(?![\\s_])[^_\\r\\n]+(?<!\\s)_(?!_)"),
            Pattern.compile("`[^`\\r\\n]+`"),
            Pattern.compile("~~[^~\\r\\n]+~~"),
            Pattern.compile("(?m)^\\s{0,3}(?:-{3,}|_{3,}|\\*{3,})\\s*$"));

    private static final Pattern HTTP_URL = Pattern.compile(
            "(?i)(?<![a-z0-9_])https?://");
    private static final Pattern WWW_URL = Pattern.compile(
            "(?i)(?<![\\p{L}\\p{N}_])www\\.[a-z0-9]");
    private static final Pattern EMAIL = Pattern.compile(
            "(?i)(?<![a-z0-9._%+-])[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,63}(?![a-z0-9_-])");
    private static final Pattern DOMAIN = Pattern.compile(
            "(?i)(?<![a-z0-9_@-])(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}(?![a-z0-9_-])");

    private final CommentSpamPatternPolicy spamPatternPolicy;

    public CommentValidator(CommentSpamPatternPolicy spamPatternPolicy) {
        this.spamPatternPolicy = spamPatternPolicy;
    }

    public ValidationResult validate(JsonNode contentNode, boolean publicPost) {
        Map<Integer, Violation> violations = new TreeMap<>();
        boolean isTextual = contentNode != null && contentNode.isTextual();
        String normalizedContent = isTextual ? contentNode.textValue().strip() : "";

        int codePointLength = normalizedContent.codePointCount(0, normalizedContent.length());
        if (codePointLength < MIN_CONTENT_CODE_POINTS) {
            addViolation(violations, CommentRule.COMMENT_MIN_LENGTH);
        }
        if (codePointLength > MAX_CONTENT_CODE_POINTS) {
            addViolation(violations, CommentRule.COMMENT_MAX_LENGTH);
        }
        if (!isTextual) {
            addViolation(violations, CommentRule.COMMENT_PLAIN_TEXT_ONLY);
        }
        if (containsHtml(normalizedContent)) {
            addViolation(violations, CommentRule.COMMENT_HTML_NOT_ALLOWED);
        }
        if (containsMarkdown(normalizedContent)) {
            addViolation(violations, CommentRule.COMMENT_MARKDOWN_NOT_ALLOWED);
        }
        if (containsUrlOrEmail(normalizedContent)) {
            addViolation(violations, CommentRule.COMMENT_URL_NOT_ALLOWED);
        }
        if (normalizedContent.isEmpty()) {
            addViolation(violations, CommentRule.COMMENT_EMPTY);
        }
        if (spamPatternPolicy.isSpam(normalizedContent)) {
            addViolation(violations, CommentRule.COMMENT_SPAM_PATTERN);
        }
        if (!publicPost) {
            addViolation(violations, CommentRule.COMMENT_POST_NOT_PUBLIC);
        }

        return new ValidationResult(normalizedContent, List.copyOf(violations.values()));
    }

    private boolean containsHtml(String content) {
        return HTML_TAG.matcher(content).find()
                || HTML_SPECIAL.matcher(content).find()
                || EXECUTABLE_HTML.matcher(content).find()
                || HTML_ENTITY.matcher(content).find();
    }

    private boolean containsMarkdown(String content) {
        return MARKDOWN_PATTERNS.stream().anyMatch(pattern -> pattern.matcher(content).find());
    }

    private boolean containsUrlOrEmail(String content) {
        return HTTP_URL.matcher(content).find()
                || WWW_URL.matcher(content).find()
                || EMAIL.matcher(content).find()
                || DOMAIN.matcher(content).find();
    }

    private void addViolation(
            Map<Integer, Violation> violations,
            CommentRule rule) {
        violations.putIfAbsent(
                rule.getRuleNumber(),
                new Violation(
                        rule.getRuleNumber(),
                        rule.getRuleCode(),
                        rule.getDefaultMessage()));
    }

    public record ValidationResult(String normalizedContent, List<Violation> violations) {

        public boolean hasViolations() {
            return !violations.isEmpty();
        }
    }

    public record Violation(int ruleNumber, String ruleCode, String message) {
    }
}
