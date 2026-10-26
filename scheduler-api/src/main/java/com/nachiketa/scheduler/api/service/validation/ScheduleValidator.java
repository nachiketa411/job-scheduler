package com.nachiketa.scheduler.api.service.validation;

import com.nachiketa.scheduler.model.Schedule;
import com.nachiketa.scheduler.model.ScheduleType;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class ScheduleValidator {

    public void validate(Schedule schedule) {
        ScheduleType type = schedule.getType();
        String expression = schedule.getExpression();

        if (type == ScheduleType.ONE_TIME) {
            validateOneTime(expression);
            return;
        }

        if (type == ScheduleType.CRON) {
            validateCron(expression);
            return;
        }

        throw new IllegalArgumentException(
                "Unsupported schedule type: " + type.getValue()
        );
    }

    private void validateOneTime(String expression) {
        try {
            long epochSeconds = Long.parseLong(expression);
            Instant executionTime = Instant.ofEpochSecond(epochSeconds);

            if (!executionTime.isAfter(Instant.now())) {
                throw new IllegalArgumentException(
                        "ONE_TIME execution must be in the future"
                );
            }
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "ONE_TIME schedule expression must be a Unix Timestamp",
                    exception
            );
        }
    }

    private void validateCron(String expression) {
        try {
            CronExpression.parse(normalizeCron(expression));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Invalid CRON expression: " + expression,
                    exception
            );
        }
    }

    private String normalizeCron(String expression) {
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
