package com.app.common.config.openapi;

import java.lang.reflect.Method;
import java.util.List;

import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;

import io.swagger.v3.oas.models.Operation;

/**
 * Rewrites the marker produced by {@code @Operation(security = {@SecurityRequirement(name = "")})}
 * into a genuinely empty security list.
 *
 * <p>A bare {@code security = {}} on the annotation is indistinguishable from the attribute's unset
 * default under Java's annotation model - both resolve to a zero-length array - so swagger-core's
 * own annotation processing already collapses "explicitly public" and "not specified" into the same
 * {@code null} on {@link Operation#getSecurity()} before any customizer runs; by the time this
 * class would see the model object, the distinction is already gone. A marker of length one, naming
 * no scheme, survives that collapse because its length is non-zero - so this customizer reads the
 * original {@code @Operation} annotation directly via reflection (searching the {@code *Api}
 * interface method the controller implements, since the annotation lives there) rather than
 * trusting the pre-processed model, and forces {@link List#of()} onto the model when it finds the
 * marker.
 */
@Component
public class AnonymousOperationSecurityCustomizer implements OperationCustomizer {

    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        Method method = handlerMethod.getMethod();
        io.swagger.v3.oas.annotations.Operation annotation =
                AnnotationUtils.findAnnotation(
                        method, io.swagger.v3.oas.annotations.Operation.class);
        if (annotation != null && isExplicitPublicMarker(annotation.security())) {
            operation.setSecurity(List.of());
        }
        return operation;
    }

    private static boolean isExplicitPublicMarker(
            io.swagger.v3.oas.annotations.security.SecurityRequirement[] security) {
        return security.length == 1 && security[0].name().isEmpty();
    }
}
