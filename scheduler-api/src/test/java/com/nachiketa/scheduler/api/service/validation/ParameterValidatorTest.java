package com.nachiketa.scheduler.api.service.validation;

import com.nachiketa.scheduler.model.ParameterType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.core.serde.document.Document;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ParameterValidatorTest {

    private ParameterValidator parameterValidator;

    @BeforeEach
    void setUp() {
        parameterValidator = new ParameterValidator();
    }

    /**
     * Verifies that validation succeeds when the supplied parameter names
     * exactly match the task schema and every value has the expected type.
     */
    @Test
    void validate_whenParametersMatchSchema_doesNotThrow() {
        Document parameters = mock(Document.class);

        Document stringValue = mock(Document.class);
        Document integerValue = mock(Document.class);
        Document longValue = mock(Document.class);
        Document booleanValue = mock(Document.class);
        Document floatValue = mock(Document.class);
        Document doubleValue = mock(Document.class);
        Document timestampValue = mock(Document.class);

        Map<String, ParameterType> schema = Map.of(
                "stringParam", ParameterType.STRING,
                "integerParam", ParameterType.INTEGER,
                "longParam", ParameterType.LONG,
                "booleanParam", ParameterType.BOOLEAN,
                "floatParam", ParameterType.FLOAT,
                "doubleParam", ParameterType.DOUBLE,
                "timestampParam", ParameterType.TIMESTAMP
        );

        Map<String, Document> supplied = Map.of(
                "stringParam", stringValue,
                "integerParam", integerValue,
                "longParam", longValue,
                "booleanParam", booleanValue,
                "floatParam", floatValue,
                "doubleParam", doubleValue,
                "timestampParam", timestampValue
        );

        when(parameters.asStringMap())
                .thenReturn(supplied);

        when(stringValue.asString())
                .thenReturn("hello");

        when(integerValue.asInteger())
                .thenReturn(10);

        when(longValue.asLong())
                .thenReturn(100L);

        when(booleanValue.asBoolean())
                .thenReturn(true);

        when(floatValue.asFloat())
                .thenReturn(1.5F);

        when(doubleValue.asDouble())
                .thenReturn(2.5);

        when(timestampValue.asTimestamp())
                .thenReturn(Instant.parse("2026-10-30T15:30:00Z"));

        assertDoesNotThrow(
                () -> parameterValidator.validate(
                        schema,
                        parameters
                )
        );

        verify(stringValue).asString();
        verify(integerValue).asInteger();
        verify(longValue).asLong();
        verify(booleanValue).asBoolean();
        verify(floatValue).asFloat();
        verify(doubleValue).asDouble();
        verify(timestampValue).asTimestamp();
    }

    /**
     * Verifies that validation fails when a required parameter
     * from the task schema is missing.
     */
    @Test
    void validate_whenRequiredParameterIsMissing_throwsIllegalArgumentException() {
        Document parameters = mock(Document.class);
        Document recipient = mock(Document.class);

        Map<String, ParameterType> schema = Map.of(
                "recipient", ParameterType.STRING,
                "retryCount", ParameterType.INTEGER
        );

        Map<String, Document> supplied = Map.of(
                "recipient", recipient
        );

        when(parameters.asStringMap())
                .thenReturn(supplied);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> parameterValidator.validate(
                        schema,
                        parameters
                )
        );

        assertEquals(
                "Supplied parameters do not match task parameter schema",
                exception.getMessage()
        );
    }

    /**
     * Verifies that validation fails when the caller supplies
     * a parameter that is not defined by the task schema.
     */
    @Test
    void validate_whenUnexpectedParameterIsSupplied_throwsIllegalArgumentException() {
        Document parameters = mock(Document.class);
        Document recipient = mock(Document.class);
        Document unexpected = mock(Document.class);

        Map<String, ParameterType> schema = Map.of(
                "recipient", ParameterType.STRING
        );

        Map<String, Document> supplied = Map.of(
                "recipient", recipient,
                "unexpected", unexpected
        );

        when(parameters.asStringMap())
                .thenReturn(supplied);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> parameterValidator.validate(
                        schema,
                        parameters
                )
        );

        assertEquals(
                "Supplied parameters do not match task parameter schema",
                exception.getMessage()
        );
    }

    /**
     * Verifies that validation fails when the supplied parameter names
     * contain both missing and unexpected values relative to the schema.
     */
    @Test
    void validate_whenParameterNamesDifferFromSchema_throwsIllegalArgumentException() {
        Document parameters = mock(Document.class);
        Document body = mock(Document.class);

        Map<String, ParameterType> schema = Map.of(
                "recipient", ParameterType.STRING
        );

        Map<String, Document> supplied = Map.of(
                "body", body
        );

        when(parameters.asStringMap())
                .thenReturn(supplied);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> parameterValidator.validate(
                        schema,
                        parameters
                )
        );

        assertEquals(
                "Supplied parameters do not match task parameter schema",
                exception.getMessage()
        );
    }

    /**
     * Verifies that a parameter whose value cannot be converted to
     * the expected type is rejected with a descriptive error.
     */
    @Test
    void validate_whenParameterHasWrongType_throwsIllegalArgumentException() {
        Document parameters = mock(Document.class);
        Document retryCount = mock(Document.class);

        Map<String, ParameterType> schema = Map.of(
                "retryCount", ParameterType.INTEGER
        );

        Map<String, Document> supplied = Map.of(
                "retryCount", retryCount
        );

        when(parameters.asStringMap())
                .thenReturn(supplied);

        RuntimeException conversionException =
                new RuntimeException("Not an integer");

        when(retryCount.asInteger())
                .thenThrow(conversionException);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> parameterValidator.validate(
                        schema,
                        parameters
                )
        );

        assertEquals(
                "Parameter 'retryCount' must be of type INTEGER",
                exception.getMessage()
        );

        assertEquals(
                conversionException,
                exception.getCause()
        );
    }

    /**
     * Verifies that an unknown parameter type is reported as unsupported
     * rather than being incorrectly reported as a value conversion failure.
     */
    @Test
    void validate_whenParameterTypeIsUnsupported_throwsUnsupportedTypeError() {
        Document parameters = mock(Document.class);
        Document value = mock(Document.class);

        ParameterType unsupportedType =
                ParameterType.unknown("DECIMAL");

        Map<String, ParameterType> schema = Map.of(
                "amount",
                unsupportedType
        );

        when(parameters.asStringMap())
                .thenReturn(
                        Map.of(
                                "amount",
                                value
                        )
                );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> parameterValidator.validate(
                        schema,
                        parameters
                )
        );

        assertEquals(
                "Unsupported parameter type: DECIMAL",
                exception.getMessage()
        );
    }

    /**
     * Verifies that an empty parameter schema accepts an empty
     * parameter document.
     */
    @Test
    void validate_whenSchemaAndParametersAreEmpty_doesNotThrow() {
        Document parameters = mock(Document.class);

        when(parameters.asStringMap())
                .thenReturn(Map.of());

        assertDoesNotThrow(
                () -> parameterValidator.validate(
                        Map.of(),
                        parameters
                )
        );
    }
}
