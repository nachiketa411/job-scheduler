package com.nachiketa.scheduler.api.service.schedule;

import com.nachiketa.scheduler.model.Schedule;
import com.nachiketa.scheduler.model.ScheduleType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NextExecutionCalculatorTest {

    private NextExecutionCalculator nextExecutionCalculator;

    @BeforeEach
    void setUp() {
        nextExecutionCalculator = new NextExecutionCalculator();
    }

    /**
     * Verifies that a ONE_TIME schedule is converted directly
     * from its Unix timestamp expression into an Instant.
     */
    @Test
    void calculateFirstExecution_whenOneTime_returnsConfiguredInstant() {
        Instant expectedExecutionTime =
                Instant.parse("2026-11-01T15:30:00Z");

        Schedule schedule = Schedule.builder()
                .type(ScheduleType.ONE_TIME)
                .expression(
                        String.valueOf(
                                expectedExecutionTime.getEpochSecond()
                        )
                )
                .build();

        Instant result =
                nextExecutionCalculator.calculateFirstExecution(schedule);

        assertEquals(
                expectedExecutionTime,
                result
        );
    }

    /**
     * Verifies that a five-field CRON expression is normalized
     * and produces a valid execution time in the future.
     */
    @Test
    void calculateFirstExecution_whenCronHasFiveFields_returnsFutureExecution() {
        Schedule schedule = Schedule.builder()
                .type(ScheduleType.CRON)
                .expression("30 15 * * 5")
                .build();

        Instant beforeCalculation = Instant.now();

        Instant result =
                nextExecutionCalculator.calculateFirstExecution(schedule);

        assertTrue(
                result.isAfter(beforeCalculation)
        );
    }

    /**
     * Verifies that a six-field CRON expression is accepted
     * without additional normalization.
     */
    @Test
    void calculateFirstExecution_whenCronHasSixFields_returnsFutureExecution() {
        Schedule schedule = Schedule.builder()
                .type(ScheduleType.CRON)
                .expression("0 30 15 * * 5")
                .build();

        Instant beforeCalculation = Instant.now();

        Instant result =
                nextExecutionCalculator.calculateFirstExecution(schedule);

        assertTrue(
                result.isAfter(beforeCalculation)
        );
    }

    /**
     * Verifies that the next recurring execution is calculated
     * relative to the supplied reference time.
     */
    @Test
    void calculateNextExecution_whenCron_returnsNextScheduledInstant() {
        Schedule schedule = Schedule.builder()
                .type(ScheduleType.CRON)
                .expression("30 15 * * 5")
                .build();

        Instant after =
                Instant.parse("2026-10-26T12:00:00Z");

        Instant expected =
                Instant.parse("2026-10-30T15:30:00Z");

        Instant result =
                nextExecutionCalculator.calculateNextExecution(
                        schedule,
                        after
                );

        assertEquals(
                expected,
                result
        );
    }

    /**
     * Verifies that when the supplied reference time is already
     * past the current week's execution, the calculator returns
     * the following matching CRON occurrence.
     */
    @Test
    void calculateNextExecution_whenCurrentOccurrenceHasPassed_returnsFollowingOccurrence() {
        Schedule schedule = Schedule.builder()
                .type(ScheduleType.CRON)
                .expression("30 15 * * 5")
                .build();

        Instant after =
                Instant.parse("2026-10-30T16:00:00Z");

        Instant expected =
                Instant.parse("2026-11-06T15:30:00Z");

        Instant result =
                nextExecutionCalculator.calculateNextExecution(
                        schedule,
                        after
                );

        assertEquals(
                expected,
                result
        );
    }

    /**
     * Verifies that surrounding whitespace in a CRON expression
     * does not affect execution-time calculation.
     */
    @Test
    void calculateNextExecution_whenCronContainsWhitespace_returnsCorrectExecution() {
        Schedule schedule = Schedule.builder()
                .type(ScheduleType.CRON)
                .expression("   30 15 * * 5   ")
                .build();

        Instant after =
                Instant.parse("2026-10-26T12:00:00Z");

        Instant expected =
                Instant.parse("2026-10-30T15:30:00Z");

        Instant result =
                nextExecutionCalculator.calculateNextExecution(
                        schedule,
                        after
                );

        assertEquals(
                expected,
                result
        );
    }

    /**
     * Verifies that recurring execution calculation is rejected
     * for ONE_TIME schedules because they do not recur.
     */
    @Test
    void calculateNextExecution_whenScheduleIsOneTime_throwsIllegalArgumentException() {
        Schedule schedule = Schedule.builder()
                .type(ScheduleType.ONE_TIME)
                .expression("1793547000")
                .build();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> nextExecutionCalculator.calculateNextExecution(
                        schedule,
                        Instant.now()
                )
        );

        assertEquals(
                "Next recurring execution can only be calculated for CRON schedules",
                exception.getMessage()
        );
    }

    /**
     * Verifies that an invalid ONE_TIME timestamp cannot be
     * converted into an execution Instant.
     */
    @Test
    void calculateFirstExecution_whenOneTimeExpressionIsInvalid_throwsNumberFormatException() {
        Schedule schedule = Schedule.builder()
                .type(ScheduleType.ONE_TIME)
                .expression("not-a-timestamp")
                .build();

        assertThrows(
                NumberFormatException.class,
                () -> nextExecutionCalculator.calculateFirstExecution(schedule)
        );
    }

    /**
     * Verifies that invalid CRON syntax is rejected when
     * calculating an execution time.
     */
    @Test
    void calculateNextExecution_whenCronIsInvalid_throwsIllegalArgumentException() {
        Schedule schedule = Schedule.builder()
                .type(ScheduleType.CRON)
                .expression("100 15 * * 5")
                .build();

        assertThrows(
                IllegalArgumentException.class,
                () -> nextExecutionCalculator.calculateNextExecution(
                        schedule,
                        Instant.now()
                )
        );
    }
}
