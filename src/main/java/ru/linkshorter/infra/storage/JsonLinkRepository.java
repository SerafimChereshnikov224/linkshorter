package ru.linkshorter.infra.storage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import ru.linkshorter.core.exception.StorageException;
import ru.linkshorter.core.model.Link;
import ru.linkshorter.core.port.LinkRepository;

public class JsonLinkRepository implements LinkRepository {

    private final Path file;
    private final ObjectMapper mapper;
    private final Map<String, Link> links = new LinkedHashMap<>();

    public JsonLinkRepository(Path file, ObjectMapper mapper) {
        this.file = file;
        this.mapper = mapper;
        load();
    }

    @Override
    public Link save(Link link) {
        links.put(link.getCode(), link);
        persist();
        return link;
    }

    @Override
    public Optional<Link> findByCode(String code) {
        return Optional.ofNullable(links.get(code));
    }

    @Override
    public List<Link> findByOwner(String ownerUuid) {
        return links.values().stream()
                .filter(link -> link.getOwnerUuid().equals(ownerUuid))
                .toList();
    }

    @Override
    public List<Link> findAll() {
        return new ArrayList<>(links.values());
    }

    @Override
    public boolean deleteByCode(String code) {
        boolean removed = links.remove(code) != null;
        if (removed) {
            persist();
        }
        return removed;
    }

    @Override
    public boolean existsByCode(String code) {
        return links.containsKey(code);
    }

    private void load() {
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            List<Link> stored = mapper.readValue(file.toFile(), new TypeReference<List<Link>>() {
            });
            for (Link link : stored) {
                links.put(link.getCode(), link);
            }
        } catch (IOException e) {
            throw new StorageException("Не удалось прочитать хранилище ссылок " + file.toAbsolutePath(), e);
        }
    }

    private void persist() {
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            mapper.writeValue(file.toFile(), new ArrayList<>(links.values()));
        } catch (IOException e) {
            throw new StorageException("Не удалось сохранить хранилище ссылок " + file.toAbsolutePath(), e);
        }
    }
}
