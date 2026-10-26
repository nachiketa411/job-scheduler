package com.nachiketa.scheduler.api.mapper;

import com.nachiketa.scheduler.model.ParameterType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.core.serde.document.Document;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParameterDocumentMapperTest {

    private ParameterDocumentMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ParameterDocumentMapper();
    }

    @Test
    void toStorage_convertsSupportedParameterTypesToStrings() {
        Document parameters =
                createAllSupportedParameterTypes();

        Map<String, String> result =
                mapper.toStorage(parameters);

        assertEquals("hello", result.get("stringValue"));
        assertEquals("42", result.get("integerValue"));
        assertEquals("123456789", result.get("longValue"));
        assertEquals("true", result.get("booleanValue"));
        assertEquals("3.5", result.get("floatValue"));
        assertEquals("9.75", result.get("doubleValue"));
        assertEquals(
                "2026-10-30T15:30:00Z",
                result.get("timestampValue")
        );
    }

    @Test
    void toStorage_whenParametersAreEmpty_returnsEmptyMap() {
        Document parameters =
                Document.of(Map.of());

        Map<String, String> result =
                mapper.toStorage(parameters);

        assertTrue(result.isEmpty());
    }

    @Test
    void fromStorage_convertsStringsUsingParameterSchema() {
        Document result =
                mapper.fromStorage(
                        createStoredParameters(),
                        createParameterSchema()
                );

        Map<String, Document> parameters =
                result.asStringMap();

        assertEquals(
                "hello",
                parameters.get("stringValue").asString()
        );

        assertEquals(
                42,
                parameters.get("integerValue").asInteger()
        );

        assertEquals(
                123456789L,
                parameters.get("longValue").asLong()
        );

        assertTrue(
                parameters.get("booleanValue").asBoolean()
        );

        assertEquals(
                3.5F,
                parameters.get("floatValue").asFloat()
        );

        assertEquals(
                9.75D,
                parameters.get("doubleValue").asDouble()
        );

        assertEquals(
                Instant.parse("2026-10-30T15:30:00Z"),
                parameters.get("timestampValue").asTimestamp()
        );
    }

    @Test
    void fromStorage_whenParametersAreEmpty_returnsEmptyDocument() {
        Document result =
                mapper.fromStorage(
                        Map.of(),
                        Map.of()
                );

        assertTrue(
                result.asStringMap().isEmpty()
        );
    }

    @Test
    void fromStorage_whenParameterIsMissingFromSchema_throwsException() {
        Map<String, String> storedParameters =
                Map.of(
                        "recipient",
                        "user@example.com"
                );

        Map<String, ParameterType> schema =
                Map.of();

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> mapper.fromStorage(
                                storedParameters,
                                schema
                        )
                );

        assertEquals(
                "No parameter type found for: recipient",
                exception.getMessage()
        );
    }

    @Test
    void fromStorage_whenIntegerCannotBeParsed_throwsException() {
        Map<String, String> storedParameters =
                Map.of(
                        "retryCount",
                        "not-an-integer"
                );

        Map<String, ParameterType> schema =
                Map.of(
                        "retryCount",
                        ParameterType.INTEGER
                );

        assertThrows(
                NumberFormatException.class,
                () -> mapper.fromStorage(
                        storedParameters,
                        schema
                )
        );
    }

    @Test
    void fromStorage_whenTimestampCannotBeParsed_throwsException() {
        Map<String, String> storedParameters =
                Map.of(
                        "sendAt",
                        "not-a-timestamp"
                );

        Map<String, ParameterType> schema =
                Map.of(
                        "sendAt",
                        ParameterType.TIMESTAMP
                );

        assertThrows(
                DateTimeParseException.class,
                () -> mapper.fromStorage(
                        storedParameters,
                        schema
                )
        );
    }

    @Test
    void fromStorage_whenParameterTypeIsUnsupported_throwsException() {
        Map<String, String> storedParameters =
                Map.of(
                        "amount",
                        "100.25"
                );

        Map<String, ParameterType> schema =
                Map.of(
                        "amount",
                        ParameterType.unknown("DECIMAL")
                );

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> mapper.fromStorage(
                                storedParameters,
                                schema
                        )
                );

        assertEquals(
                "Unsupported parameter type: DECIMAL",
                exception.getMessage()
        );
    }

    @Test
    void roundTrip_preservesParameterValues() {
        Document original =
                createAllSupportedParameterTypes();

        Map<String, String> stored =
                mapper.toStorage(original);

        Document reconstructed =
                mapper.fromStorage(
                        stored,
                        createParameterSchema()
                );

        assertEquals(
                original.asStringMap(),
                reconstructed.asStringMap()
        );
    }

    private static Document createAllSupportedParameterTypes() {
        return Document.of(
                Map.of(
                        "stringValue", Document.of("hello"),
                        "integerValue", Document.of(42),
                        "longValue", Document.of(123456789L),
                        "booleanValue", Document.of(true),
                        "floatValue", Document.of(3.5F),
                        "doubleValue", Document.of(9.75D),
                        "timestampValue",
                        Document.of(
                                Instant.parse("2026-10-30T15:30:00Z")
                        )
                )
        );
    }

    private static Map<String, String> createStoredParameters() {
        return Map.of(
                "stringValue", "hello",
                "integerValue", "42",
                "longValue", "123456789",
                "booleanValue", "true",
                "floatValue", "3.5",
                "doubleValue", "9.75",
                "timestampValue", "2026-10-30T15:30:00Z"
        );
    }

    private static Map<String, ParameterType> createParameterSchema() {
        return Map.of(
                "stringValue", ParameterType.STRING,
                "integerValue", ParameterType.INTEGER,
                "longValue", ParameterType.LONG,
                "booleanValue", ParameterType.BOOLEAN,
                "floatValue", ParameterType.FLOAT,
                "doubleValue", ParameterType.DOUBLE,
                "timestampValue", ParameterType.TIMESTAMP
        );
    }
}