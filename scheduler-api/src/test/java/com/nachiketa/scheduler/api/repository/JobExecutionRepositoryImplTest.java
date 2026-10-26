package com.nachiketa.scheduler.api.repository;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import com.nachiketa.scheduler.model.ExecutionStatus;
import com.nachiketa.scheduler.model.JobExecution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class JobExecutionRepositoryImplTest {

    private CqlSession session;
    private JobExecutionRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        session = mock(CqlSession.class);
        repository = new JobExecutionRepositoryImpl(session);
    }

    /**
     * Verifies that saving an execution calculates the correct
     * five-minute bucket and persists all execution fields.
     */
    @Test
    void save_persistsExecutionWithCorrectTimeBucket() {
        UUID jobId = UUID.randomUUID();

        Instant executionTime =
                Instant.parse("2026-10-30T15:32:45Z");

        Instant expectedBucket =
                Instant.parse("2026-10-30T15:30:00Z");

        Instant startedAt =
                Instant.parse("2026-10-30T15:32:46Z");

        Instant completedAt =
                Instant.parse("2026-10-30T15:33:00Z");

        JobExecution execution = JobExecution.builder()
                .jobId(jobId.toString())
                .executionTime(executionTime)
                .status(ExecutionStatus.PENDING)
                .attempt(2)
                .startedAt(startedAt)
                .completedAt(completedAt)
                .error("test-error")
                .build();

        repository.save(execution);

        ArgumentCaptor<SimpleStatement> captor =
                ArgumentCaptor.forClass(SimpleStatement.class);

        verify(session)
                .execute(captor.capture());

        SimpleStatement statement =
                captor.getValue();

        assertTrue(
                statement.getQuery()
                        .contains("INSERT INTO job_executions")
        );

        assertEquals(
                expectedBucket,
                statement.getPositionalValues().get(0)
        );

        assertEquals(
                executionTime,
                statement.getPositionalValues().get(1)
        );

        assertEquals(
                jobId,
                statement.getPositionalValues().get(2)
        );

        assertEquals(
                "PENDING",
                statement.getPositionalValues().get(3)
        );

        assertEquals(
                2,
                statement.getPositionalValues().get(4)
        );

        assertEquals(
                startedAt,
                statement.getPositionalValues().get(5)
        );

        assertEquals(
                completedAt,
                statement.getPositionalValues().get(6)
        );

        assertEquals(
                "test-error",
                statement.getPositionalValues().get(7)
        );
    }

    /**
     * Verifies that find calculates the same five-minute bucket
     * used during persistence and queries by bucket, execution time,
     * and job ID.
     */
    @Test
    void find_usesCorrectTimeBucketAndExecutionKey() {
        UUID jobId = UUID.randomUUID();

        Instant executionTime =
                Instant.parse("2026-10-30T15:32:45Z");

        Instant expectedBucket =
                Instant.parse("2026-10-30T15:30:00Z");

        ResultSet resultSet = mock(ResultSet.class);

        when(session.execute(any(SimpleStatement.class)))
                .thenReturn(resultSet);

        when(resultSet.one())
                .thenReturn(null);

        Optional<JobExecution> result =
                repository.find(
                        jobId,
                        executionTime
                );

        assertTrue(result.isEmpty());

        ArgumentCaptor<SimpleStatement> captor =
                ArgumentCaptor.forClass(SimpleStatement.class);

        verify(session)
                .execute(captor.capture());

        SimpleStatement statement =
                captor.getValue();

        assertEquals(
                expectedBucket,
                statement.getPositionalValues().get(0)
        );

        assertEquals(
                executionTime,
                statement.getPositionalValues().get(1)
        );

        assertEquals(
                jobId,
                statement.getPositionalValues().get(2)
        );
    }

    /**
     * Verifies that an execution row returned from Cassandra
     * is reconstructed into the expected JobExecution model.
     */
    @Test
    void find_whenExecutionExists_returnsMappedExecution() {
        UUID jobId = UUID.randomUUID();

        Instant executionTime =
                Instant.parse("2026-10-30T15:30:00Z");

        Instant startedAt =
                Instant.parse("2026-10-30T15:30:01Z");

        Instant completedAt =
                Instant.parse("2026-10-30T15:30:10Z");

        Row row = mock(Row.class);
        ResultSet resultSet = mock(ResultSet.class);

        when(session.execute(any(SimpleStatement.class)))
                .thenReturn(resultSet);

        when(resultSet.one())
                .thenReturn(row);

        when(row.getUuid("job_id"))
                .thenReturn(jobId);

        when(row.getInstant("execution_time"))
                .thenReturn(executionTime);

        when(row.getString("status"))
                .thenReturn("SUCCEEDED");

        when(row.getInt("attempt"))
                .thenReturn(1);

        when(row.getInstant("started_at"))
                .thenReturn(startedAt);

        when(row.getInstant("completed_at"))
                .thenReturn(completedAt);

        when(row.getString("error"))
                .thenReturn("none");

        Optional<JobExecution> result =
                repository.find(
                        jobId,
                        executionTime
                );

        assertTrue(result.isPresent());

        JobExecution execution =
                result.get();

        assertEquals(
                jobId.toString(),
                execution.getJobId()
        );

        assertEquals(
                executionTime,
                execution.getExecutionTime()
        );

        assertEquals(
                ExecutionStatus.SUCCEEDED,
                execution.getStatus()
        );

        assertEquals(
                1,
                execution.getAttempt()
        );

        assertEquals(
                startedAt,
                execution.getStartedAt()
        );

        assertEquals(
                completedAt,
                execution.getCompletedAt()
        );

        assertEquals(
                "none",
                execution.getError()
        );
    }

    /**
     * Verifies that findInWindow uses the requested bucket and
     * half-open execution-time range [from, to).
     */
    @Test
    void findInWindow_queriesRequestedBucketAndTimeRange() {
        Instant bucket =
                Instant.parse("2026-10-30T15:30:00Z");

        Instant from =
                Instant.parse("2026-10-30T15:30:00Z");

        Instant to =
                Instant.parse("2026-10-30T15:35:00Z");

        ResultSet resultSet = mock(ResultSet.class);

        when(session.execute(any(SimpleStatement.class)))
                .thenReturn(resultSet);

        when(resultSet.iterator())
                .thenReturn(Collections.emptyIterator());

        List<JobExecution> result =
                repository.findInWindow(
                        bucket,
                        from,
                        to
                );

        assertTrue(result.isEmpty());

        ArgumentCaptor<SimpleStatement> captor =
                ArgumentCaptor.forClass(SimpleStatement.class);

        verify(session)
                .execute(captor.capture());

        SimpleStatement statement =
                captor.getValue();

        assertTrue(
                statement.getQuery()
                        .contains("execution_time >= ?")
        );

        assertTrue(
                statement.getQuery()
                        .contains("execution_time < ?")
        );

        assertEquals(
                bucket,
                statement.getPositionalValues().get(0)
        );

        assertEquals(
                from,
                statement.getPositionalValues().get(1)
        );

        assertEquals(
                to,
                statement.getPositionalValues().get(2)
        );
    }

    /**
     * Verifies that findInWindow converts every Cassandra row
     * in the requested window into a JobExecution.
     */
    @Test
    void findInWindow_whenRowsExist_returnsAllExecutions() {
        Instant bucket =
                Instant.parse("2026-10-30T15:30:00Z");

        Instant from =
                Instant.parse("2026-10-30T15:30:00Z");

        Instant to =
                Instant.parse("2026-10-30T15:35:00Z");

        Row firstRow =
                createExecutionRow(
                        UUID.randomUUID(),
                        Instant.parse("2026-10-30T15:31:00Z")
                );

        Row secondRow =
                createExecutionRow(
                        UUID.randomUUID(),
                        Instant.parse("2026-10-30T15:33:00Z")
                );

        ResultSet resultSet = mock(ResultSet.class);

        when(session.execute(any(SimpleStatement.class)))
                .thenReturn(resultSet);

        when(resultSet.iterator())
                .thenReturn(
                        List.of(
                                firstRow,
                                secondRow
                        ).iterator()
                );

        List<JobExecution> result =
                repository.findInWindow(
                        bucket,
                        from,
                        to
                );

        assertEquals(
                2,
                result.size()
        );
    }

    /**
     * Verifies that executions with optional lifecycle fields unset
     * can still be reconstructed from Cassandra.
     */
    @Test
    void find_whenOptionalExecutionFieldsAreNull_returnsExecution() {
        UUID jobId = UUID.randomUUID();

        Instant executionTime =
                Instant.parse("2026-10-30T15:30:00Z");

        Row row = mock(Row.class);
        ResultSet resultSet = mock(ResultSet.class);

        when(session.execute(any(SimpleStatement.class)))
                .thenReturn(resultSet);

        when(resultSet.one())
                .thenReturn(row);

        when(row.getUuid("job_id"))
                .thenReturn(jobId);

        when(row.getInstant("execution_time"))
                .thenReturn(executionTime);

        when(row.getString("status"))
                .thenReturn(
                        ExecutionStatus.PENDING.getValue()
                );

        when(row.getInt("attempt"))
                .thenReturn(0);

        when(row.getInstant("started_at"))
                .thenReturn(null);

        when(row.getInstant("completed_at"))
                .thenReturn(null);

        when(row.getString("error"))
                .thenReturn(null);

        Optional<JobExecution> result =
                repository.find(
                        jobId,
                        executionTime
                );

        assertTrue(result.isPresent());

        JobExecution execution = result.get();

        assertEquals(
                jobId.toString(),
                execution.getJobId()
        );

        assertEquals(
                executionTime,
                execution.getExecutionTime()
        );

        assertEquals(
                ExecutionStatus.PENDING,
                execution.getStatus()
        );

        assertEquals(
                0,
                execution.getAttempt()
        );

        assertNull(execution.getStartedAt());
        assertNull(execution.getCompletedAt());
        assertNull(execution.getError());
    }

    private Row createExecutionRow(
            UUID jobId,
            Instant executionTime) {

        Row row = mock(Row.class);

        when(row.getUuid("job_id"))
                .thenReturn(jobId);

        when(row.getInstant("execution_time"))
                .thenReturn(executionTime);

        when(row.getString("status"))
                .thenReturn("PENDING");

        when(row.getInt("attempt"))
                .thenReturn(0);

        when(row.getInstant("started_at"))
                .thenReturn(
                        executionTime.plusSeconds(1)
                );

        when(row.getInstant("completed_at"))
                .thenReturn(
                        executionTime.plusSeconds(10)
                );

        when(row.getString("error"))
                .thenReturn("none");

        return row;
    }
}
