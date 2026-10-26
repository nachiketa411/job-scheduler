package com.nachiketa.scheduler.api.service.schedule;

import com.nachiketa.scheduler.model.Schedule;
import com.nachiketa.scheduler.model.ScheduleType;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

@Component
public class NextExecutionCalculator {

    public Instant calculateFirstExecution(Schedule schedule) {
        if (schedule.getType() == ScheduleType.ONE_TIME) {
            return parseOneTime(schedule.getExpression());
        }

        if (schedule.getType() == ScheduleType.CRON) {
            return calculateNextCron(
                    schedule.getExpression(),
                    Instant.now()
            );
        }

        throw new IllegalArgumentException(
                "Unsupported schedule type: "
                        + schedule.getType().getValue()
        );
    }

    public Instant calculateNextExecution(
            Schedule schedule,
            Instant after) {

        if (schedule.getType() != ScheduleType.CRON) {
            throw new IllegalArgumentException(
                    "Next recurring execution can only be calculated for CRON schedules"
            );
        }

        return calculateNextCron(
                schedule.getExpression(),
                after
        );
    }

    private Instant parseOneTime(String expression) {
        return Instant.ofEpochSecond(
                Long.parseLong(expression)
        );
    }

    private Instant calculateNextCron(
            String expression,
            Instant after) {

        CronExpression cron =
                CronExpression.parse(
                        normalizeCron(expression)
                );

        ZonedDateTime next =
                cron.next(
                        after.atZone(ZoneOffset.UTC)
                );

        if (next == null) {
            throw new IllegalStateException(
                    "Could not calculate next execution for CRON: "
                            + expression
            );
        }

        return next.toInstant();
    }

    private String normalizeCron(String expression) {
        String trimmed = expression.trim();
        int fields = trimmed.split("\\s+").length;

        if (fields == 5) {
            return "0 " + trimmed;
        }

        return trimmed;
    }
}
