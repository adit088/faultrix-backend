    package com.adit.mockDemo.insights;

    import lombok.AllArgsConstructor;
    import lombok.Getter;
    import lombok.NoArgsConstructor;
    import lombok.Setter;

    import java.util.List;

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor
    public class InsightSnapshot {

        private long failures;
        private long delays;
        private List<FailureInsight> insights;


    }
