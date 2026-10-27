package com.nachiketa.scheduler.api.service.validation;

import com.nachiketa.scheduler.api.exception.InvalidRequestException;
import com.nachiketa.scheduler.api.service.schedule.CronExpressions;
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
        long epochSeconds;
        try {
            epochSeconds = Long.parseLong(expression);
        } catch (NumberFormatException exception) {
            throw new InvalidRequestException(
                    "ONE_TIME schedule expression must be a Unix Timestamp",
                    exception
            );
        }

        Instant executionTime = Instant.ofEpochSecond(epochSeconds);

        if (!executionTime.isAfter(Instant.now())) {
            throw new IllegalArgumentException(
                    "ONE_TIME execution must be in the future"
            );
        }
    }

    private void validateCron(String expression) {
        try {
            CronExpressions.parse(expression);
        } catch (IllegalArgumentException exception) {
            throw new InvalidRequestException(
                    "Invalid CRON expression: " + expression,
                    exception
            );
        }
    }
}
