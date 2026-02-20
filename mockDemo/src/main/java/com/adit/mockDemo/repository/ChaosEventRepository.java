package com.adit.mockDemo.repository;

import com.adit.mockDemo.entity.ChaosEvent;
import com.adit.mockDemo.entity.Organization;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface ChaosEventRepository extends JpaRepository<ChaosEvent, Long> {

    // Native query — avoids PostgreSQL's inability to type-infer null Boolean parameters in JPQL.
    // CAST(:injected AS boolean) tells Postgres the type even when the value is NULL.
    @Query(value = """
            SELECT * FROM chaos_events e
            WHERE e.organization_id = :orgId
            AND (:target   IS NULL OR e.target       = :target)
            AND (:from     IS NULL OR e.occurred_at  >= CAST(:from AS timestamptz))
            AND (:to       IS NULL OR e.occurred_at  <= CAST(:to   AS timestamptz))
            AND (CAST(:injected AS boolean) IS NULL OR e.injected = CAST(:injected AS boolean))
            ORDER BY e.occurred_at DESC
            """, nativeQuery = true)
    List<ChaosEvent> findEvents(
            @Param("orgId")    Long orgId,
            @Param("target")   String target,
            @Param("from")     Instant from,
            @Param("to")       Instant to,
            @Param("injected") Boolean injected,
            Pageable pageable
    );

    long countByOrganizationAndOccurredAtBetween(
            Organization org, Instant from, Instant to);

    @Query("""
            SELECT COUNT(e) FROM ChaosEvent e
            WHERE e.organization = :org
            AND e.injected = true
            AND e.occurredAt BETWEEN :from AND :to
            """)
    long countInjectedBetween(
            @Param("org")  Organization org,
            @Param("from") Instant from,
            @Param("to")   Instant to
    );

    @Query("""
            SELECT e.target, COUNT(e) as cnt
            FROM ChaosEvent e
            WHERE e.organization = :org
            AND e.injected = true
            AND e.occurredAt BETWEEN :from AND :to
            GROUP BY e.target
            ORDER BY cnt DESC
            """)
    List<Object[]> findTopTargets(
            @Param("org")  Organization org,
            @Param("from") Instant from,
            @Param("to")   Instant to,
            Pageable pageable
    );

    @Query("""
            SELECT e.chaosType, COUNT(e) as cnt
            FROM ChaosEvent e
            WHERE e.organization = :org
            AND e.injected = true
            AND e.occurredAt BETWEEN :from AND :to
            GROUP BY e.chaosType
            ORDER BY cnt DESC
            """)
    List<Object[]> findTypeBreakdown(
            @Param("org")  Organization org,
            @Param("from") Instant from,
            @Param("to")   Instant to
    );

    // Native PostgreSQL query — EXTRACT returns numeric, mapped as Double in Java
    @Query(value = """
            SELECT
                EXTRACT(HOUR FROM occurred_at)  AS hr,
                COUNT(*)                         AS total,
                SUM(CASE WHEN injected = TRUE THEN 1 ELSE 0 END) AS injected,
                AVG(delay_ms)                    AS avg_delay_ms
            FROM chaos_events
            WHERE organization_id = :orgId
            AND occurred_at >= CAST(:from AS timestamptz)
            GROUP BY EXTRACT(HOUR FROM occurred_at)
            ORDER BY EXTRACT(HOUR FROM occurred_at) ASC
            """, nativeQuery = true)
    List<Object[]> findHourlyTimeSeries(
            @Param("orgId") Long orgId,
            @Param("from")  Instant from
    );

    @Query("""
            SELECT AVG(e.delayMs) FROM ChaosEvent e
            WHERE e.organization = :org
            AND e.delayMs > 0
            AND e.occurredAt BETWEEN :from AND :to
            """)
    Double findAvgInjectedLatency(
            @Param("org")  Organization org,
            @Param("from") Instant from,
            @Param("to")   Instant to
    );

    @Query("""
            SELECT e FROM ChaosEvent e
            WHERE e.organization = :org
            AND e.target = :target
            ORDER BY e.occurredAt DESC
            """)
    List<ChaosEvent> findRecentByOrganizationAndTarget(
            @Param("org") Organization org,
            @Param("target") String target,
            Pageable pageable
    );
}