package com.adit.mockDemo.chaos.execution;

/**
 * How a chaos rule matches incoming request targets.
 *
 * EXACT  — target must match exactly (current default behaviour)
 * PREFIX — target must start with the rule's targetPattern
 * REGEX  — target must match the rule's targetPattern as a Java regex
 */
public enum TargetingMode {
    EXACT,
    PREFIX,
    REGEX
}