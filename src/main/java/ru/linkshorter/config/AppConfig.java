package ru.linkshorter.config;

import static ru.linkshorter.core.exception.LinkShorterException.Type.STORAGE;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;
import lombok.RequiredArgsConstructor;
import ru.linkshorter.core.exception.LinkShorterException;

@RequiredArgsConstructor
public class AppConfig {

    private final Duration linkTtl;
    private final long defaultMaxVisits;
    private final long maxAllowedVisits;
    private final int shortCodeLength;
    private final Path storageDir;
    private final String shortLinkPrefix;

    public static AppConfig load() {
        var properties = new Properties();
        loadFromClasspath(properties);
        loadFromWorkingDirectory(properties);
        applySystemProperties(properties);

        return new AppConfig(
                Duration.ofMinutes(Long.parseLong(properties.getProperty("link.ttl.minutes", "1440"))),
                Long.parseLong(properties.getProperty("link.max-visits.default", "100")),
                Long.parseLong(properties.getProperty("link.max-visits.max", "1000000")),
                Integer.parseInt(properties.getProperty("shortlink.code.length", "7")),
                Path.of(properties.getProperty("storage.dir", "data")),
                properties.getProperty("shortlink.prefix", "sho.rt/"));
    }

    private static void loadFromClasspath(Properties target) {
        try (var input = AppConfig.class.getResourceAsStream("/application.properties")) {
            if (input != null) {
                target.load(new InputStreamReader(input, StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new LinkShorterException(STORAGE, "Не удалось прочитать application.properties", e);
        }
    }

    private static void loadFromWorkingDirectory(Properties target) {
        var external = Path.of("application.properties");
        if (!Files.isRegularFile(external)) {
            return;
        }
        try (var reader = Files.newBufferedReader(external, StandardCharsets.UTF_8)) {
            target.load(reader);
        } catch (IOException e) {
            throw new LinkShorterException(
                    STORAGE, "Не удалось прочитать " + external.toAbsolutePath(), e);
        }
    }

    private static void applySystemProperties(Properties target) {
        for (var key : target.stringPropertyNames()) {
            var override = System.getProperty(key);
            if (override != null) {
                target.setProperty(key, override);
            }
        }
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

    public int shortCodeLength() {
        return shortCodeLength;
    }

    public Path storageDir() {
        return storageDir;
    }

    public String shortLinkPrefix() {
        return shortLinkPrefix;
    }
}
