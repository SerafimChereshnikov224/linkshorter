package ru.linkshorter.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import ru.linkshorter.core.exception.ValidationException;

class AppConfigTest {

    @Test
    void loadsConfigurationFromPropertiesFiles() {
        AppConfig config = AppConfig.load();

        assertThat(config.linkTtl()).isEqualTo(Duration.ofMinutes(1440));
        assertThat(config.defaultMaxVisits()).isEqualTo(100L);
        assertThat(config.maxAllowedVisits()).isEqualTo(1_000_000L);
        assertThat(config.shortLinkPrefix()).isEqualTo("sho.rt/");
    }

    @Test
    void systemPropertyOverridesFileValue() {
        System.setProperty("link.ttl.minutes", "5");
        try {
            assertThat(AppConfig.load().linkTtl()).isEqualTo(Duration.ofMinutes(5));
        } finally {
            System.clearProperty("link.ttl.minutes");
        }
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThatThrownBy(() ->
                        new AppConfig(Duration.ZERO, 100, 1_000_000, Path.of("data"), "sho.rt/"))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() ->
                        new AppConfig(Duration.ofMinutes(1), 200, 100, Path.of("data"), "sho.rt/"))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() ->
                        new AppConfig(Duration.ofMinutes(1), 0, 100, Path.of("data"), "sho.rt/"))
                .isInstanceOf(ValidationException.class);
    }
}
