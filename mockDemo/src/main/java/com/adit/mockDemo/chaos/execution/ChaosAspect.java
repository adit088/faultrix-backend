package com.adit.mockDemo.chaos.execution;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;

/**
 * AOP Aspect that intercepts controller methods and injects chaos
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class ChaosAspect {

    private final ChaosExecutor chaosExecutor;

    /**
     * Pointcut for all controller methods EXCEPT chaos management APIs
     * We DON'T inject chaos into:
     * - Chaos rule management (ChaosRuleController, ChaosScheduleController, ChaosControlController, ChaosEventController)
     * - System info (SystemController)
     * - Insights (InsightController)
     * - Experiments (TrafficController, ChaosExperimentController) - these generate chaos, not receive it
     */
    @Pointcut("within(@org.springframework.web.bind.annotation.RestController *) " +
            "&& !within(com.adit.mockDemo.controller.ChaosRuleController) " +
            "&& !within(com.adit.mockDemo.controller.ChaosScheduleController) " +
            "&& !within(com.adit.mockDemo.controller.ChaosControlController) " +
            "&& !within(com.adit.mockDemo.controller.ChaosEventController) " +
            "&& !within(com.adit.mockDemo.controller.SystemController) " +
            "&& !within(com.adit.mockDemo.controller.TrafficController) " +
            "&& !within(com.adit.mockDemo.controller.ChaosExperimentController) " +
            "&& !within(com.adit.mockDemo.insights.InsightController) " +
            "&& !within(com.adit.mockDemo.proxy.ProxyController)")
    public void controllerMethods() {}

    /**
     * Around advice that intercepts controller methods
     */
    @Around("controllerMethods()")
    public Object injectChaos(ProceedingJoinPoint joinPoint) throws Throwable {
        // Get request context
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        if (attributes == null) {
            // No request context, proceed normally
            return joinPoint.proceed();
        }

        HttpServletRequest request = attributes.getRequest();
        String requestPath = request.getRequestURI();
        String requestId = getOrCreateRequestId(request);

        log.debug("Chaos aspect intercepted: {} {}", request.getMethod(), requestPath);

        try {
            // Execute chaos logic BEFORE the controller method
            chaosExecutor.executeChaos(requestPath, requestId);

            // If no chaos was thrown, proceed with normal execution
            return joinPoint.proceed();

        } catch (Exception e) {
            // Chaos was injected, throw it up
            throw e;
        }
    }

    /**
     * Get or create unique request ID
     */
    private String getOrCreateRequestId(HttpServletRequest request) {
        String correlationId = request.getHeader("X-Correlation-ID");
        if (correlationId == null) {
            correlationId = request.getAttribute("correlationId") != null
                    ? request.getAttribute("correlationId").toString()
                    : UUID.randomUUID().toString();
        }
        return correlationId;
    }
}