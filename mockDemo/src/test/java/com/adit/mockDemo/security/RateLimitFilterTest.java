package com.adit.mockDemo.security;

import com.adit.mockDemo.entity.Organization;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    private RateLimitFilter filter;

    @Mock
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter();
    }

    private Organization orgWithPlan(String plan) {
        return Organization.builder()
                .id(1L)
                .slug("test-org")
                .name("Test Org")
                .apiKey("hashed-key")
                .plan(plan)
                .maxRules(10)
                .enabled(true)
                .build();
    }

    @Test
    void publicEndpoint_bypassesRateLimit() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/swagger-ui/index.html");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain, times(1)).doFilter(request, response);
        assertThat(response.getStatus()).isNotEqualTo(429);
    }

    @Test
    void noOrgAttribute_passesThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/chaos/rules");
        MockHttpServletResponse response = new MockHttpServletResponse();
        // No currentOrganization attribute set — auth filter hasn't run

        filter.doFilter(request, response, filterChain);

        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    void withinFreeLimit_allowsRequest() throws Exception {
        Organization org = orgWithPlan("free"); // limit = 60/min

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/chaos/rules");
        request.setAttribute("currentOrganization", org);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain, times(1)).doFilter(request, response);
        assertThat(response.getStatus()).isNotEqualTo(429);
    }

    @Test
    void exceededFreeLimit_returns429() throws Exception {
        Organization org = orgWithPlan("free"); // limit = 60/min

        // Send 61 requests — the 61st should be rate-limited
        for (int i = 0; i < 60; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/chaos/rules");
            req.setAttribute("currentOrganization", org);
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(req, res, filterChain);
        }

        // 61st request
        MockHttpServletRequest limitedRequest = new MockHttpServletRequest("GET", "/api/v1/chaos/rules");
        limitedRequest.setAttribute("currentOrganization", org);
        MockHttpServletResponse limitedResponse = new MockHttpServletResponse();

        filter.doFilter(limitedRequest, limitedResponse, filterChain);

        assertThat(limitedResponse.getStatus()).isEqualTo(429);
        assertThat(limitedResponse.getHeader("X-RateLimit-Limit")).isEqualTo("60");
        assertThat(limitedResponse.getHeader("Retry-After")).isEqualTo("60");
    }

    @Test
    void enterprisePlan_hasHigherLimit() throws Exception {
        Organization org = orgWithPlan("enterprise"); // limit = 10000/min

        // Send 100 requests — all should pass for enterprise
        for (int i = 0; i < 100; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/chaos/rules");
            req.setAttribute("currentOrganization", org);
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(req, res, filterChain);
            assertThat(res.getStatus()).isNotEqualTo(429);
        }
    }

    @Test
    void rateLimitResponse_containsCorrectHeaders() throws Exception {
        Organization org = orgWithPlan("free");

        // Exhaust limit
        for (int i = 0; i < 60; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/chaos/rules");
            req.setAttribute("currentOrganization", org);
            filter.doFilter(req, new MockHttpServletResponse(), filterChain);
        }

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/chaos/rules");
        req.setAttribute("currentOrganization", org);
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, filterChain);

        assertThat(res.getStatus()).isEqualTo(429);
        assertThat(res.getHeader("X-RateLimit-Remaining")).isEqualTo("0");
        assertThat(res.getContentType()).isEqualTo("application/json");
    }

    @Test
    void actuatorHealth_bypassesRateLimit() throws Exception {
        Organization org = orgWithPlan("free");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        request.setAttribute("currentOrganization", org);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isNotEqualTo(429);
    }

    @Test
    void cleanup_doesNotThrow() {
        // Add some entries to the window map
        Organization org = orgWithPlan("free");
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/chaos/rules");
        req.setAttribute("currentOrganization", org);

        // Should not throw
        filter.cleanup();
    }
}