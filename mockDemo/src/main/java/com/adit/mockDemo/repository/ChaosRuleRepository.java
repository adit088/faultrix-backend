package com.adit.mockDemo.repository;

import com.adit.mockDemo.chaos.execution.TargetingMode;
import com.adit.mockDemo.entity.ChaosRuleEntity;
import com.adit.mockDemo.entity.Organization;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChaosRuleRepository extends JpaRepository<ChaosRuleEntity, Long> {

    // ── Tenant-scoped ────────────────────────────────────────────────────────

    List<ChaosRuleEntity> findByOrganization(Organization organization);

    List<ChaosRuleEntity> findByOrganizationAndEnabledTrue(Organization organization);

    Optional<ChaosRuleEntity> findByOrganizationAndTarget(Organization organization, String target);

    Optional<ChaosRuleEntity> findByOrganizationAndId(Organization organization, Long id);

    long countByOrganization(Organization organization);

    long countByOrganizationAndEnabledTrue(Organization organization);

    // ── Advanced targeting ───────────────────────────────────────────────────

    @Query("""
            SELECT c FROM ChaosRuleEntity c
            WHERE c.organization = :org
            AND c.enabled = true
            AND c.targetingMode <> com.adit.mockDemo.chaos.execution.TargetingMode.EXACT
            ORDER BY c.id ASC
            """)
    List<ChaosRuleEntity> findAdvancedTargetingRules(@Param("org") Organization organization);

    // ── Paginated ────────────────────────────────────────────────────────────

    @Query("""
            SELECT c FROM ChaosRuleEntity c WHERE c.organization = :org AND
            (:cursor IS NULL OR c.id > :cursor) AND
            (:enabled IS NULL OR c.enabled = :enabled) AND
            (:tags IS NULL OR c.tags LIKE CONCAT('%', :tags, '%')) AND
            (:search IS NULL OR LOWER(c.target) LIKE LOWER(CONCAT('%', :search, '%')) OR
            LOWER(c.description) LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    List<ChaosRuleEntity> findPaginated(
            @Param("org")     Organization organization,
            @Param("cursor")  Long cursor,
            @Param("enabled") Boolean enabled,
            @Param("tags")    String tags,
            @Param("search")  String search,
            Pageable pageable
    );

    @Query("""
            SELECT COUNT(c) FROM ChaosRuleEntity c WHERE c.organization = :org AND
            (:enabled IS NULL OR c.enabled = :enabled) AND
            (:tags IS NULL OR c.tags LIKE CONCAT('%', :tags, '%')) AND
            (:search IS NULL OR LOWER(c.target) LIKE LOWER(CONCAT('%', :search, '%')) OR
            LOWER(c.description) LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    long countFiltered(
            @Param("org")     Organization organization,
            @Param("enabled") Boolean enabled,
            @Param("tags")    String tags,
            @Param("search")  String search
    );

    // ── Legacy ───────────────────────────────────────────────────────────────

    @Query("SELECT c FROM ChaosRuleEntity c WHERE c.target = :target")
    Optional<ChaosRuleEntity> findByTarget(@Param("target") String target);
}