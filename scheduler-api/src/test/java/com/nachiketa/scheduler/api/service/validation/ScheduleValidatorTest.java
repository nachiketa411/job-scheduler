package com.nachiketa.scheduler.api.service.validation;

import com.nachiketa.scheduler.model.Schedule;
import com.nachiketa.scheduler.model.ScheduleType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ScheduleValidatorTest {

    private ScheduleValidator scheduleValidator;

    @BeforeEach
    void setUp() {
        scheduleValidator = new ScheduleValidator();
    }

    /**
     * Verifies that a ONE_TIME schedule with a Unix timestamp
     * sufficiently far in the future is considered valid.
     */
    @Test
    void validate_whenOneTimeScheduleIsInFuture_doesNotThrow() {
        String futureTimestamp = String.valueOf(
                Instant.now()
                        .plusSeconds(3600)
                        .getEpochSecond()
        );

        Schedule schedule = Schedule.builder()
                .type(ScheduleType.ONE_TIME)
                .expression(futureTimestamp)
                .build();

        assertDoesNotThrow(
                () -> scheduleValidator.validate(schedule)
        );
    }

    /**
     * Verifies that a ONE_TIME schedule whose execution time
     * has already passed is rejected.
     */
    @Test
    void validate_whenOneTimeScheduleIsInPast_throwsIllegalArgumentException() {
        String pastTimestamp = String.valueOf(
                Instant.now()
                        .minusSeconds(3600)
                        .getEpochSecond()
        );

        Schedule schedule = Schedule.builder()
                .type(ScheduleType.ONE_TIME)
                .expression(pastTimestamp)
                .build();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> scheduleValidator.validate(schedule)
        );

        assertEquals(
                "ONE_TIME execution must be in the future",
                exception.getMessage()
        );
    }

    /**
     * Verifies that a ONE_TIME schedule must contain a numeric
     * Unix timestamp rather than an arbitrary string.
     */
    @Test
    void validate_whenOneTimeExpressionIsNotUnixTimestamp_throwsIllegalArgumentException() {
        Schedule schedule = Schedule.builder()
                .type(ScheduleType.ONE_TIME)
                .expression("tomorrow")
                .build();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> scheduleValidator.validate(schedule)
        );

        assertEquals(
                "ONE_TIME schedule expression must be a Unix Timestamp",
                exception.getMessage()
        );

        assertInstanceOf(NumberFormatException.class, exception.getCause());
    }

    /**
     * Verifies that a valid five-field CRON expression is accepted.
     * The validator should normalize it by adding the seconds field
     * required by Spring's CronExpression parser.
     */
    @Test
    void validate_whenCronHasFiveFields_doesNotThrow() {
        Schedule schedule = Schedule.builder()
                .type(ScheduleType.CRON)
                .expression("30 15 * * 5")
                .build();

        assertDoesNotThrow(
                () -> scheduleValidator.validate(schedule)
        );
    }

    /**
     * Verifies that a valid six-field CRON expression is accepted
     * without requiring normalization.
     */
    @Test
    void validate_whenCronHasSixFields_doesNotThrow() {
        Schedule schedule = Schedule.builder()
                .type(ScheduleType.CRON)
                .expression("0 30 15 * * 5")
                .build();

        assertDoesNotThrow(
                () -> scheduleValidator.validate(schedule)
        );
    }

    /**
     * Verifies that surrounding whitespace does not cause
     * an otherwise valid CRON expression to be rejected.
     */
    @Test
    void validate_whenCronContainsSurroundingWhitespace_doesNotThrow() {
        Schedule schedule = Schedule.builder()
                .type(ScheduleType.CRON)
                .expression("   30 15 * * 5   ")
                .build();

        assertDoesNotThrow(
                () -> scheduleValidator.validate(schedule)
        );
    }

    /**
     * Verifies that a CRON expression with fewer than five fields
     * is rejected.
     */
    @Test
    void validate_whenCronHasTooFewFields_throwsIllegalArgumentException() {
        Schedule schedule = Schedule.builder()
                .type(ScheduleType.CRON)
                .expression("30 15 * *")
                .build();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> scheduleValidator.validate(schedule)
        );

        assertEquals(
                "Invalid CRON expression: 30 15 * *",
                exception.getMessage()
        );

        assertInstanceOf(IllegalArgumentException.class, exception.getCause());

        assertEquals(
                "CRON expression must contain 5 or 6 fields",
                exception.getCause().getMessage()
        );
    }

    /**
     * Verifies that a CRON expression with more than six fields
     * is rejected.
     */
    @Test
    void validate_whenCronHasTooManyFields_throwsIllegalArgumentException() {
        Schedule schedule = Schedule.builder()
                .type(ScheduleType.CRON)
                .expression("0 30 15 * * 5 EXTRA")
                .build();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> scheduleValidator.validate(schedule)
        );

        assertEquals(
                "Invalid CRON expression: 0 30 15 * * 5 EXTRA",
                exception.getMessage()
        );

        assertEquals(
                "CRON expression must contain 5 or 6 fields",
                exception.getCause().getMessage()
        );
    }

    /**
     * Verifies that a CRON expression with the correct number
     * of fields but invalid CRON syntax is rejected.
     */
    @Test
    void validate_whenCronSyntaxIsInvalid_throwsIllegalArgumentException() {
        Schedule schedule = Schedule.builder()
                .type(ScheduleType.CRON)
                .expression("100 15 * * 5")
                .build();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> scheduleValidator.validate(schedule)
        );

        assertEquals(
                "Invalid CRON expression: 100 15 * * 5",
                exception.getMessage()
        );

        assertInstanceOf(IllegalArgumentException.class, exception.getCause());
    }
}
