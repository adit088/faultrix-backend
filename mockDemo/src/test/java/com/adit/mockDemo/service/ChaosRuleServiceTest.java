package com.adit.mockDemo.service;

import com.adit.mockDemo.chaos.execution.TargetMatcher;
import com.adit.mockDemo.chaos.execution.TargetingMode;
import com.adit.mockDemo.dto.ChaosRuleRequest;
import com.adit.mockDemo.dto.ChaosRuleResponse;
import com.adit.mockDemo.entity.ChaosRuleEntity;
import com.adit.mockDemo.entity.Organization;
import com.adit.mockDemo.exception.ResourceNotFoundException;
import com.adit.mockDemo.exception.ValidationException;
import com.adit.mockDemo.repository.ChaosRuleRepository;
import com.adit.mockDemo.security.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChaosRuleServiceTest {

    @Mock private ChaosRuleRepository chaosRuleRepository;
    @Mock private TenantContext        tenantContext;
    @Mock private TargetMatcher        targetMatcher;

    @InjectMocks
    private ChaosRuleService service;

    private Organization testOrg;
    private ChaosRuleEntity testEntity;

    @BeforeEach
    void setUp() {
        testOrg = Organization.builder()
                .id(1L).name("Test Org").slug("test-org")
                .apiKey("hashed-key").plan("free").maxRules(10).enabled(true)
                .build();

        testEntity = ChaosRuleEntity.builder()
                .id(1L).organization(testOrg)
                .target("/api/v1/users")
                .targetPattern(null)
                .targetingMode(TargetingMode.EXACT)
                .failureRate(0.3).maxDelayMs(200L)
                .enabled(true).blastRadius(1.0)
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .createdBy("system").updatedBy("system")
                .version(0L)
                .build();
    }

    // ── READ ──────────────────────────────────────────────────────────────────

    @Test
    void getAllRules_returnsMappedResponses() {
        when(chaosRuleRepository.findByOrganization(testOrg)).thenReturn(List.of(testEntity));

        List<ChaosRuleResponse> result = service.getAllRules(testOrg);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTarget()).isEqualTo("/api/v1/users");
        assertThat(result.get(0).getFailureRate()).isEqualTo(0.3);
    }

    @Test
    void getRuleById_found_returnsResponse() {
        when(chaosRuleRepository.findByOrganizationAndId(testOrg, 1L))
                .thenReturn(Optional.of(testEntity));

        ChaosRuleResponse result = service.getRuleById(testOrg, 1L);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getTarget()).isEqualTo("/api/v1/users");
    }

    @Test
    void getRuleById_notFound_throwsResourceNotFoundException() {
        when(chaosRuleRepository.findByOrganizationAndId(testOrg, 99L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getRuleById(testOrg, 99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void mapToResponse_includesTargetPatternAndTargetingMode() {
        ChaosRuleEntity withPattern = ChaosRuleEntity.builder()
                .id(2L).organization(testOrg)
                .target("/api/v1/users")
                .targetPattern("/api/v1/users/.*")
                .targetingMode(TargetingMode.REGEX)
                .failureRate(0.2).maxDelayMs(100L)
                .enabled(true).blastRadius(1.0)
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .createdBy("system").updatedBy("system").version(0L)
                .build();

        when(chaosRuleRepository.findByOrganizationAndId(testOrg, 2L))
                .thenReturn(Optional.of(withPattern));

        ChaosRuleResponse result = service.getRuleById(testOrg, 2L);

        // These were always null before the mapper fix
        assertThat(result.getTargetPattern()).isEqualTo("/api/v1/users/.*");
        assertThat(result.getTargetingMode()).isEqualTo(TargetingMode.REGEX);
    }

    // ── WRITE ─────────────────────────────────────────────────────────────────

    @Test
    void createRule_success_savesAndReturns() {
        ChaosRuleRequest request = ChaosRuleRequest.builder()
                .target("/api/v1/orders")
                .failureRate(0.2).maxDelayMs(100L)
                .enabled(true).blastRadius(1.0)
                .build();

        when(chaosRuleRepository.countByOrganization(testOrg)).thenReturn(0L);
        when(chaosRuleRepository.findByOrganizationAndTarget(testOrg, "/api/v1/orders"))
                .thenReturn(Optional.empty());
        when(chaosRuleRepository.save(any())).thenReturn(testEntity);

        ChaosRuleResponse result = service.createRule(testOrg, request);

        assertThat(result).isNotNull();
        verify(chaosRuleRepository).save(any());
    }

    @Test
    void createRule_atMaxRules_throwsValidationException() {
        when(chaosRuleRepository.countByOrganization(testOrg)).thenReturn(10L);

        ChaosRuleRequest request = ChaosRuleRequest.builder()
                .target("/api/v1/new")
                .failureRate(0.1).maxDelayMs(0L).enabled(true).blastRadius(1.0)
                .build();

        assertThatThrownBy(() -> service.createRule(testOrg, request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Rule limit reached");
    }

    @Test
    void createRule_duplicateTarget_throwsValidationException() {
        when(chaosRuleRepository.countByOrganization(testOrg)).thenReturn(1L);
        when(chaosRuleRepository.findByOrganizationAndTarget(testOrg, "/api/v1/users"))
                .thenReturn(Optional.of(testEntity));

        ChaosRuleRequest request = ChaosRuleRequest.builder()
                .target("/api/v1/users")
                .failureRate(0.1).maxDelayMs(0L).enabled(true).blastRadius(1.0)
                .build();

        assertThatThrownBy(() -> service.createRule(testOrg, request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void createRule_invalidFailureRate_throwsValidationException() {
        when(chaosRuleRepository.countByOrganization(testOrg)).thenReturn(0L);
        when(chaosRuleRepository.findByOrganizationAndTarget(any(), any()))
                .thenReturn(Optional.empty());

        ChaosRuleRequest request = ChaosRuleRequest.builder()
                .target("/api/v1/new")
                .failureRate(1.5)  // > 1.0 — invalid
                .maxDelayMs(0L).enabled(true).blastRadius(1.0)
                .build();

        assertThatThrownBy(() -> service.createRule(testOrg, request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Failure rate");
    }

    @Test
    void deleteRule_default_throwsValidationException() {
        ChaosRuleEntity defaultRule = ChaosRuleEntity.builder()
                .id(99L).organization(testOrg).target("default")
                .failureRate(0.0).maxDelayMs(0L).enabled(false).blastRadius(1.0)
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .createdBy("system").updatedBy("system").version(0L)
                .build();

        when(chaosRuleRepository.findByOrganizationAndId(testOrg, 99L))
                .thenReturn(Optional.of(defaultRule));

        assertThatThrownBy(() -> service.deleteRule(testOrg, 99L))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Cannot delete the default");
    }

    @Test
    void deleteRule_notFound_throwsResourceNotFoundException() {
        when(chaosRuleRepository.findByOrganizationAndId(testOrg, 999L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteRule(testOrg, 999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── CHAOS ENGINE ──────────────────────────────────────────────────────────

    @Test
    void getRuleForChaosEngine_exactMatch_returnsMappedModel() {
        when(chaosRuleRepository.findByOrganizationAndTarget(testOrg, "/api/v1/users"))
                .thenReturn(Optional.of(testEntity));

        var rule = service.getRuleForChaosEngine(testOrg, "/api/v1/users");

        assertThat(rule.getTarget()).isEqualTo("/api/v1/users");
        assertThat(rule.getFailureRate()).isEqualTo(0.3);
        assertThat(rule.getEnabled()).isTrue();
    }

    @Test
    void getRuleForChaosEngine_noMatch_returnsSafeNoOp() {
        when(chaosRuleRepository.findByOrganizationAndTarget(testOrg, "/api/v1/unknown"))
                .thenReturn(Optional.empty());
        when(chaosRuleRepository.findAdvancedTargetingRules(testOrg)).thenReturn(List.of());
        when(chaosRuleRepository.findByOrganizationAndTarget(testOrg, "default"))
                .thenReturn(Optional.empty());

        var rule = service.getRuleForChaosEngine(testOrg, "/api/v1/unknown");

        assertThat(rule.getEnabled()).isFalse();
        assertThat(rule.getFailureRate()).isEqualTo(0.0);
    }
}