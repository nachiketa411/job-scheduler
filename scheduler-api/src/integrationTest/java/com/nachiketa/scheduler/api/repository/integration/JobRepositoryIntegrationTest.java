package com.nachiketa.scheduler.api.repository.integration;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import com.nachiketa.scheduler.api.mapper.ParameterDocumentMapper;
import com.nachiketa.scheduler.api.repository.ITaskRepository;
import com.nachiketa.scheduler.api.repository.JobExecutionRepositoryImpl;
import com.nachiketa.scheduler.api.repository.JobRepositoryImpl;
import com.nachiketa.scheduler.api.repository.TaskRepositoryImpl;
import com.nachiketa.scheduler.model.ExecutionStatus;
import com.nachiketa.scheduler.model.Job;
import com.nachiketa.scheduler.model.JobExecution;
import com.nachiketa.scheduler.model.ParameterType;
import com.nachiketa.scheduler.model.Schedule;
import com.nachiketa.scheduler.model.ScheduleType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.cassandra.CassandraContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.smithy.java.core.serde.document.Document;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Testcontainers
public class JobRepositoryIntegrationTest {

    @Container
    static final CassandraContainer cassandra =
            new CassandraContainer(
                    DockerImageName.parse("cassandra:5.0")
            )
                    .withInitScript("cassandra/schema.cql");

    private static CqlSession session;

    private ITaskRepository taskRepository;
    private ParameterDocumentMapper parameterMapper;

    private JobRepositoryImpl repository;

    @BeforeAll
    static void setUpCassandra() {
        session = CqlSession.builder()
                .addContactPoint(
                        cassandra.getContactPoint()
                )
                .withLocalDatacenter(
                        cassandra.getLocalDatacenter()
                )
                .withKeyspace("scheduler")
                .build();
    }

    @BeforeEach
    void setUp() {
        session.execute(
                "TRUNCATE scheduler.jobs"
        );

        session.execute(
                "TRUNCATE scheduler.tasks"
        );

        session.execute(
                "TRUNCATE scheduler.job_executions"
        );

        TaskRepositoryImpl taskRepository =
                new TaskRepositoryImpl(session);

        ParameterDocumentMapper parameterMapper =
                new ParameterDocumentMapper();

        repository = new JobRepositoryImpl(
                session,
                taskRepository,
                parameterMapper
        );
    }

    @AfterAll
    static void tearDown() {
        if (session != null) {
            session.close();
        }
    }

    /**
     * Verifies that a Job can be persisted to real Cassandra
     * and subsequently reconstructed through the repository.
     */
    @Test
    void saveAndFind_roundTripsJobThroughCassandra() {
        UUID jobId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        insertTask(taskId);

        Instant createdAt =
                Instant.parse("2026-10-26T18:00:00Z");

        Document parameters =
                Document.of(
                        Map.of(
                                "recipient",
                                Document.of("user@example.com"),

                                "retryCount",
                                Document.of(3),

                                "enabled",
                                Document.of(true)
                        )
                );

        Schedule schedule =
                Schedule.builder()
                        .type(ScheduleType.CRON)
                        .expression("30 15 * * 5")
                        .build();

        Job job =
                Job.builder()
                        .jobId(jobId.toString())
                        .name("weekly-email")
                        .taskId(taskId.toString())
                        .parameters(parameters)
                        .schedule(schedule)
                        .createdAt(createdAt)
                        .build();

        repository.save(job);

        Optional<Job> result =
                repository.findById(jobId);

        assertTrue(result.isPresent());

        Job savedJob =
                result.get();

        assertEquals(
                jobId.toString(),
                savedJob.getJobId()
        );

        assertEquals(
                "weekly-email",
                savedJob.getName()
        );

        assertEquals(
                taskId.toString(),
                savedJob.getTaskId()
        );

        assertEquals(
                ScheduleType.CRON,
                savedJob.getSchedule().getType()
        );

        assertEquals(
                "30 15 * * 5",
                savedJob.getSchedule().getExpression()
        );

        assertEquals(
                createdAt,
                savedJob.getCreatedAt()
        );

        Map<String, Document> savedParameters =
                savedJob.getParameters()
                        .asStringMap();

        assertEquals(
                "user@example.com",
                savedParameters
                        .get("recipient")
                        .asString()
        );

        assertEquals(
                3,
                savedParameters
                        .get("retryCount")
                        .asInteger()
        );

        assertTrue(
                savedParameters
                        .get("enabled")
                        .asBoolean()
        );
    }

    @Test
    void findById_whenJobDoesNotExist_returnsEmpty() {
        UUID jobId = UUID.randomUUID();

        Optional<Job> result =
                repository.findById(jobId);

        assertTrue(result.isEmpty());
    }

    /**
     * Verifies that saveWithFirstExecution persists both the job
     * and its first execution to real Cassandra.
     */
    @Test
    void saveWithFirstExecution_persistsJobAndExecution() {
        UUID jobId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        insertTask(taskId);

        Job job =
                Job.builder()
                        .jobId(jobId.toString())
                        .name("weekly-email")
                        .taskId(taskId.toString())
                        .parameters(
                                Document.of(
                                        Map.of(
                                                "recipient", Document.of("user@example.com"),
                                                "retryCount", Document.of(3),
                                                "enabled", Document.of(true)
                                        )
                                )
                        )
                        .schedule(
                                Schedule.builder()
                                        .type(ScheduleType.CRON)
                                        .expression("30 15 * * 5")
                                        .build()
                        )
                        .createdAt(Instant.parse("2026-10-26T18:00:00Z"))
                        .build();

        Instant executionTime =
                Instant.parse("2026-10-30T15:30:00Z");

        JobExecution execution =
                JobExecution.builder()
                        .jobId(jobId.toString())
                        .executionTime(executionTime)
                        .status(ExecutionStatus.PENDING)
                        .attempt(0)
                        .build();

        repository.saveWithFirstExecution(job, execution);

        assertTrue(
                repository.findById(jobId).isPresent()
        );

        Optional<JobExecution> savedExecution =
                new JobExecutionRepositoryImpl(session)
                        .find(jobId, executionTime);

        assertTrue(savedExecution.isPresent());
        assertEquals(
                ExecutionStatus.PENDING,
                savedExecution.get().getStatus()
        );
    }

    private static void insertTask(UUID taskId) {
        Map<String, String> parameterSchema =
                Map.of(
                        "recipient", "STRING",
                        "retryCount", "INTEGER",
                        "enabled", "BOOLEAN"
                );

        session.execute(
                SimpleStatement.newInstance(
                        """
                        INSERT INTO tasks (
                            task_id,
                            name,
                            parameter_schema
                        )
                        VALUES (?, ?, ?)
                        """,
                        taskId,
                        "send-email",
                        parameterSchema
                )
        );
    }
}
