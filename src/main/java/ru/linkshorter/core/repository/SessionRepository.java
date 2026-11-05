package ru.linkshorter.core.repository;

import java.util.Optional;
import java.util.UUID;

public interface SessionRepository {

    Optional<UUID> currentUser();

    void saveCurrentUser(UUID uuid);
}
