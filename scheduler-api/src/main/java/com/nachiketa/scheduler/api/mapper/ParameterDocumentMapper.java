package com.nachiketa.scheduler.api.mapper;


import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import com.nachiketa.scheduler.model.ParameterType;
import org.springframework.stereotype.Component;
import software.amazon.smithy.java.core.serde.document.Document;

@Component
public class ParameterDocumentMapper {

    public Map<String, String> toStorage(Document document) {
        Map<String, Document> parameters = document.asStringMap();

        Map<String, String> result = new HashMap<>();

        parameters.forEach((name, value) ->
                result.put(name, toStorageValue(value))
        );

        return result;
    }

    public Document fromStorage(
            Map<String, String> storedParameters,
            Map<String, ParameterType> schema
    ) {
        Map<String, Document> parameters = new HashMap<>();
        storedParameters.forEach((name, value) -> {
            ParameterType type = schema.get(name);
            if (type == null) {
                throw new IllegalStateException(
                        "No parameter type found for: " + name
                );
            }

            parameters.put(
                    name,
                    fromStorageValue(value, type)
            );
        });

        return Document.of(parameters);
    }

    private String toStorageValue(Document value) {
        return switch (value.type()) {
            case STRING, ENUM -> value.asString();
            case INTEGER, INT_ENUM -> Integer.toString(value.asInteger());
            case LONG -> Long.toString(value.asLong());
            case FLOAT -> Float.toString(value.asFloat());
            case DOUBLE -> Double.toString(value.asDouble());
            case BOOLEAN -> Boolean.toString(value.asBoolean());
            case TIMESTAMP -> value.asTimestamp().toString();
            default ->
                    throw new IllegalArgumentException(
                            "Unsupported parameter type: "
                                    + value.type()
                    );
        };
    }

    private Document fromStorageValue(
            String value,
            ParameterType type
    ) {
        if (type == ParameterType.STRING) {
            return Document.of(value);
        }

        if (type == ParameterType.INTEGER) {
            return Document.of(Integer.parseInt(value));
        }

        if (type == ParameterType.LONG) {
            return Document.of(Long.parseLong(value));
        }

        if (type == ParameterType.BOOLEAN) {
            return Document.of(Boolean.parseBoolean(value));
        }

        if (type == ParameterType.FLOAT) {
            return Document.of(Float.parseFloat(value));
        }

        if (type == ParameterType.DOUBLE) {
            return Document.of(Double.parseDouble(value));
        }

        if (type == ParameterType.TIMESTAMP) {
            return Document.of(Instant.parse(value));
        }

        throw new IllegalArgumentException(
                "Unsupported parameter type: "
                        + type.getValue()
        );
    }
}
