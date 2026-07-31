package web.tosunsaeng.domain.newsletter.domain.policy;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import web.tosunsaeng.domain.newsletter.exception.NewsletterException;
import web.tosunsaeng.global.error.code.status.ErrorStatus;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NewsletterEmailNormalizerTest {

    private final Validator validator = Validation
            .buildDefaultValidatorFactory()
            .getValidator();
    private final NewsletterEmailNormalizer normalizer =
            new NewsletterEmailNormalizer(validator);

    @Test
    void stripsLowercasesAndAcceptsValidEmail() {
        assertThat(normalizer.normalize("  User.Name+News@Example.COM\t"))
                .isEqualTo("user.name+news@example.com");
    }

    @Test
    void doesNotApplyProviderSpecificRewriting() {
        assertThat(normalizer.normalize("First.Last+tag@gmail.com"))
                .isEqualTo("first.last+tag@gmail.com");
    }

    @Test
    void rejectsNullBlankAndInvalidFormatWithoutEchoingInput() {
        assertError(null, ErrorStatus._NEWSLETTER_EMAIL_REQUIRED);
        assertError(" \t\n", ErrorStatus._NEWSLETTER_EMAIL_REQUIRED);
        String invalid = "not-an-email-sensitive-value";

        assertThatThrownBy(() -> normalizer.normalize(invalid))
                .isInstanceOfSatisfying(NewsletterException.class, exception -> {
                    assertThat(exception.getCode())
                            .isEqualTo(ErrorStatus._NEWSLETTER_EMAIL_INVALID);
                    assertThat(String.valueOf(exception.getMessage()))
                            .doesNotContain(invalid);
                });
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void appliesUnicodeCodePointMaximumAt254() {
        Validator acceptingValidator = mock(Validator.class);
        when(acceptingValidator.validate(any())).thenReturn(Collections.emptySet());
        NewsletterEmailNormalizer lengthOnly =
                new NewsletterEmailNormalizer(acceptingValidator);
        String atLimit = "a".repeat(248) + "@x.com";
        String overLimit = "a".repeat(249) + "@x.com";

        assertThat(atLimit.codePointCount(0, atLimit.length())).isEqualTo(254);
        assertThat(lengthOnly.normalize(atLimit)).isEqualTo(atLimit);
        assertThatThrownBy(() -> lengthOnly.normalize(overLimit))
                .isInstanceOfSatisfying(NewsletterException.class, exception ->
                        assertThat(exception.getCode())
                                .isEqualTo(ErrorStatus._NEWSLETTER_EMAIL_TOO_LONG));
    }

    private void assertError(String value, ErrorStatus expected) {
        assertThatThrownBy(() -> normalizer.normalize(value))
                .isInstanceOfSatisfying(NewsletterException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(expected));
    }
}
