package com.adit.mockDemo.service;

import com.adit.mockDemo.chaos.ChaosRule;
import com.adit.mockDemo.chaos.execution.TargetMatcher;
import com.adit.mockDemo.chaos.execution.TargetingMode;
import com.adit.mockDemo.dto.ChaosRuleRequest;
import com.adit.mockDemo.dto.ChaosRuleResponse;
import com.adit.mockDemo.dto.ChaosRuleStats;
import com.adit.mockDemo.dto.PageRequest;
import com.adit.mockDemo.dto.PageResponse;
import com.adit.mockDemo.entity.ChaosRuleEntity;
import com.adit.mockDemo.entity.Organization;
import com.adit.mockDemo.exception.ResourceNotFoundException;
import com.adit.mockDemo.exception.ValidationException;
import com.adit.mockDemo.repository.ChaosRuleRepository;
import com.adit.mockDemo.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class ChaosRuleService {

    private final ChaosRuleRepository chaosRuleRepository;
    private final TenantContext        tenantContext;
    private final TargetMatcher        targetMatcher;

    // ================ READ ================

    @Cacheable(value = "chaosRules", key = "#organization.id + ':all'")
    @Transactional(readOnly = true)
    public List<ChaosRuleResponse> getAllRules(Organization organization) {
        log.info("GET /api/v1/chaos/rules - Fetching all rules for org: {}", organization.getSlug());
        return chaosRuleRepository.findByOrganization(organization)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Cacheable(value = "chaosRules", key = "#organization.id + ':id:' + #id")
    @Transactional(readOnly = true)
    public ChaosRuleResponse getRuleById(Organization organization, Long id) {
        log.info("GET /api/v1/chaos/rules/{} - Fetching for org: {}", id, organization.getSlug());
        return mapToResponse(
                chaosRuleRepository.findByOrganizationAndId(organization, id)
                        .orElseThrow(() -> new ResourceNotFoundException("ChaosRule", id.toString()))
        );
    }

    @Cacheable(value = "chaosRules", key = "#organization.id + ':target:' + #target")
    @Transactional(readOnly = true)
    public ChaosRuleResponse getRuleByTarget(Organization organization, String target) {
        log.info("GET /api/v1/chaos/rules/target/{} - Fetching for org: {}", target, organization.getSlug());
        return mapToResponse(
                chaosRuleRepository.findByOrganizationAndTarget(organization, target)
                        .orElseThrow(() -> new ResourceNotFoundException("ChaosRule", target))
        );
    }

    @Transactional(readOnly = true)
    public List<ChaosRuleResponse> getEnabledRules(Organization organization) {
        log.info("GET /api/v1/chaos/rules/enabled - Fetching for org: {}", organization.getSlug());
        return chaosRuleRepository.findByOrganizationAndEnabledTrue(organization)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // ================ PAGINATION ================

    @Transactional(readOnly = true)
    public PageResponse<ChaosRuleResponse> getRulesPaginated(Organization organization,
                                                             PageRequest pageRequest) {
        log.info("GET /api/v1/chaos/rules/paginated?cursor={}&limit={} - Org: {}",
                pageRequest.getCursor(), pageRequest.getLimit(), organization.getSlug());

        String sortField = validateSortField(pageRequest.getSortBy());

        Sort.Direction direction = "DESC".equalsIgnoreCase(pageRequest.getSortDirection())
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;

        org.springframework.data.domain.Pageable pageable =
                org.springframework.data.domain.PageRequest.of(
                        0, pageRequest.getLimit() + 1,
                        Sort.by(direction, sortField));

        List<ChaosRuleEntity> entities = chaosRuleRepository.findPaginated(
                organization,
                pageRequest.getCursor(),
                pageRequest.getEnabled(),
                pageRequest.getTags(),
                pageRequest.getSearch(),
                pageable);

        boolean hasMore = entities.size() > pageRequest.getLimit();
        if (hasMore) {
            entities = entities.subList(0, pageRequest.getLimit());
        }

        List<ChaosRuleResponse> responses = entities.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        Long nextCursor = hasMore && !responses.isEmpty()
                ? responses.get(responses.size() - 1).getId()
                : null;

        PageResponse.PaginationMetadata metadata = PageResponse.PaginationMetadata.builder()
                .nextCursor(nextCursor)
                .hasMore(hasMore)
                .count(responses.size())
                .limit(pageRequest.getLimit())
                .build();

        return PageResponse.<ChaosRuleResponse>builder()
                .data(responses)
                .pagination(metadata)
                .build();
    }

    // ================ STATS ================

    @Transactional(readOnly = true)
    public ChaosRuleStats getStats(Organization organization) {
        log.info("GET /api/v1/chaos/rules/stats - Org: {}", organization.getSlug());

        long total   = chaosRuleRepository.countByOrganization(organization);
        long enabled = chaosRuleRepository.countByOrganizationAndEnabledTrue(organization);

        return ChaosRuleStats.builder()
                .totalRules(total)
                .enabledRules(enabled)
                .disabledRules(total - enabled)
                .maxRulesAllowed(organization.getMaxRules())
                .remainingRules(organization.getMaxRules() - (int) total)
                .currentPlan(organization.getPlan())
                .build();
    }

    // ================ WRITE ================

    @CacheEvict(value = "chaosRules", allEntries = true)
    public ChaosRuleResponse createRule(Organization organization, ChaosRuleRequest request) {
        log.info("POST /api/v1/chaos/rules - Target: {}, Org: {}",
                request.getTarget(), organization.getSlug());

        long currentCount = chaosRuleRepository.countByOrganization(organization);
        if (currentCount >= organization.getMaxRules()) {
            throw new ValidationException(
                    String.format("Rule limit reached. Your %s plan allows %d rules. Upgrade to add more.",
                            organization.getPlan(), organization.getMaxRules()));
        }

        if (chaosRuleRepository.findByOrganizationAndTarget(organization, request.getTarget()).isPresent()) {
            throw new ValidationException(
                    String.format("Chaos rule with target '%s' already exists", request.getTarget()));
        }

        validateChaosRule(request);

        ChaosRuleEntity saved = chaosRuleRepository.save(mapToEntity(organization, request));
        log.info("Created chaos rule ID: {} for org: {}", saved.getId(), organization.getSlug());
        return mapToResponse(saved);
    }

    @CacheEvict(value = "chaosRules", allEntries = true)
    public ChaosRuleResponse updateRule(Organization organization, Long id, ChaosRuleRequest request) {
        log.info("PUT /api/v1/chaos/rules/{} - Org: {}", id, organization.getSlug());

        ChaosRuleEntity existing = chaosRuleRepository.findByOrganizationAndId(organization, id)
                .orElseThrow(() -> new ResourceNotFoundException("ChaosRule", id.toString()));

        validateChaosRule(request);

        if (!existing.getTarget().equals(request.getTarget())) {
            chaosRuleRepository.findByOrganizationAndTarget(organization, request.getTarget())
                    .ifPresent(conflict -> {
                        if (!conflict.getId().equals(id)) {
                            throw new ValidationException(
                                    String.format("Target '%s' is already used by another rule (ID: %d)",
                                            request.getTarget(), conflict.getId()));
                        }
                    });
        }

        // Evict cached regex pattern if targeting changed
        if (existing.getTargetPattern() != null) {
            targetMatcher.evictPattern(existing.getTargetPattern());
        }

        updateEntityFromRequest(existing, request);
        ChaosRuleEntity saved = chaosRuleRepository.save(existing);
        log.info("Updated chaos rule ID: {} for org: {}", saved.getId(), organization.getSlug());
        return mapToResponse(saved);
    }

    @CacheEvict(value = "chaosRules", allEntries = true)
    public void deleteRule(Organization organization, Long id) {
        log.info("DELETE /api/v1/chaos/rules/{} - Org: {}", id, organization.getSlug());

        ChaosRuleEntity entity = chaosRuleRepository.findByOrganizationAndId(organization, id)
                .orElseThrow(() -> new ResourceNotFoundException("ChaosRule", id.toString()));

        if ("default".equalsIgnoreCase(entity.getTarget())) {
            throw new ValidationException("Cannot delete the default chaos rule");
        }

        targetMatcher.evictPattern(entity.getTargetPattern());
        chaosRuleRepository.deleteById(id);
        log.info("Deleted chaos rule ID: {} for org: {}", id, organization.getSlug());
    }

    @CacheEvict(value = "chaosRules", allEntries = true)
    public void deleteRuleByTarget(Organization organization, String target) {
        log.info("DELETE /api/v1/chaos/rules/target/{} - Org: {}", target, organization.getSlug());

        if ("default".equalsIgnoreCase(target)) {
            throw new ValidationException("Cannot delete the default chaos rule");
        }

        ChaosRuleEntity entity = chaosRuleRepository.findByOrganizationAndTarget(organization, target)
                .orElseThrow(() -> new ResourceNotFoundException("ChaosRule", target));

        targetMatcher.evictPattern(entity.getTargetPattern());
        chaosRuleRepository.delete(entity);
        log.info("Deleted chaos rule for target: {} in org: {}", target, organization.getSlug());
    }

    // ================ CHAOS ENGINE SUPPORT ================

    /**
     * Get chaos rule for chaos engine execution (tenant-aware).
     *
     * Resolution order:
     *   1. Exact match on target
     *   2. Advanced targeting rules (PREFIX / REGEX)
     *   3. "default" fallback rule
     *   4. Safe no-op defaults (never throws)
     */
    @Cacheable(value = "chaosRules", key = "#organization.id + ':engine:' + #target")
    @Transactional(readOnly = true)
    public ChaosRule getRuleForChaosEngine(Organization organization, String target) {
        log.debug("Chaos engine fetching rule for target: {} in org: {}", target, organization.getSlug());

        // 1. Exact match — fastest path, most common
        ChaosRuleEntity exact = chaosRuleRepository
                .findByOrganizationAndTarget(organization, target)
                .orElse(null);

        if (exact != null) {
            return mapToModel(exact);
        }

        // 2. Advanced targeting — PREFIX / REGEX rules
        List<ChaosRuleEntity> advancedRules =
                chaosRuleRepository.findAdvancedTargetingRules(organization);

        for (ChaosRuleEntity rule : advancedRules) {
            if (targetMatcher.matches(rule, target)) {
                log.debug("Advanced match: rule '{}' ({}) matched target '{}'",
                        rule.getTarget(), rule.getTargetingMode(), target);
                return mapToModel(rule);
            }
        }

        // 3. Default fallback rule
        ChaosRuleEntity defaultRule = chaosRuleRepository
                .findByOrganizationAndTarget(organization, "default")
                .orElse(null);

        if (defaultRule != null) {
            log.debug("No rule for target: '{}' — using 'default' for org: {}",
                    target, organization.getSlug());
            return mapToModel(defaultRule);
        }

        // 4. Safe no-op — never throw, never crash request path
        log.debug("No rule found for target: '{}' or 'default' in org: '{}' — returning safe defaults",
                target, organization.getSlug());
        return ChaosRule.builder()
                .target(target)
                .failureRate(0.0)
                .maxDelayMs(0L)
                .blastRadius(0.0)
                .enabled(false)
                .build();
    }

    // ================ DEPRECATED LEGACY ================

    @Deprecated(since = "Phase 2", forRemoval = true)
    @Cacheable(value = "chaosRules", key = "'legacy:' + #target")
    @Transactional(readOnly = true)
    public ChaosRule getRule(String target) {
        log.warn("DEPRECATED getRule(target) — use getRuleForChaosEngine(org, target)");
        return chaosRuleRepository.findByTarget(target)
                .map(this::mapToModel)
                .orElseGet(() -> chaosRuleRepository.findByTarget("default")
                        .map(this::mapToModel)
                        .orElseGet(() -> ChaosRule.builder()
                                .target(target)
                                .failureRate(0.0)
                                .maxDelayMs(0L)
                                .blastRadius(0.0)
                                .enabled(false)
                                .build()));
    }

    // ================ VALIDATION ================

    private void validateChaosRule(ChaosRuleRequest request) {
        if (request.getFailureRate() != null
                && (request.getFailureRate() < 0.0 || request.getFailureRate() > 1.0)) {
            throw new ValidationException("Failure rate must be between 0.0 and 1.0");
        }

        if (request.getMaxDelayMs() != null
                && (request.getMaxDelayMs() < 0 || request.getMaxDelayMs() > 30000)) {
            throw new ValidationException("Max delay must be between 0 and 30000ms");
        }

        if (request.getBlastRadius() != null
                && (request.getBlastRadius() < 0.0 || request.getBlastRadius() > 1.0)) {
            throw new ValidationException("Blast radius must be between 0.0 and 1.0");
        }

        if (request.getTargetingMode() != null &&
                request.getTargetingMode() == TargetingMode.REGEX &&
                request.getTargetPattern() != null &&
                !targetMatcher.isValidRegex(request.getTargetPattern())) {
            throw new ValidationException(
                    "Invalid regex pattern: '" + request.getTargetPattern() + "'");
        }
    }

    // ================ MAPPERS ================

    private ChaosRuleResponse mapToResponse(ChaosRuleEntity entity) {
        return ChaosRuleResponse.builder()
                .id(entity.getId())
                .organizationId(entity.getOrganization().getId())
                .target(entity.getTarget())
                .targetPattern(entity.getTargetPattern())       // was missing — always returned null
                .targetingMode(entity.getTargetingMode())       // was missing — always returned null
                .failureRate(entity.getFailureRate())
                .maxDelayMs(entity.getMaxDelayMs())
                .enabled(entity.getEnabled())
                .description(entity.getDescription())
                .blastRadius(entity.getBlastRadius())
                .seed(entity.getSeed())
                .tags(entity.getTags())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .createdBy(entity.getCreatedBy())
                .updatedBy(entity.getUpdatedBy())
                .version(entity.getVersion())
                .build();
    }

    private ChaosRuleEntity mapToEntity(Organization organization, ChaosRuleRequest request) {
        return ChaosRuleEntity.builder()
                .organization(organization)
                .target(request.getTarget())
                .targetPattern(request.getTargetPattern())
                .targetingMode(request.getTargetingMode() != null
                        ? request.getTargetingMode()
                        : TargetingMode.EXACT)
                .failureRate(request.getFailureRate())
                .maxDelayMs(request.getMaxDelayMs())
                .enabled(request.getEnabled())
                .description(request.getDescription())
                .blastRadius(request.getBlastRadius() != null ? request.getBlastRadius() : 1.0)
                .seed(request.getSeed())
                .tags(request.getTags())
                .createdBy("system")
                .updatedBy("system")
                .build();
    }

    private void updateEntityFromRequest(ChaosRuleEntity entity, ChaosRuleRequest request) {
        entity.setTarget(request.getTarget());
        entity.setTargetPattern(request.getTargetPattern());
        entity.setTargetingMode(request.getTargetingMode() != null
                ? request.getTargetingMode()
                : TargetingMode.EXACT);
        entity.setFailureRate(request.getFailureRate());
        entity.setMaxDelayMs(request.getMaxDelayMs());
        entity.setEnabled(request.getEnabled());
        entity.setDescription(request.getDescription());
        entity.setBlastRadius(request.getBlastRadius() != null ? request.getBlastRadius() : 1.0);
        entity.setSeed(request.getSeed());
        entity.setTags(request.getTags());
        entity.setUpdatedBy("system");
    }

    private ChaosRule mapToModel(ChaosRuleEntity entity) {
        return ChaosRule.builder()
                .target(entity.getTarget())
                .failureRate(entity.getFailureRate())
                .maxDelayMs(entity.getMaxDelayMs())
                .enabled(entity.getEnabled())
                .description(entity.getDescription())
                .seed(entity.getSeed())
                .blastRadius(entity.getBlastRadius())
                .build();
    }

    private String validateSortField(String sortBy) {
        return switch (sortBy.toLowerCase()) {
            case "target"      -> "target";
            case "createdat"   -> "createdAt";
            case "updatedat"   -> "updatedAt";
            case "enabled"     -> "enabled";
            default            -> "id";
        };
    }
}