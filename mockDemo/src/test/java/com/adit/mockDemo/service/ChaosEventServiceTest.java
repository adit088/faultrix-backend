package com.adit.mockDemo.service;

import com.adit.mockDemo.chaos.execution.ChaosDecision;
import com.adit.mockDemo.chaos.execution.ChaosType;
import com.adit.mockDemo.entity.ChaosEvent;
import com.adit.mockDemo.entity.Organization;
import com.adit.mockDemo.repository.ChaosEventRepository;
import com.adit.mockDemo.repository.ChaosRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChaosEventServiceTest {

    @Mock private ChaosEventRepository eventRepository;
    @Mock private ChaosRuleRepository  ruleRepository;

    @InjectMocks
    private ChaosEventService service;

    private Organization testOrg;

    @BeforeEach
    void setUp() {
        testOrg = Organization.builder()
                .id(1L).name("Test").slug("test")
                .apiKey("hash").plan("free").maxRules(10).enabled(true)
                .build();
    }

    @Test
    void recordEvent_injected_savesEventWithCorrectFields() {
        ChaosDecision decision = ChaosDecision.builder()
                .shouldInjectChaos(true)
                .chaosType(ChaosType.ERROR_5XX)
                .errorCode(500)
                .delayMs(0)
                .build();

        when(ruleRepository.findByOrganizationAndTarget(any(), any()))
                .thenReturn(Optional.empty());

        ArgumentCaptor<ChaosEvent> captor = ArgumentCaptor.forClass(ChaosEvent.class);
        when(eventRepository.save(captor.capture())).thenReturn(new ChaosEvent());

        service.recordEvent(testOrg, "/api/v1/users", "req-001", decision, true);

        // Since it's @Async, we need to verify the save happened (in test context Async runs synchronously)
        verify(eventRepository).save(any(ChaosEvent.class));
        ChaosEvent saved = captor.getValue();
        assertThat(saved.getTarget()).isEqualTo("/api/v1/users");
        assertThat(saved.getInjected()).isTrue();
        assertThat(saved.getChaosType()).isEqualTo(ChaosType.ERROR_5XX);
        assertThat(saved.getHttpStatus()).isEqualTo(500);
    }

    @Test
    void recordEvent_notInjected_savesEventWithNullHttpStatus() {
        ChaosDecision decision = ChaosDecision.noChaos();

        when(ruleRepository.findByOrganizationAndTarget(any(), any()))
                .thenReturn(Optional.empty());

        ArgumentCaptor<ChaosEvent> captor = ArgumentCaptor.forClass(ChaosEvent.class);
        when(eventRepository.save(captor.capture())).thenReturn(new ChaosEvent());

        service.recordEvent(testOrg, "/api/v1/users", "req-002", decision, false);

        verify(eventRepository).save(any(ChaosEvent.class));
        ChaosEvent saved = captor.getValue();
        assertThat(saved.getInjected()).isFalse();
        assertThat(saved.getHttpStatus()).isNull();
    }

    @Test
    void recordEvent_repositoryThrows_doesNotPropagateException() {
        ChaosDecision decision = ChaosDecision.noChaos();
        when(ruleRepository.findByOrganizationAndTarget(any(), any()))
                .thenReturn(Optional.empty());
        when(eventRepository.save(any())).thenThrow(new RuntimeException("DB down"));

        // Must NOT throw — event persistence failure cannot crash the request thread
        service.recordEvent(testOrg, "/api/v1/users", "req-003", decision, false);
    }
}