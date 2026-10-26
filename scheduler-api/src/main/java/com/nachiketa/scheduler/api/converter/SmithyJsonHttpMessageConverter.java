package com.nachiketa.scheduler.api.converter;

import com.nachiketa.scheduler.api.exception.SmithyConverterException;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.json.JsonCodec;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class SmithyJsonHttpMessageConverter
        extends AbstractHttpMessageConverter<SerializableStruct> {

    private final JsonCodec jsonCodec;

    // Cache the factory that creates new builders
    private final Map<
                Class<? extends SerializableStruct>,
                Supplier<? extends ShapeBuilder<? extends SerializableStruct>>
                > builderFactories = new ConcurrentHashMap<>();

    public SmithyJsonHttpMessageConverter() {
        super(MediaType.APPLICATION_JSON);
        this.jsonCodec = JsonCodec.builder().build();
    }

    @Override
    protected boolean supports(Class<?> clazz) {
        return SerializableStruct.class.isAssignableFrom(clazz);
    }

    @Override
    protected SerializableStruct readInternal(
            Class<? extends SerializableStruct> clazz,
            HttpInputMessage inputMessage
    ) throws IOException, HttpMessageNotReadableException {

        byte[] body = inputMessage.getBody().readAllBytes();

        try {
            ShapeBuilder<? extends SerializableStruct> builder = createBuilder(clazz);
            return deserialize(body, builder);
        } catch (SmithyConverterException e) {
            throw e;

        } catch (RuntimeException e) {
            throw new HttpMessageNotReadableException(
                    "Unable to deserialize Smithy JSON into " + clazz.getName(),
                    e,
                    inputMessage
            );
        }
    }

    @Override
    protected void writeInternal(
            SerializableStruct value,
            HttpOutputMessage outputMessage)
            throws IOException {

        try {
            ByteBuffer json = jsonCodec.serialize(value);
            writeByteBuffer(json, outputMessage);

        } catch (RuntimeException e) {
            throw new SmithyConverterException(
                    "Unable to serialize Smithy object " + value.getClass().getName(),
                    e
            );
        }
    }


    private ShapeBuilder<? extends SerializableStruct> createBuilder(
            Class<? extends SerializableStruct> clazz
    ) {
        return builderFactories
                .computeIfAbsent(clazz, this::createBuilderFactory)
                .get();
    }

    @SuppressWarnings("unchecked")
    private Supplier<? extends ShapeBuilder<? extends SerializableStruct>>
    createBuilderFactory(Class<? extends SerializableStruct> clazz) {

        try {
            Method builderMethod = clazz.getMethod("builder");

            return () -> {
                try {
                    return (ShapeBuilder<? extends SerializableStruct>)
                            builderMethod.invoke(null);
                } catch (ReflectiveOperationException e) {
                    throw new SmithyConverterException(
                            "Unable to create Smithy builder for "
                                    + clazz.getName(),
                            e
                    );
                }
            };

        } catch (NoSuchMethodException e) {
            throw new SmithyConverterException(
                    "Smithy type does not expose a static builder(): "
                            + clazz.getName(),
                    e
            );
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private SerializableStruct deserialize(
            byte[] body,
            ShapeBuilder<? extends SerializableStruct> builder) {

        return (SerializableStruct) jsonCodec.deserializeShape(
                body,
                (ShapeBuilder) builder
        );
    }

    private void writeByteBuffer(
            ByteBuffer buffer,
            HttpOutputMessage outputMessage)
            throws IOException {

        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);

        outputMessage.getBody().write(bytes);
    }
}
