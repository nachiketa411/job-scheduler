package com.nachiketa.scheduler.api.repository.integration;

import com.datastax.oss.driver.api.core.CqlSession;
import com.nachiketa.scheduler.api.repository.JobExecutionRepositoryImpl;
import com.nachiketa.scheduler.model.ExecutionStatus;
import com.nachiketa.scheduler.model.JobExecution;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.cassandra.CassandraContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
public class JobExecutionRepositoryIntegrationTest {

    @Container
    static final CassandraContainer cassandra = new CassandraContainer(
            DockerImageName.parse("cassandra:5.0")
    ).withInitScript("cassandra/schema.cql");

    private static CqlSession session;
    private static JobExecutionRepositoryImpl repository;

    @BeforeAll
    static void setUp() {
        session = CqlSession.builder()
                .addContactPoint(
                        cassandra.getContactPoint()
                )
                .withLocalDatacenter(
                        cassandra.getLocalDatacenter()
                )
                .withKeyspace("scheduler")
                .build();

        repository = new JobExecutionRepositoryImpl(session);
    }

    @BeforeEach
    void cleanDatabase() {
        session.execute(
                "TRUNCATE scheduler.job_executions"
        );
    }

    @AfterAll
    static void tearDown() {
        if (session != null) {
            session.close();
        }
    }

    /**
     * Verifies that a JobExecution can be persisted to real Cassandra
     * and then reconstructed through the repository.
     */
    @Test
    void saveAndFind_roundTripsExecutionThroughCassandra() {
        UUID jobId = UUID.randomUUID();

        Instant executionTime =
                Instant.parse("2026-10-30T15:32:45Z");

        JobExecution execution =
                JobExecution.builder()
                        .jobId(jobId.toString())
                        .executionTime(executionTime)
                        .status(ExecutionStatus.PENDING)
                        .attempt(0)
                        .build();

        repository.save(execution);

        Optional<JobExecution> result =
                repository.find(
                        jobId,
                        executionTime
                );

        assertTrue(result.isPresent());

        JobExecution saved =
                result.get();

        assertEquals(
                jobId.toString(),
                saved.getJobId()
        );

        assertEquals(
                executionTime,
                saved.getExecutionTime()
        );

        assertEquals(
                ExecutionStatus.PENDING,
                saved.getStatus()
        );

        assertEquals(
                0,
                saved.getAttempt()
        );

        assertNull(saved.getStartedAt());
        assertNull(saved.getCompletedAt());
        assertNull(saved.getError());
    }

    /**
     * Verifies that looking up an execution that does not exist
     * returns an empty Optional from real Cassandra.
     */
    @Test
    void find_whenExecutionDoesNotExist_returnsEmpty() {
        UUID jobId = UUID.randomUUID();

        Instant executionTime =
                Instant.parse("2026-10-30T16:00:00Z");

        Optional<JobExecution> result =
                repository.find(
                        jobId,
                        executionTime
                );

        assertTrue(result.isEmpty());
    }

    /**
     * Verifies that multiple executions inside the same five-minute
     * bucket can be queried using an execution-time range.
     */
    @Test
    void findInWindow_returnsExecutionsInsideRequestedWindow() {
        Instant timeBucket =
                Instant.parse("2026-10-30T15:30:00Z");

        JobExecution firstExecution =
                createExecution(
                        UUID.randomUUID(),
                        Instant.parse("2026-10-30T15:31:00Z")
                );

        JobExecution secondExecution =
                createExecution(
                        UUID.randomUUID(),
                        Instant.parse("2026-10-30T15:33:00Z")
                );

        repository.save(firstExecution);
        repository.save(secondExecution);

        List<JobExecution> results =
                repository.findInWindow(
                        timeBucket,
                        Instant.parse("2026-10-30T15:30:00Z"),
                        Instant.parse("2026-10-30T15:35:00Z")
                );

        assertEquals(
                2,
                results.size()
        );

        assertTrue(
                results.stream()
                        .anyMatch(execution ->
                                execution.getJobId()
                                        .equals(firstExecution.getJobId()))
        );

        assertTrue(
                results.stream()
                        .anyMatch(execution ->
                                execution.getJobId()
                                        .equals(secondExecution.getJobId()))
        );
    }

    /**
     * Verifies that the upper bound of findInWindow is exclusive.
     */
    @Test
    void findInWindow_doesNotIncludeExecutionAtUpperBound() {
        Instant timeBucket =
                Instant.parse("2026-10-30T16:00:00Z");

        JobExecution insideWindow =
                createExecution(
                        UUID.randomUUID(),
                        Instant.parse("2026-10-30T16:04:59Z")
                );

        JobExecution upperBound =
                createExecution(
                        UUID.randomUUID(),
                        Instant.parse("2026-10-30T16:05:00Z")
                );

        repository.save(insideWindow);
        repository.save(upperBound);

        List<JobExecution> results =
                repository.findInWindow(
                        timeBucket,
                        Instant.parse("2026-10-30T16:00:00Z"),
                        Instant.parse("2026-10-30T16:05:00Z")
                );

        assertEquals(
                1,
                results.size()
        );

        assertEquals(
                insideWindow.getJobId(),
                results.getFirst().getJobId()
        );
    }

    private static JobExecution createExecution(
            UUID jobId,
            Instant executionTime) {

        return JobExecution.builder()
                .jobId(jobId.toString())
                .executionTime(executionTime)
                .status(ExecutionStatus.PENDING)
                .attempt(0)
                .build();
    }
}
