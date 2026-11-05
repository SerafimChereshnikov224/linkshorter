package ru.linkshorter.core.service;

import static ru.linkshorter.core.exception.LinkShorterException.Type.VALIDATION;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import ru.linkshorter.core.exception.LinkShorterException;

public class UrlValidator {

    private static final int MAX_LENGTH = 2048;

    public String validate(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new LinkShorterException(VALIDATION, "URL не может быть пустым.");
        }
        var url = rawUrl.trim();
        if (url.length() > MAX_LENGTH) {
            throw new LinkShorterException(
                    VALIDATION, "URL слишком длинный: максимум " + MAX_LENGTH + " символов.");
        }
        if (url.contains(" ")) {
            throw new LinkShorterException(VALIDATION, "URL не должен содержать пробелы.");
        }

        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new LinkShorterException(VALIDATION, "Некорректный URL: " + e.getMessage());
        }

        var scheme = uri.getScheme();
        if (scheme == null || !isHttpScheme(scheme)) {
            throw new LinkShorterException(VALIDATION, "Поддерживаются только ссылки с http или https.");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new LinkShorterException(VALIDATION, "URL должен содержать имя хоста.");
        }
        return url;
    }

    private boolean isHttpScheme(String scheme) {
        var normalized = scheme.toLowerCase(Locale.ROOT);
        return normalized.equals("http") || normalized.equals("https");
    }
}
