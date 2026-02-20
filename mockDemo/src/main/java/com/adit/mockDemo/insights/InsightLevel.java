package com.adit.mockDemo.insights;

public enum InsightLevel {
    SUCCESS,    // Positive finding (system is resilient)
    INFO,       // Informational (no action needed)
    WARNING,    // Should fix but not urgent
    CRITICAL    // Fix immediately
}