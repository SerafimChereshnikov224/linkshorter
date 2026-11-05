package ru.linkshorter.core.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import ru.linkshorter.core.exception.ValidationException;

public class UrlValidator {

    private static final int MAX_LENGTH = 2048;

    public String validate(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new ValidationException("URL не может быть пустым.");
        }
        String url = rawUrl.trim();
        if (url.length() > MAX_LENGTH) {
            throw new ValidationException("URL слишком длинный: максимум " + MAX_LENGTH + " символов.");
        }
        if (url.contains(" ")) {
            throw new ValidationException("URL не должен содержать пробелы.");
        }

        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new ValidationException("Некорректный URL: " + e.getMessage());
        }

        String scheme = uri.getScheme();
        if (scheme == null || !isHttpScheme(scheme)) {
            throw new ValidationException("Поддерживаются только ссылки с http или https.");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new ValidationException("URL должен содержать имя хоста.");
        }
        return url;
    }

    private boolean isHttpScheme(String scheme) {
        String normalized = scheme.toLowerCase(Locale.ROOT);
        return normalized.equals("http") || normalized.equals("https");
    }
}
