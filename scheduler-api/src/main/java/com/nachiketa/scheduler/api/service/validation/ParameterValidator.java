package com.nachiketa.scheduler.api.service.validation;

import com.nachiketa.scheduler.api.exception.InvalidRequestException;
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
            throw new InvalidRequestException(
                    "Supplied parameters do not match task parameter schema"
            );
        }
    }

    private void validateType(
            String name,
            Document value,
            ParameterType expectedType) {

        switch (expectedType.getValue()) {
            case "STRING" -> validateConversion(
                    name,
                    expectedType,
                    value::asString
            );

            case "INTEGER" -> validateConversion(
                    name,
                    expectedType,
                    value::asInteger
            );

            case "LONG" -> validateConversion(
                    name,
                    expectedType,
                    value::asLong
            );

            case "BOOLEAN" -> validateConversion(
                    name,
                    expectedType,
                    value::asBoolean
            );

            case "FLOAT" -> validateConversion(
                    name,
                    expectedType,
                    value::asFloat
            );

            case "DOUBLE" -> validateConversion(
                    name,
                    expectedType,
                    value::asDouble
            );

            case "TIMESTAMP" -> validateConversion(
                    name,
                    expectedType,
                    value::asTimestamp
            );

            default -> throw new IllegalArgumentException(
                    "Unsupported parameter type: " + expectedType.getValue()
            );
        }
    }


    private void validateConversion(
            String name,
            ParameterType expectedType,
            Runnable conversion) {

        try {
            conversion.run();
        } catch (RuntimeException exception) {
            throw new InvalidRequestException(
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
