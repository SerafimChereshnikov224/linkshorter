package ru.linkshorter.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class AppConfigTest {

    @Test
    void loadsConfigurationFromPropertiesFiles() {
        var config = AppConfig.load();

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
}
