package com.app.common.config.openapi;

import java.util.List;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.stereotype.Component;

import com.app.common.response.error.ValidationErrorResponse;

import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;

/**
 * Registers the typed failure envelopes that no single operation can name.
 *
 * <p>{@code ApiResponse.data} is declared as an empty schema, so every detail a failure carries is
 * invisible to a generated client. Where one error code is the whole of an operation's declared
 * outcome, that operation's {@code @ApiResponse} names the typed envelope directly and no
 * registration is needed. {@code VALIDATION_ERROR} is the case that cannot be handled that way: a
 * 400 on most operations also covers a malformed cursor, an out-of-range page size and an
 * unsupported query parameter, none of which carry a payload, so declaring the validation envelope
 * as that operation's 400 would promise a payload that usually does not arrive.
 *
 * <p>springdoc emits only schemas something references, so the type is registered here rather than
 * left to be dropped. A client that switches on {@code code} then has a real type to parse the
 * detail into.
 */
@Component
public class ErrorDetailSchemaCustomizer implements OpenApiCustomizer {

    private static final List<Class<?>> UNREFERENCED_ERROR_ENVELOPES =
            List.of(ValidationErrorResponse.class);

    @Override
    public void customise(OpenAPI openApi) {
        if (openApi.getComponents() == null) {
            openApi.setComponents(new Components());
        }
        for (Class<?> envelope : UNREFERENCED_ERROR_ENVELOPES) {
            ModelConverters.getInstance()
                    .readAll(new AnnotatedType(envelope))
                    .forEach(openApi.getComponents()::addSchemas);
        }
    }
}
