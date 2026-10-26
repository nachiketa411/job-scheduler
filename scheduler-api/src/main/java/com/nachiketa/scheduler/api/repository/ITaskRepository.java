package com.nachiketa.scheduler.api.repository;

import com.nachiketa.scheduler.model.ParameterType;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface ITaskRepository {

    Optional<Map<String, ParameterType>> findParameterSchema(UUID taskId);
}
