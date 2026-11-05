package ru.linkshorter.infra.storage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import ru.linkshorter.core.exception.StorageException;
import ru.linkshorter.core.model.Notification;
import ru.linkshorter.core.port.NotificationRepository;

public class JsonNotificationRepository implements NotificationRepository {

    private final Path file;
    private final ObjectMapper mapper;
    private final Map<String, Notification> notifications = new LinkedHashMap<>();

    public JsonNotificationRepository(Path file, ObjectMapper mapper) {
        this.file = file;
        this.mapper = mapper;
        load();
    }

    @Override
    public Notification save(Notification notification) {
        notifications.put(notification.getId(), notification);
        persist();
        return notification;
    }

    @Override
    public List<Notification> findByOwner(String ownerUuid) {
        return notifications.values().stream()
                .filter(notification -> notification.getOwnerUuid().equals(ownerUuid))
                .sorted((left, right) -> right.getCreatedAt().compareTo(left.getCreatedAt()))
                .toList();
    }

    @Override
    public long countUnreadByOwner(String ownerUuid) {
        return notifications.values().stream()
                .filter(notification -> notification.getOwnerUuid().equals(ownerUuid))
                .filter(notification -> !notification.isRead())
                .count();
    }

    @Override
    public void markAllReadByOwner(String ownerUuid, Instant readAt) {
        boolean changed = false;
        for (Notification notification : notifications.values()) {
            if (notification.getOwnerUuid().equals(ownerUuid) && !notification.isRead()) {
                notification.setReadAt(readAt);
                changed = true;
            }
        }
        if (changed) {
            persist();
        }
    }

    private void load() {
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            List<Notification> stored =
                    mapper.readValue(file.toFile(), new TypeReference<List<Notification>>() {
                    });
            for (Notification notification : stored) {
                notifications.put(notification.getId(), notification);
            }
        } catch (IOException e) {
            throw new StorageException(
                    "Не удалось прочитать хранилище уведомлений " + file.toAbsolutePath(), e);
        }
    }

    private void persist() {
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            mapper.writeValue(file.toFile(), new ArrayList<>(notifications.values()));
        } catch (IOException e) {
            throw new StorageException(
                    "Не удалось сохранить хранилище уведомлений " + file.toAbsolutePath(), e);
        }
    }
}
