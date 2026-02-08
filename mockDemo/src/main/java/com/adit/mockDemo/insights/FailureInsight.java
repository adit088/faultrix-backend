package com.adit.mockDemo.insights;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class FailureInsight {

    private InsightType type;
    private InsightLevel level;
    private String title;
    private String message;
    private String recommendation;
}
