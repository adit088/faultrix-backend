package com.adit.mockDemo.chaos.execution;

import com.adit.mockDemo.entity.ChaosRuleEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Matches an incoming request target against a ChaosRuleEntity.
 * Regex patterns are compiled once and cached — safe for concurrent use.
 *
 * Uses Optional<Pattern> as the map value so that invalid patterns are cached
 * as Optional.empty() rather than null — ConcurrentHashMap forbids null values
 * and would throw NullPointerException if computeIfAbsent returned null.
 */
@Component
@Slf4j
public class TargetMatcher {

    // Cache compiled patterns — Optional.empty() represents a known-invalid pattern
    private final ConcurrentHashMap<String, Optional<Pattern>> patternCache = new ConcurrentHashMap<>();

    /**
     * Returns true if the incoming requestTarget matches the rule's targeting config.
     */
    public boolean matches(ChaosRuleEntity rule, String requestTarget) {
        return switch (rule.getTargetingMode()) {
            case EXACT  -> matchExact(rule, requestTarget);
            case PREFIX -> matchPrefix(rule, requestTarget);
            case REGEX  -> matchRegex(rule, requestTarget);
        };
    }

    private boolean matchExact(ChaosRuleEntity rule, String requestTarget) {
        return rule.getTarget().equals(requestTarget);
    }

    private boolean matchPrefix(ChaosRuleEntity rule, String requestTarget) {
        String pattern = rule.getTargetPattern() != null
                ? rule.getTargetPattern()
                : rule.getTarget();
        return requestTarget.startsWith(pattern);
    }

    private boolean matchRegex(ChaosRuleEntity rule, String requestTarget) {
        String patternStr = rule.getTargetPattern() != null
                ? rule.getTargetPattern()
                : rule.getTarget();

        try {
            Optional<Pattern> cached = patternCache.computeIfAbsent(patternStr, p -> {
                try {
                    return Optional.of(Pattern.compile(p));
                } catch (PatternSyntaxException e) {
                    log.error("Invalid regex pattern in chaos rule ID {}: '{}'",
                            rule.getId(), p, e);
                    return Optional.empty(); // cache the miss — never return null
                }
            });

            // Optional.empty() means invalid pattern — skip injection safely
            if (cached.isEmpty()) return false;
            return cached.get().matcher(requestTarget).matches();

        } catch (Exception e) {
            log.error("Regex match failed for rule ID {}: target='{}', pattern='{}'",
                    rule.getId(), requestTarget, patternStr, e);
            return false;
        }
    }

    /**
     * Validate a regex pattern before saving — call from service layer.
     */
    public boolean isValidRegex(String pattern) {
        try {
            Pattern.compile(pattern);
            return true;
        } catch (PatternSyntaxException e) {
            return false;
        }
    }

    /**
     * Evict a cached pattern when a rule is updated.
     * Works for both valid (Optional.of) and invalid (Optional.empty) cached entries.
     */
    public void evictPattern(String pattern) {
        if (pattern != null) {
            patternCache.remove(pattern);
        }
    }
}