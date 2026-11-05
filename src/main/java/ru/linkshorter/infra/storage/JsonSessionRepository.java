package ru.linkshorter.infra.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import ru.linkshorter.core.exception.StorageException;
import ru.linkshorter.core.port.SessionRepository;

public class JsonSessionRepository implements SessionRepository {

    private final Path file;
    private final ObjectMapper mapper;

    public JsonSessionRepository(Path file, ObjectMapper mapper) {
        this.file = file;
        this.mapper = mapper;
    }

    @Override
    public Optional<UUID> currentUser() {
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            SessionState state = mapper.readValue(file.toFile(), SessionState.class);
            if (state == null || state.currentUser() == null || state.currentUser().isBlank()) {
                return Optional.empty();
            }
            return Optional.of(UUID.fromString(state.currentUser()));
        } catch (IOException e) {
            throw new StorageException("Не удалось прочитать файл сессии " + file.toAbsolutePath(), e);
        } catch (IllegalArgumentException e) {
            throw new StorageException(
                    "Файл сессии повреждён: " + file.toAbsolutePath() + ". Удалите его и запустите заново.", e);
        }
    }

    @Override
    public void saveCurrentUser(UUID uuid) {
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            mapper.writeValue(file.toFile(), new SessionState(uuid.toString()));
        } catch (IOException e) {
            throw new StorageException("Не удалось сохранить файл сессии " + file.toAbsolutePath(), e);
        }
    }

    public record SessionState(String currentUser) {
    }
}
