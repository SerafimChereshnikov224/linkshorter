package ru.linkshorter.support;

import java.util.Optional;
import java.util.UUID;
import ru.linkshorter.core.port.SessionRepository;

public class InMemorySessionRepository implements SessionRepository {

    private UUID currentUser;

    @Override
    public Optional<UUID> currentUser() {
        return Optional.ofNullable(currentUser);
    }

    @Override
    public void saveCurrentUser(UUID uuid) {
        this.currentUser = uuid;
    }
}
