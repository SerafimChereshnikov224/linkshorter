package ru.linkshorter.core.repository;

import java.util.List;
import java.util.Optional;
import ru.linkshorter.core.model.Link;

public interface LinkRepository {

    Link save(Link link);

    Optional<Link> findByCode(String code);

    List<Link> findByOwner(String ownerUuid);

    List<Link> findAll();

    boolean deleteByCode(String code);

    boolean existsByCode(String code);
}
