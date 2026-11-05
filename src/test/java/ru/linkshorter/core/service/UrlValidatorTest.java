package ru.linkshorter.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import ru.linkshorter.core.exception.LinkShorterException;

class UrlValidatorTest {

    private final UrlValidator validator = new UrlValidator();

    @Test
    void acceptsHttpsUrl() {
        var url = "https://www.baeldung.com/java-9-http-client";

        assertThat(validator.validate(url)).isEqualTo(url);
    }

    @Test
    void acceptsHttpUrl() {
        var url = "http://example.com/path?query=1";

        assertThat(validator.validate(url)).isEqualTo(url);
    }

    @Test
    void trimsSurroundingWhitespace() {
        assertThat(validator.validate("  https://example.com  ")).isEqualTo("https://example.com");
    }

    @Test
    void rejectsBlankUrl() {
        assertThatThrownBy(() -> validator.validate("   "))
                .isInstanceOf(LinkShorterException.class)
                .hasMessageContaining("пустым");
    }

    @Test
    void rejectsNonHttpScheme() {
        assertThatThrownBy(() -> validator.validate("ftp://example.com/file"))
                .isInstanceOf(LinkShorterException.class)
                .hasMessageContaining("http");
    }

    @Test
    void rejectsUrlWithoutHost() {
        assertThatThrownBy(() -> validator.validate("https://"))
                .isInstanceOf(LinkShorterException.class);
    }

    @Test
    void rejectsUrlWithSpaces() {
        assertThatThrownBy(() -> validator.validate("https://example.com/a b"))
                .isInstanceOf(LinkShorterException.class)
                .hasMessageContaining("пробелы");
    }

    @Test
    void rejectsTooLongUrl() {
        var url = "https://example.com/" + "a".repeat(2048);

        assertThatThrownBy(() -> validator.validate(url))
                .isInstanceOf(LinkShorterException.class)
                .hasMessageContaining("длинный");
    }
}
