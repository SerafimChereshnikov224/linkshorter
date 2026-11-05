package ru.linkshorter.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.linkshorter.core.exception.StorageException;
import ru.linkshorter.core.model.Link;
import ru.linkshorter.core.model.LinkStatus;
import ru.linkshorter.core.model.Notification;
import ru.linkshorter.core.model.NotificationType;

class JsonStorageTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    private final ObjectMapper mapper = JsonSupport.mapper();

    @TempDir
    Path tempDir;

    @Test
    void linksSurviveRepositoryRestart() {
        Path file = tempDir.resolve("links.json");
        Link link = new Link(
                "abc1234", "https://example.com", "user-1", NOW, NOW.plusSeconds(3600), 5L);

        new JsonLinkRepository(file, mapper).save(link);

        JsonLinkRepository reloaded = new JsonLinkRepository(file, mapper);
        Link restored = reloaded.findByCode("abc1234").orElseThrow();
        assertThat(restored.getOriginalUrl()).isEqualTo("https://example.com");
        assertThat(restored.getOwnerUuid()).isEqualTo("user-1");
        assertThat(restored.getMaxVisits()).isEqualTo(5L);
        assertThat(restored.getExpiresAt()).isEqualTo(NOW.plusSeconds(3600));
        assertThat(restored.status(NOW)).isEqualTo(LinkStatus.ACTIVE);
    }

    @Test
    void sessionSurvivesRestart() {
        Path file = tempDir.resolve("session.json");
        UUID user = UUID.randomUUID();

        new JsonSessionRepository(file, mapper).saveCurrentUser(user);

        assertThat(new JsonSessionRepository(file, mapper).currentUser()).contains(user);
    }

    @Test
    void notificationsSurviveRestartAndCanBeMarkedRead() {
        Path file = tempDir.resolve("notifications.json");
        JsonNotificationRepository repository = new JsonNotificationRepository(file, mapper);
        repository.save(new Notification(
                "n-1", "user-1", "abc1234", NotificationType.LIMIT_REACHED, "limit", NOW));
        repository.save(new Notification(
                "n-2", "user-2", "def5678", NotificationType.EXPIRED, "expired", NOW));

        assertThat(repository.findByOwner("user-1")).hasSize(1);
        assertThat(repository.countUnreadByOwner("user-1")).isEqualTo(1);

        repository.markAllReadByOwner("user-1", NOW.plusSeconds(10));
        JsonNotificationRepository reloaded = new JsonNotificationRepository(file, mapper);

        assertThat(reloaded.countUnreadByOwner("user-1")).isZero();
        assertThat(reloaded.countUnreadByOwner("user-2")).isEqualTo(1);
        assertThat(reloaded.findByOwner("user-1").get(0).isRead()).isTrue();
    }

    @Test
    void corruptedStorageFileProducesStorageException() throws IOException {
        Path file = tempDir.resolve("links.json");
        Files.writeString(file, "not a json", StandardCharsets.UTF_8);

        assertThatThrownBy(() -> new JsonLinkRepository(file, mapper))
                .isInstanceOf(StorageException.class);
    }
}
