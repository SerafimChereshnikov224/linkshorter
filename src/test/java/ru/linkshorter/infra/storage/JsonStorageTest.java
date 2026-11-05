package ru.linkshorter.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static ru.linkshorter.core.model.LinkStatus.ACTIVE;
import static ru.linkshorter.core.model.NotificationType.EXPIRED;
import static ru.linkshorter.core.model.NotificationType.LIMIT_REACHED;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.linkshorter.config.JsonConfig;
import ru.linkshorter.core.exception.LinkShorterException;
import ru.linkshorter.core.model.Link;
import ru.linkshorter.core.model.Notification;

class JsonStorageTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    private final ObjectMapper mapper = JsonConfig.mapper();

    @TempDir
    Path tempDir;

    @Test
    void linksSurviveRepositoryRestart() {
        var file = tempDir.resolve("links.json");
        var link = Link.builder()
                .code("abc1234")
                .originalUrl("https://example.com")
                .ownerUuid("user-1")
                .createdAt(NOW)
                .expiresAt(NOW.plusSeconds(3600))
                .maxVisits(5L)
                .build();

        new JsonLinkRepository(file, mapper).save(link);

        var reloaded = new JsonLinkRepository(file, mapper);
        var restored = reloaded.findByCode("abc1234").orElseThrow();
        assertThat(restored.getOriginalUrl()).isEqualTo("https://example.com");
        assertThat(restored.getOwnerUuid()).isEqualTo("user-1");
        assertThat(restored.getMaxVisits()).isEqualTo(5L);
        assertThat(restored.getExpiresAt()).isEqualTo(NOW.plusSeconds(3600));
        assertThat(restored.status(NOW)).isEqualTo(ACTIVE);
    }

    @Test
    void sessionSurvivesRestart() {
        var file = tempDir.resolve("session.json");
        var user = UUID.randomUUID();

        new JsonSessionRepository(file, mapper).saveCurrentUser(user);

        assertThat(new JsonSessionRepository(file, mapper).currentUser()).contains(user);
    }

    @Test
    void notificationsSurviveRestartAndCanBeMarkedRead() {
        var file = tempDir.resolve("notifications.json");
        var repository = new JsonNotificationRepository(file, mapper);
        repository.save(Notification.builder()
                .id("n-1")
                .ownerUuid("user-1")
                .linkCode("abc1234")
                .type(LIMIT_REACHED)
                .message("limit")
                .createdAt(NOW)
                .build());
        repository.save(Notification.builder()
                .id("n-2")
                .ownerUuid("user-2")
                .linkCode("def5678")
                .type(EXPIRED)
                .message("expired")
                .createdAt(NOW)
                .build());

        assertThat(repository.findByOwner("user-1")).hasSize(1);
        assertThat(repository.countUnreadByOwner("user-1")).isEqualTo(1);

        repository.markAllReadByOwner("user-1", NOW.plusSeconds(10));
        var reloaded = new JsonNotificationRepository(file, mapper);

        assertThat(reloaded.countUnreadByOwner("user-1")).isZero();
        assertThat(reloaded.countUnreadByOwner("user-2")).isEqualTo(1);
        assertThat(reloaded.findByOwner("user-1").get(0).isRead()).isTrue();
    }

    @Test
    void corruptedStorageFileProducesStorageException() throws IOException {
        var file = tempDir.resolve("links.json");
        Files.writeString(file, "not a json", StandardCharsets.UTF_8);

        assertThatThrownBy(() -> new JsonLinkRepository(file, mapper))
                .isInstanceOf(LinkShorterException.class);
    }
}
