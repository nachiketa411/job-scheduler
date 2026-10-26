package com.nachiketa.scheduler.api.repository.integration;

import com.datastax.oss.driver.api.core.CqlSession;
import com.nachiketa.scheduler.api.mapper.ParameterDocumentMapper;
import com.nachiketa.scheduler.api.repository.ITaskRepository;
import com.nachiketa.scheduler.api.repository.JobRepositoryImpl;
import com.nachiketa.scheduler.model.Job;
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

        taskRepository = mock(ITaskRepository.class);
        parameterMapper = mock(ParameterDocumentMapper.class);

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

        Instant createdAt =
                Instant.parse("2026-10-26T18:00:00Z");

        Document inputParameters =
                mock(Document.class);

        Document reconstructedParameters =
                mock(Document.class);

        Map<String, String> storedParameters = Map.of(
                "recipient", "user@example.com",
                "retryCount", "3"
        );

        @SuppressWarnings("unchecked")
        Map<String, ParameterType> parameterSchema =
                mock(Map.class);

        when(parameterMapper.toStorage(inputParameters))
                .thenReturn(storedParameters);

        when(taskRepository.findParameterSchema(taskId))
                .thenReturn(
                        Optional.of(parameterSchema)
                );

        when(parameterMapper.fromStorage(
                storedParameters,
                parameterSchema
        )).thenReturn(reconstructedParameters);

        Schedule schedule = Schedule.builder()
                .type(ScheduleType.CRON)
                .expression("30 15 * * 5")
                .build();

        Job job = Job.builder()
                .jobId(jobId.toString())
                .name("weekly-email")
                .taskId(taskId.toString())
                .parameters(inputParameters)
                .schedule(schedule)
                .createdAt(createdAt)
                .build();

        repository.save(job);

        Optional<Job> result =
                repository.findById(jobId);

        assertTrue(result.isPresent());

        Job savedJob = result.get();

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

        assertSame(
                reconstructedParameters,
                savedJob.getParameters()
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

        verify(parameterMapper)
                .toStorage(inputParameters);

        verify(taskRepository)
                .findParameterSchema(taskId);

        verify(parameterMapper)
                .fromStorage(
                        storedParameters,
                        parameterSchema
                );
    }

    /**
     * Verifies that querying real Cassandra for a job that
     * does not exist returns an empty Optional.
     */
    @Test
    void findById_whenJobDoesNotExist_returnsEmpty() {
        UUID jobId = UUID.randomUUID();

        Optional<Job> result =
                repository.findById(jobId);

        assertTrue(result.isEmpty());
    }
}
