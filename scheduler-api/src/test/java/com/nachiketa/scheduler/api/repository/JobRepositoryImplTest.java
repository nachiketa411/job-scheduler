package com.nachiketa.scheduler.api.repository;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import com.nachiketa.scheduler.api.mapper.ParameterDocumentMapper;
import com.nachiketa.scheduler.model.Job;
import com.nachiketa.scheduler.model.ParameterType;
import com.nachiketa.scheduler.model.Schedule;
import com.nachiketa.scheduler.model.ScheduleType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.smithy.java.core.serde.document.Document;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class JobRepositoryImplTest {

    private CqlSession session;
    private ITaskRepository taskRepository;
    private ParameterDocumentMapper parameterMapper;

    private JobRepositoryImpl jobRepository;

    @BeforeEach
    void setUp() {
        session = mock(CqlSession.class);
        taskRepository = mock(ITaskRepository.class);
        parameterMapper = mock(ParameterDocumentMapper.class);

        jobRepository = new JobRepositoryImpl(
                session,
                taskRepository,
                parameterMapper
        );
    }

    /**
     * Verifies that saving a job converts its Smithy Document parameters
     * into the Cassandra storage representation before persistence.
     */
    @Test
    void save_convertsParametersToStorageFormat() {
        UUID jobId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        Document parameters = mock(Document.class);

        Map<String, String> storedParameters = Map.of(
                "recipient", "user@example.com",
                "retryCount", "3"
        );

        when(parameterMapper.toStorage(parameters))
                .thenReturn(storedParameters);

        Job job = createJob(
                jobId,
                taskId,
                parameters
        );

        jobRepository.save(job);

        verify(parameterMapper)
                .toStorage(parameters);
    }

    /**
     * Verifies that saving a job produces an INSERT statement containing
     * the expected Cassandra column values.
     */
    @Test
    void save_executesInsertWithExpectedValues() {
        UUID jobId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        Document parameters = mock(Document.class);

        Map<String, String> storedParameters = Map.of(
                "recipient", "user@example.com"
        );

        when(parameterMapper.toStorage(parameters))
                .thenReturn(storedParameters);

        Job job = createJob(
                jobId,
                taskId,
                parameters
        );

        jobRepository.save(job);

        ArgumentCaptor<SimpleStatement> statementCaptor =
                ArgumentCaptor.forClass(SimpleStatement.class);

        verify(session)
                .execute(statementCaptor.capture());

        SimpleStatement statement =
                statementCaptor.getValue();

        assertTrue(
                statement.getQuery().contains("INSERT INTO jobs")
        );

        assertEquals(
                jobId,
                statement.getPositionalValues().get(0)
        );

        assertEquals(
                "weekly-email",
                statement.getPositionalValues().get(1)
        );

        assertEquals(
                taskId,
                statement.getPositionalValues().get(2)
        );

        assertEquals(
                storedParameters,
                statement.getPositionalValues().get(3)
        );

        assertEquals(
                "CRON",
                statement.getPositionalValues().get(4)
        );

        assertEquals(
                "30 15 * * 5",
                statement.getPositionalValues().get(5)
        );

        assertEquals(
                Instant.parse("2026-10-26T18:00:00Z"),
                statement.getPositionalValues().get(6)
        );
    }

    private Job createJob(
            UUID jobId,
            UUID taskId,
            Document parameters) {

        Schedule schedule = Schedule.builder()
                .type(ScheduleType.CRON)
                .expression("30 15 * * 5")
                .build();

        return Job.builder()
                .jobId(jobId.toString())
                .name("weekly-email")
                .taskId(taskId.toString())
                .parameters(parameters)
                .schedule(schedule)
                .createdAt(
                        Instant.parse("2026-10-26T18:00:00Z")
                )
                .build();
    }

    /**
     * Verifies that findById returns an empty Optional
     * when Cassandra does not contain the requested job.
     */
    @Test
    void findById_whenJobDoesNotExist_returnsEmpty() {
        UUID jobId = UUID.randomUUID();

        ResultSet resultSet = mock(ResultSet.class);

        when(session.execute(any(SimpleStatement.class)))
                .thenReturn(resultSet);

        when(resultSet.one())
                .thenReturn(null);

        Optional<Job> result =
                jobRepository.findById(jobId);

        assertTrue(result.isEmpty());
    }

    /**
     * Verifies that findById reconstructs a complete Job from the Cassandra row,
     * including parameters and schedule information.
     */
    @Test
    void findById_whenJobExists_returnsMappedJob() {
        UUID jobId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        Instant createdAt =
                Instant.parse("2026-10-26T18:00:00Z");

        Map<String, String> storedParameters = Map.of(
                "recipient", "user@example.com",
                "retryCount", "3"
        );

        @SuppressWarnings("unchecked")
        Map<String, ParameterType> parameterSchema = mock(Map.class);

        Document parameters = mock(Document.class);

        Row row = mock(Row.class);
        ResultSet resultSet = mock(ResultSet.class);

        when(session.execute(any(SimpleStatement.class)))
                .thenReturn(resultSet);

        when(resultSet.one())
                .thenReturn(row);

        when(row.getUuid("job_id"))
                .thenReturn(jobId);

        when(row.getUuid("task_id"))
                .thenReturn(taskId);

        when(row.getString("name"))
                .thenReturn("weekly-email");

        when(row.getInstant("created_at"))
                .thenReturn(createdAt);

        when(row.getMap(
                "parameters",
                String.class,
                String.class
        )).thenReturn(storedParameters);

        when(row.getString("schedule_type"))
                .thenReturn("CRON");

        when(row.getString("schedule_expression"))
                .thenReturn("30 15 * * 5");

        when(taskRepository.findParameterSchema(taskId))
                .thenReturn(Optional.of(parameterSchema));

        when(parameterMapper.fromStorage(
                storedParameters,
                parameterSchema
        )).thenReturn(parameters);

        Optional<Job> result =
                jobRepository.findById(jobId);

        assertTrue(result.isPresent());

        Job job = result.get();

        assertEquals(
                jobId.toString(),
                job.getJobId()
        );

        assertEquals(
                taskId.toString(),
                job.getTaskId()
        );

        assertEquals(
                "weekly-email",
                job.getName()
        );

        assertEquals(
                parameters,
                job.getParameters()
        );

        assertEquals(
                ScheduleType.CRON,
                job.getSchedule().getType()
        );

        assertEquals(
                "30 15 * * 5",
                job.getSchedule().getExpression()
        );

        assertEquals(
                createdAt,
                job.getCreatedAt()
        );

        verify(taskRepository)
                .findParameterSchema(taskId);

        verify(parameterMapper)
                .fromStorage(
                        storedParameters,
                        parameterSchema
                );
    }

    /**
     * Verifies that a null Cassandra parameter map is treated as empty
     * when reconstructing the Smithy parameter Document.
     */
    @Test
    void findById_whenStoredParametersAreNull_usesEmptyMap() {
        UUID jobId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        @SuppressWarnings("unchecked")
        Map<String, ParameterType> parameterSchema = mock(Map.class);

        Document parameters = mock(Document.class);

        Row row = mock(Row.class);
        ResultSet resultSet = mock(ResultSet.class);

        when(session.execute(any(SimpleStatement.class)))
                .thenReturn(resultSet);

        when(resultSet.one())
                .thenReturn(row);

        when(row.getUuid("job_id"))
                .thenReturn(jobId);

        when(row.getUuid("task_id"))
                .thenReturn(taskId);

        when(row.getString("name"))
                .thenReturn("weekly-email");

        when(row.getInstant("created_at"))
                .thenReturn(
                        Instant.parse("2026-10-26T18:00:00Z")
                );

        when(row.getMap(
                "parameters",
                String.class,
                String.class
        )).thenReturn(null);

        when(row.getString("schedule_type"))
                .thenReturn("CRON");

        when(row.getString("schedule_expression"))
                .thenReturn("30 15 * * 5");

        when(taskRepository.findParameterSchema(taskId))
                .thenReturn(Optional.of(parameterSchema));

        when(parameterMapper.fromStorage(
                Map.of(),
                parameterSchema
        )).thenReturn(parameters);

        Optional<Job> result =
                jobRepository.findById(jobId);

        assertTrue(result.isPresent());

        verify(parameterMapper)
                .fromStorage(
                        Map.of(),
                        parameterSchema
                );
    }

    /**
     * Verifies that job reconstruction fails when the referenced task
     * no longer exists and its parameter schema cannot be loaded.
     */
    @Test
    void findById_whenTaskDoesNotExist_throwsIllegalStateException() {
        UUID jobId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        Row row = mock(Row.class);
        ResultSet resultSet = mock(ResultSet.class);

        when(session.execute(any(SimpleStatement.class)))
                .thenReturn(resultSet);

        when(resultSet.one())
                .thenReturn(row);

        when(row.getUuid("job_id"))
                .thenReturn(jobId);

        when(row.getUuid("task_id"))
                .thenReturn(taskId);

        when(row.getString("name"))
                .thenReturn("weekly-email");

        when(row.getInstant("created_at"))
                .thenReturn(
                        Instant.parse("2026-10-26T18:00:00Z")
                );

        when(row.getMap(
                "parameters",
                String.class,
                String.class
        )).thenReturn(Map.of());

        when(taskRepository.findParameterSchema(taskId))
                .thenReturn(Optional.empty());

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> jobRepository.findById(jobId)
        );

        assertEquals(
                "Task does not exist: " + taskId,
                exception.getMessage()
        );
    }
}
