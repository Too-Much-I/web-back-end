package web.tosunsaeng.domain.blog.comment.domain.policy;

import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class CommentSpamPatternPolicy {

    static final int MAX_ALLOWED_IDENTICAL_RUN = 9;
    static final int MIN_REPEAT_UNIT = 2;
    static final int MAX_REPEAT_UNIT = 20;
    static final int MAX_REPEAT_TOKENS = 5;
    static final int REPEAT_COUNT = 5;

    public boolean isSpam(String content) {
        if (content == null || content.isEmpty()) {
            return false;
        }
        return hasIdenticalCodePointRun(content)
                || hasRepeatedCodePointPhrase(normalizeWhitespace(content))
                || hasRepeatedTokenPhrase(content);
    }

    private boolean hasIdenticalCodePointRun(String content) {
        int previous = -1;
        int runLength = 0;
        int[] codePoints = content.codePoints().toArray();
        for (int codePoint : codePoints) {
            if (codePoint == previous) {
                runLength++;
            } else {
                previous = codePoint;
                runLength = 1;
            }
            if (runLength > MAX_ALLOWED_IDENTICAL_RUN) {
                return true;
            }
        }
        return false;
    }

    private boolean hasRepeatedCodePointPhrase(String content) {
        int[] codePoints = content.codePoints().toArray();
        for (int start = 0; start < codePoints.length; start++) {
            int maximumUnitLength = Math.min(
                    MAX_REPEAT_UNIT,
                    (codePoints.length - start) / REPEAT_COUNT);
            for (int unitLength = MIN_REPEAT_UNIT;
                    unitLength <= maximumUnitLength;
                    unitLength++) {
                if (repeats(codePoints, start, unitLength)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean repeats(int[] codePoints, int start, int unitLength) {
        for (int repeat = 1; repeat < REPEAT_COUNT; repeat++) {
            int repeatedStart = start + repeat * unitLength;
            for (int offset = 0; offset < unitLength; offset++) {
                if (codePoints[start + offset] != codePoints[repeatedStart + offset]) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean hasRepeatedTokenPhrase(String content) {
        String stripped = content.strip();
        if (stripped.isEmpty()) {
            return false;
        }
        String[] tokens = stripped.split("(?U)\\s+");
        for (int start = 0; start < tokens.length; start++) {
            int maximumUnitTokens = Math.min(
                    MAX_REPEAT_TOKENS,
                    (tokens.length - start) / REPEAT_COUNT);
            for (int unitTokens = 1; unitTokens <= maximumUnitTokens; unitTokens++) {
                String unit = String.join(" ",
                        Arrays.copyOfRange(tokens, start, start + unitTokens));
                int unitLength = unit.codePointCount(0, unit.length());
                if (unitLength >= MIN_REPEAT_UNIT
                        && unitLength <= MAX_REPEAT_UNIT
                        && repeatedTokensMatch(tokens, start, unitTokens)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean repeatedTokensMatch(String[] tokens, int start, int unitTokens) {
        for (int repeat = 1; repeat < REPEAT_COUNT; repeat++) {
            int repeatedStart = start + repeat * unitTokens;
            for (int offset = 0; offset < unitTokens; offset++) {
                if (!tokens[start + offset].equals(tokens[repeatedStart + offset])) {
                    return false;
                }
            }
        }
        return true;
    }

    private String normalizeWhitespace(String content) {
        return content.replaceAll("(?U)\\s+", " ");
    }
}
