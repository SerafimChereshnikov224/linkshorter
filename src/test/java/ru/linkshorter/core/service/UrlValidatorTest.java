package ru.linkshorter.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import ru.linkshorter.core.exception.ValidationException;

class UrlValidatorTest {

    private final UrlValidator validator = new UrlValidator();

    @Test
    void acceptsHttpsUrl() {
        String url = "https://www.baeldung.com/java-9-http-client";

        assertThat(validator.validate(url)).isEqualTo(url);
    }

    @Test
    void acceptsHttpUrl() {
        String url = "http://example.com/path?query=1";

        assertThat(validator.validate(url)).isEqualTo(url);
    }

    @Test
    void trimsSurroundingWhitespace() {
        assertThat(validator.validate("  https://example.com  ")).isEqualTo("https://example.com");
    }

    @Test
    void rejectsBlankUrl() {
        assertThatThrownBy(() -> validator.validate("   "))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("пустым");
    }

    @Test
    void rejectsNonHttpScheme() {
        assertThatThrownBy(() -> validator.validate("ftp://example.com/file"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("http");
    }

    @Test
    void rejectsUrlWithoutHost() {
        assertThatThrownBy(() -> validator.validate("https://"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void rejectsUrlWithSpaces() {
        assertThatThrownBy(() -> validator.validate("https://example.com/a b"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("пробелы");
    }

    @Test
    void rejectsTooLongUrl() {
        String url = "https://example.com/" + "a".repeat(2048);

        assertThatThrownBy(() -> validator.validate(url))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("длинный");
    }
}
