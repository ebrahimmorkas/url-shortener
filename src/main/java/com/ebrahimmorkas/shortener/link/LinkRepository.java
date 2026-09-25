package com.ebrahimmorkas.shortener.link;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface LinkRepository extends JpaRepository<Link, Long> {

    @Query(value = "select nextval('links_id_seq')", nativeQuery = true)
    long nextId();

    Optional<Link> findByCode(String code);

    boolean existsByCode(String code);
}
