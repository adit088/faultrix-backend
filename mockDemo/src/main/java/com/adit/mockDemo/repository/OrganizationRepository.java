package com.adit.mockDemo.repository;

import com.adit.mockDemo.entity.Organization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrganizationRepository extends JpaRepository<Organization, Long> {

    Optional<Organization> findByApiKey(String apiKey);

    Optional<Organization> findBySlug(String slug);

    boolean existsBySlug(String slug);
}