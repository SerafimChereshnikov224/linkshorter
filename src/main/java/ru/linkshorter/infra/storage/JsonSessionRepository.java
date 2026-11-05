package ru.linkshorter.infra.storage;

import static ru.linkshorter.core.exception.LinkShorterException.Type.STORAGE;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import ru.linkshorter.core.exception.LinkShorterException;
import ru.linkshorter.core.repository.SessionRepository;

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
            var state = mapper.readValue(file.toFile(), SessionState.class);
            if (state == null || state.currentUser() == null || state.currentUser().isBlank()) {
                return Optional.empty();
            }
            return Optional.of(UUID.fromString(state.currentUser()));
        } catch (IOException | IllegalArgumentException e) {
            throw new LinkShorterException(
                    STORAGE,
                    "Не удалось прочитать файл сессии " + file.toAbsolutePath()
                            + ". Удалите его и запустите заново.",
                    e);
        }
    }

    @Override
    public void saveCurrentUser(UUID uuid) {
        try {
            var parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            mapper.writeValue(file.toFile(), new SessionState(uuid.toString()));
        } catch (IOException e) {
            throw new LinkShorterException(
                    STORAGE, "Не удалось сохранить файл сессии " + file.toAbsolutePath(), e);
        }
    }

    public record SessionState(String currentUser) {
    }
}
