package com.nachiketa.scheduler.api.repository.integration;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import com.nachiketa.scheduler.api.repository.TaskRepositoryImpl;
import com.nachiketa.scheduler.model.ParameterType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.cassandra.CassandraContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
public class TaskRepositoryIntegrationTest {

    @Container
    static final CassandraContainer cassandra =
            new CassandraContainer(
                    DockerImageName.parse("cassandra:5.0")
            )
                    .withInitScript("cassandra/schema.cql");

    private static CqlSession session;
    private static TaskRepositoryImpl repository;

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

        repository =
                new TaskRepositoryImpl(session);
    }

    @BeforeEach
    void cleanDatabase() {
        session.execute(
                "TRUNCATE scheduler.tasks"
        );
    }

    @AfterAll
    static void tearDown() {
        if (session != null) {
            session.close();
        }
    }

    /**
     * Verifies that a task parameter schema stored in Cassandra
     * is converted into the corresponding ParameterType values.
     */
    @Test
    void findParameterSchema_whenTaskExists_returnsMappedSchema() {
        UUID taskId = UUID.randomUUID();

        Map<String, String> storedSchema = Map.of(
                "recipient", "STRING",
                "retryCount", "INTEGER",
                "sendAt", "TIMESTAMP"
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
                        storedSchema
                )
        );

        Optional<Map<String, ParameterType>> result =
                repository.findParameterSchema(taskId);

        assertTrue(result.isPresent());

        Map<String, ParameterType> schema =
                result.get();

        assertEquals(
                3,
                schema.size()
        );

        assertEquals(
                ParameterType.STRING,
                schema.get("recipient")
        );

        assertEquals(
                ParameterType.INTEGER,
                schema.get("retryCount")
        );

        assertEquals(
                ParameterType.TIMESTAMP,
                schema.get("sendAt")
        );
    }

    /**
     * Verifies that looking up a task that does not exist
     * returns an empty Optional.
     */
    @Test
    void findParameterSchema_whenTaskDoesNotExist_returnsEmpty() {
        UUID taskId = UUID.randomUUID();

        Optional<Map<String, ParameterType>> result =
                repository.findParameterSchema(taskId);

        assertTrue(result.isEmpty());
    }

    /**
     * Verifies that a task with no parameter schema is returned
     * as an empty application-level schema rather than null.
     */
    @Test
    void findParameterSchema_whenStoredSchemaIsNull_returnsEmptyMap() {
        UUID taskId = UUID.randomUUID();

        session.execute(
                SimpleStatement.newInstance(
                        """
                        INSERT INTO tasks (
                            task_id,
                            name
                        )
                        VALUES (?, ?)
                        """,
                        taskId,
                        "no-parameter-task"
                )
        );

        Optional<Map<String, ParameterType>> result =
                repository.findParameterSchema(taskId);

        assertTrue(result.isPresent());

        assertTrue(
                result.get().isEmpty()
        );
    }

    /**
     * Verifies that all currently supported ParameterType values
     * can be reconstructed from Cassandra.
     */
    @Test
    void findParameterSchema_mapsAllSupportedParameterTypes() {
        UUID taskId = UUID.randomUUID();

        Map<String, String> storedSchema = Map.of(
                "stringValue", "STRING",
                "integerValue", "INTEGER",
                "longValue", "LONG",
                "booleanValue", "BOOLEAN",
                "floatValue", "FLOAT",
                "doubleValue", "DOUBLE",
                "timestampValue", "TIMESTAMP"
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
                        "all-parameter-types",
                        storedSchema
                )
        );

        Map<String, ParameterType> result =
                repository.findParameterSchema(taskId)
                        .orElseThrow();

        assertEquals(
                ParameterType.STRING,
                result.get("stringValue")
        );

        assertEquals(
                ParameterType.INTEGER,
                result.get("integerValue")
        );

        assertEquals(
                ParameterType.LONG,
                result.get("longValue")
        );

        assertEquals(
                ParameterType.BOOLEAN,
                result.get("booleanValue")
        );

        assertEquals(
                ParameterType.FLOAT,
                result.get("floatValue")
        );

        assertEquals(
                ParameterType.DOUBLE,
                result.get("doubleValue")
        );

        assertEquals(
                ParameterType.TIMESTAMP,
                result.get("timestampValue")
        );
    }

    @Test
    void findParameterSchema_whenStoredTypeIsUnsupported_throwsException() {
        UUID taskId = UUID.randomUUID();

        Map<String, String> storedSchema = Map.of(
                "amount", "DECIMAL"
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
                        "invalid-task",
                        storedSchema
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> repository.findParameterSchema(taskId)
        );
    }
}
