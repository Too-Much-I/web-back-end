package web.tosunsaeng.domain.comment.domain.policy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import web.tosunsaeng.domain.comment.domain.enums.CommentRule;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class CommentValidatorTest {

    private CommentValidator validator;

    @BeforeEach
    void setUp() {
        validator = new CommentValidator(new CommentSpamPatternPolicy());
    }

    @Test
    void keepsFixedRuleNumbersCodesAndOrder() {
        assertThat(Arrays.stream(CommentRule.values())
                .map(CommentRule::getRuleNumber)
                .toList())
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        assertThat(Arrays.stream(CommentRule.values())
                .map(CommentRule::getRuleCode)
                .toList())
                .containsExactly(
                        "COMMENT_MIN_LENGTH",
                        "COMMENT_MAX_LENGTH",
                        "COMMENT_TRIM",
                        "COMMENT_PLAIN_TEXT_ONLY",
                        "COMMENT_HTML_NOT_ALLOWED",
                        "COMMENT_MARKDOWN_NOT_ALLOWED",
                        "COMMENT_URL_NOT_ALLOWED",
                        "COMMENT_EMPTY",
                        "COMMENT_SPAM_PATTERN",
                        "COMMENT_POST_NOT_PUBLIC");
    }

    @Test
    void rejectsOneUnicodeCodePointAndAcceptsTwo() {
        assertRules(text("😀"), true, 1);
        assertThat(validate(text("😀가"), true).violations()).isEmpty();
    }

    @Test
    void acceptsFiveHundredCodePointsAndRejectsFiveHundredOne() {
        String fiveHundred = distinctHangul(500);
        String fiveHundredOne = distinctHangul(501);

        assertThat(fiveHundred.codePointCount(0, fiveHundred.length())).isEqualTo(500);
        assertThat(validate(text(fiveHundred), true).violations()).isEmpty();
        assertRules(text(fiveHundredOne), true, 2);
    }

    @Test
    void countsSurrogatePairsAsUnicodeCodePoints() {
        String content = "😀".repeat(500);

        CommentValidator.ValidationResult result = validate(text(content), true);

        assertThat(content.length()).isEqualTo(1000);
        assertThat(ruleNumbers(result)).doesNotContain(2);
        assertThat(ruleNumbers(result)).contains(9);
    }

    @Test
    void stripsLeadingAndTrailingUnicodeWhitespaceWithoutRuleThreeViolation() {
        CommentValidator.ValidationResult result = validate(
                text("\u2003\t  정상 댓글  \n\u2003"),
                true);

        assertThat(result.normalizedContent()).isEqualTo("정상 댓글");
        assertThat(result.violations()).isEmpty();
        assertThat(ruleNumbers(result)).doesNotContain(3);
    }

    @Test
    void nonStringContentReturnsPlainTextViolationWithoutRuntimeFailure() {
        List<JsonNode> nonStringNodes = Arrays.asList(
                null,
                JsonNodeFactory.instance.missingNode(),
                JsonNodeFactory.instance.nullNode(),
                JsonNodeFactory.instance.objectNode().put("text", "댓글"),
                JsonNodeFactory.instance.arrayNode().add("댓글"),
                JsonNodeFactory.instance.numberNode(123),
                JsonNodeFactory.instance.booleanNode(true));

        for (JsonNode node : nonStringNodes) {
            CommentValidator.ValidationResult result = validate(node, true);

            assertThat(result.normalizedContent()).isEmpty();
            assertThat(ruleNumbers(result)).containsExactly(1, 4, 8);
        }
    }

    @Test
    void blocksHtmlTagsAndExecutableHtmlPatterns() {
        List<String> blocked = List.of(
                "<div>댓글</div>",
                "<script>alert(1)</script>",
                "<a href='path'>링크</a>",
                "<img src='asset'>",
                "<iframe>frame</iframe>",
                "<!-- comment -->",
                "<!DOCTYPE html>",
                "<?xml version='1.0'?>",
                "javascript:alert(1)",
                "onclick = alert(1)");

        for (String content : blocked) {
            assertThat(ruleNumbers(validate(text(content), true)))
                    .as(content)
                    .contains(5);
        }
    }

    @Test
    void blocksNamedAndNumericHtmlEntitiesButAllowsComparisonCharacters() {
        for (String content : List.of("&lt;script&gt;", "&#60;div&#62;", "&#x3c;img&#x3e;", "&copy;")) {
            assertThat(ruleNumbers(validate(text(content), true)))
                    .as(content)
                    .contains(5);
        }

        assertThat(validate(text("점수는 5 > 3이고 2 < 4이며 A & B예요."), true).violations())
                .isEmpty();
        assertThat(validate(text("condition=좋아요 같은 일반 문장은 허용해요."), true).violations())
                .isEmpty();
    }

    @Test
    void blocksClearMarkdownStructures() {
        List<String> blocked = List.of(
                "```java\ncode\n```",
                "~~~\ncode\n~~~",
                "# 제목",
                "> 인용",
                "- 목록",
                "+ 목록",
                "* 목록",
                "1. 목록",
                "[링크](path)",
                "![이미지](asset)",
                "[참조][id]",
                "**강조**",
                "__강조__",
                "*강조*",
                "_강조_",
                "`코드`",
                "~~취소~~",
                "---");

        for (String content : blocked) {
            assertThat(ruleNumbers(validate(text(content), true)))
                    .as(content)
                    .contains(6);
        }
    }

    @Test
    void allowsOrdinarySentencesWithSingleSpecialCharacters() {
        List<String> allowed = List.of(
                "시험에서 #1이 가장 어려워요.",
                "3 * 4만큼 연습했어요.",
                "점수는 5 > 3이라고 생각해요.",
                "괄호 (예시), 대시 - 와 단일 별표 * 는 괜찮아요.",
                "정말 정말 좋아요!");

        for (String content : allowed) {
            assertThat(validate(text(content), true).violations())
                    .as(content)
                    .isEmpty();
        }
    }

    @Test
    void blocksHttpHttpsWwwDomainsAndEmailAddresses() {
        List<String> blocked = List.of(
                "http://example 경로",
                "주소는http://example 경로",
                "HTTPS://example 경로",
                "www.example.com을 확인해요",
                "example.com을 확인해요",
                "sub.example.co.kr을 확인해요",
                "user@example.com으로 보내요");

        for (String content : blocked) {
            assertThat(ruleNumbers(validate(text(content), true)))
                    .as(content)
                    .contains(7);
        }
    }

    @Test
    void doesNotTreatSpacedAtSignAsEmailAddress() {
        assertThat(validate(text("name @ example 형태는 주소가 아니에요."), true).violations())
                .isEmpty();
    }

    @Test
    void emptyWhitespaceTabAndNewlineReturnMinimumThenEmpty() {
        for (String content : List.of("", "   ", "\t\n\r")) {
            assertRules(text(content), true, 1, 8);
        }
    }

    @Test
    void identicalCodePointSpamUsesTenCharacterBoundary() {
        assertThat(validate(text("ㅋ".repeat(9)), true).violations()).isEmpty();
        assertRules(text("ㅋ".repeat(10)), true, 9);
    }

    @Test
    void repeatedPhraseSpamUsesFiveRepeatBoundary() {
        assertThat(validate(text("도배".repeat(4)), true).violations()).isEmpty();
        assertRules(text("도배".repeat(5)), true, 9);
        assertThat(validate(text("도배 ".repeat(4) + "도배가 아니에요"), true).violations())
                .isEmpty();
        assertRules(text("도배 ".repeat(5)), true, 9);
    }

    @Test
    void nonPublicPostAddsRuleTenWithoutExposingReason() {
        assertRules(text("정상 댓글"), false, 10);
        assertRules(null, false, 1, 4, 8, 10);
    }

    @Test
    void collectsHtmlAndUrlViolationsInRuleNumberOrder() {
        assertRules(text("<a href='https://example.com'>링크</a>"), true, 5, 7);
    }

    @Test
    void collectsMarkdownAndUrlViolationsInRuleNumberOrder() {
        assertRules(text("[링크](https://example.com)"), true, 6, 7);
    }

    @Test
    void deduplicatesSameRuleAndSortsAllViolations() {
        CommentValidator.ValidationResult duplicateHtml = validate(
                text("<div><img src='asset'></div>"),
                true);
        assertThat(ruleNumbers(duplicateHtml)).containsExactly(5);

        CommentValidator.ValidationResult combined = validate(
                text("# 제목 <div>https://example.com</div>"),
                false);
        assertThat(ruleNumbers(combined)).containsExactly(5, 6, 7, 10).isSorted();
    }

    private CommentValidator.ValidationResult validate(JsonNode content, boolean publicPost) {
        return validator.validate(content, publicPost);
    }

    private void assertRules(JsonNode content, boolean publicPost, Integer... expected) {
        assertThat(ruleNumbers(validate(content, publicPost))).containsExactly(expected);
    }

    private List<Integer> ruleNumbers(CommentValidator.ValidationResult result) {
        return result.violations().stream()
                .map(CommentValidator.Violation::ruleNumber)
                .toList();
    }

    private JsonNode text(String value) {
        return JsonNodeFactory.instance.textNode(value);
    }

    private String distinctHangul(int length) {
        return IntStream.range(0, length)
                .mapToObj(index -> Character.toString(0xAC00 + index))
                .collect(Collectors.joining());
    }
}
