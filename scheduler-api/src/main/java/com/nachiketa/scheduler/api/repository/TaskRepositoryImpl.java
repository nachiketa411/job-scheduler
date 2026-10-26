package com.nachiketa.scheduler.api.repository;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import com.nachiketa.scheduler.model.ParameterType;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class TaskRepositoryImpl implements ITaskRepository {

    private final CqlSession session;

    public TaskRepositoryImpl(CqlSession session) {
        this.session = session;
    }

    @Override
    public Optional<Map<String, ParameterType>> findParameterSchema(UUID taskId) {
        Row row = session.execute(
                SimpleStatement.newInstance(
                        """
                                SELECT parameter_schema
                                FROM tasks
                                WHERE task_id = ?
                                """,
                        taskId
                )
        ).one();

        if (row == null) {
            return Optional.empty();
        }
        Map<String, String> storedSchema = row.getMap(
                "parameter_schema", String.class, String.class
        );

        if (storedSchema == null) {
            return Optional.of(Map.of());
        }

        Map<String, ParameterType> parameterSchema = new HashMap<>();

        storedSchema.forEach((parameterName, parameterType) ->
                parameterSchema.put(
                        parameterName,
                        ParameterType.from(parameterType)
                )
        );

        return Optional.of(parameterSchema);
    }
}
