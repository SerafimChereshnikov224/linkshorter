package ru.linkshorter.core.service;

import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import ru.linkshorter.core.repository.SessionRepository;

@RequiredArgsConstructor
public class SessionService {

    private final SessionRepository sessionRepository;

    public UUID currentUser() {
        var existing = sessionRepository.currentUser();
        if (existing.isPresent()) {
            return existing.get();
        }
        var generated = UUID.randomUUID();
        sessionRepository.saveCurrentUser(generated);
        return generated;
    }

    public UUID login(UUID uuid) {
        sessionRepository.saveCurrentUser(uuid);
        return uuid;
    }

    public Optional<UUID> peekUser() {
        return sessionRepository.currentUser();
    }
}
