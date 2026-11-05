package ru.linkshorter.support;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import ru.linkshorter.core.model.Link;
import ru.linkshorter.core.port.LinkRepository;

public class InMemoryLinkRepository implements LinkRepository {

    private final Map<String, Link> storage = new LinkedHashMap<>();

    @Override
    public Link save(Link link) {
        storage.put(link.getCode(), link);
        return link;
    }

    @Override
    public Optional<Link> findByCode(String code) {
        return Optional.ofNullable(storage.get(code));
    }

    @Override
    public List<Link> findByOwner(String ownerUuid) {
        return storage.values().stream()
                .filter(link -> link.getOwnerUuid().equals(ownerUuid))
                .toList();
    }

    @Override
    public List<Link> findAll() {
        return new ArrayList<>(storage.values());
    }

    @Override
    public boolean deleteByCode(String code) {
        return storage.remove(code) != null;
    }

    @Override
    public boolean existsByCode(String code) {
        return storage.containsKey(code);
    }
}
