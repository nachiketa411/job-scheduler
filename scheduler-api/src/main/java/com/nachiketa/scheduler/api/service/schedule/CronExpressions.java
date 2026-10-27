package com.nachiketa.scheduler.api.service.schedule;

import org.springframework.scheduling.support.CronExpression;

public final class CronExpressions {
    private CronExpressions() {}

    public static CronExpression parse(String expression) {
        return CronExpression.parse(normalize(expression));
    }

    private static String normalize(String expression) {
        String trimmed = expression.trim();
        int fields = trimmed.split("\\s+").length;

        if (fields == 5) {
            return "0 " + trimmed;
        }
        if (fields == 6) {
            return trimmed;
        }

        throw new IllegalArgumentException(
                "CRON expression must contain 5 or 6 fields"
        );
    }
}
