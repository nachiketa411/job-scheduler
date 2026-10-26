package com.nachiketa.scheduler.api.service.validation;

import com.nachiketa.scheduler.model.ParameterType;
import org.springframework.stereotype.Component;
import software.amazon.smithy.java.core.serde.document.Document;

import java.util.Map;

@Component
public class ParameterValidator {

    public void validate(
            Map<String, ParameterType> schema,
            Document parameters
    ) {
        Map<String, Document> supplied =
                parameters.asStringMap();

        validateParameterNames(
                schema,
                supplied
        );

        supplied.forEach((name, value) ->
                validateType(
                        name,
                        value,
                        schema.get(name)
                )
        );
    }

    private void validateParameterNames(
            Map<String, ParameterType> schema,
            Map<String, Document> supplied) {

        if (!supplied.keySet().equals(schema.keySet())) {
            throw new IllegalArgumentException(
                    "Supplied parameters do not match task parameter schema"
            );
        }
    }

    private void validateType(
            String name,
            Document value,
            ParameterType expectedType) {

        try {
            if (expectedType == ParameterType.STRING) {
                value.asString();
                return;
            }

            if (expectedType == ParameterType.INTEGER) {
                value.asInteger();
                return;
            }

            if (expectedType == ParameterType.LONG) {
                value.asLong();
                return;
            }

            if (expectedType == ParameterType.BOOLEAN) {
                value.asBoolean();
                return;
            }

            if (expectedType == ParameterType.FLOAT) {
                value.asFloat();
                return;
            }

            if (expectedType == ParameterType.DOUBLE) {
                value.asDouble();
                return;
            }

            if (expectedType == ParameterType.TIMESTAMP) {
                value.asTimestamp();
                return;
            }

            throw new IllegalArgumentException(
                    "Unsupported parameter type: "
                            + expectedType.getValue()
            );

        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(
                    "Parameter '%s' must be of type %s"
                            .formatted(
                                    name,
                                    expectedType.getValue()
                            ),
                    exception
            );
        }
    }
}
