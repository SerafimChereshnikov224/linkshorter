package ru.linkshorter.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;
import ru.linkshorter.core.exception.ValidationException;

public final class AppConfig {

    public static final String FILE_NAME = "application.properties";

    private static final String KEY_TTL_MINUTES = "link.ttl.minutes";
    private static final String KEY_DEFAULT_MAX_VISITS = "link.max-visits.default";
    private static final String KEY_MAX_VISITS_LIMIT = "link.max-visits.max";
    private static final String KEY_STORAGE_DIR = "storage.dir";
    private static final String KEY_PREFIX = "shortlink.prefix";

    private final Duration linkTtl;
    private final long defaultMaxVisits;
    private final long maxAllowedVisits;
    private final Path storageDir;
    private final String shortLinkPrefix;

    public AppConfig(
            Duration linkTtl,
            long defaultMaxVisits,
            long maxAllowedVisits,
            Path storageDir,
            String shortLinkPrefix) {
        this.linkTtl = requirePositive(linkTtl, "TTL ссылки");
        this.maxAllowedVisits = requirePositive(maxAllowedVisits, "Максимально допустимый лимит переходов");
        this.defaultMaxVisits = requirePositive(defaultMaxVisits, "Лимит переходов по умолчанию");
        if (defaultMaxVisits > maxAllowedVisits) {
            throw new ValidationException(
                    "Лимит по умолчанию (" + defaultMaxVisits
                            + ") не может превышать максимально допустимый (" + maxAllowedVisits + ").");
        }
        this.storageDir = storageDir;
        this.shortLinkPrefix = shortLinkPrefix;
    }

    public static AppConfig load() {
        Properties properties = defaults();
        loadFromClasspath(properties);
        loadFromWorkingDirectory(properties);
        applySystemProperties(properties);

        return new AppConfig(
                Duration.ofMinutes(readLong(properties, KEY_TTL_MINUTES)),
                readLong(properties, KEY_DEFAULT_MAX_VISITS),
                readLong(properties, KEY_MAX_VISITS_LIMIT),
                Path.of(requireText(properties, KEY_STORAGE_DIR)),
                requireText(properties, KEY_PREFIX));
    }

    private static Properties defaults() {
        Properties properties = new Properties();
        properties.setProperty(KEY_TTL_MINUTES, "1440");
        properties.setProperty(KEY_DEFAULT_MAX_VISITS, "100");
        properties.setProperty(KEY_MAX_VISITS_LIMIT, "1000000");
        properties.setProperty(KEY_STORAGE_DIR, "data");
        properties.setProperty(KEY_PREFIX, "sho.rt/");
        return properties;
    }

    private static void loadFromClasspath(Properties target) {
        try (InputStream input = AppConfig.class.getResourceAsStream("/" + FILE_NAME)) {
            if (input != null) {
                Properties loaded = new Properties();
                loaded.load(new java.io.InputStreamReader(input, StandardCharsets.UTF_8));
                target.putAll(loaded);
            }
        } catch (IOException e) {
            throw new ValidationException("Не удалось прочитать " + FILE_NAME + " из classpath: " + e.getMessage());
        }
    }

    private static void loadFromWorkingDirectory(Properties target) {
        Path external = Path.of(FILE_NAME);
        if (!Files.isRegularFile(external)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(external, StandardCharsets.UTF_8)) {
            Properties loaded = new Properties();
            loaded.load(reader);
            target.putAll(loaded);
        } catch (IOException e) {
            throw new ValidationException("Не удалось прочитать " + external.toAbsolutePath() + ": " + e.getMessage());
        }
    }

    private static void applySystemProperties(Properties target) {
        for (String key : target.stringPropertyNames()) {
            String override = System.getProperty(key);
            if (override != null) {
                target.setProperty(key, override);
            }
        }
    }

    private static long readLong(Properties properties, String key) {
        String raw = requireText(properties, key);
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            throw new ValidationException("Параметр '" + key + "' должен быть целым числом, получено: " + raw);
        }
    }

    private static String requireText(Properties properties, String key) {
        String raw = properties.getProperty(key);
        if (raw == null || raw.isBlank()) {
            throw new ValidationException("Не задан обязательный параметр конфигурации: " + key);
        }
        return raw.trim();
    }

    private static Duration requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new ValidationException(name + " должен быть положительным.");
        }
        return value;
    }

    private static long requirePositive(long value, String name) {
        if (value <= 0) {
            throw new ValidationException(name + " должен быть положительным.");
        }
        return value;
    }

    public Duration linkTtl() {
        return linkTtl;
    }

    public long defaultMaxVisits() {
        return defaultMaxVisits;
    }

    public long maxAllowedVisits() {
        return maxAllowedVisits;
    }

    public Path storageDir() {
        return storageDir;
    }

    public String shortLinkPrefix() {
        return shortLinkPrefix;
    }
}
