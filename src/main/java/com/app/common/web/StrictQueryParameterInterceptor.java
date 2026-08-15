package com.app.common.web;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.MethodParameter;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;

/**
 * Rejects query parameters a handler does not declare, for handlers annotated {@link
 * StrictQueryParameters}.
 *
 * <p>Implemented as a {@link HandlerInterceptor} rather than a servlet filter, and that choice is
 * load-bearing. A filter runs before handler mapping, so it cannot know which parameters are legal
 * for the matched route, and it also sees requests that never reach an annotated controller at all:
 * the SockJS transports append their own cache-busting parameter, and the WebSocket handshake
 * carries the access token as a query parameter because a browser cannot set an authorization
 * header on an upgrade. An interceptor receives the resolved {@link HandlerMethod}, so it only ever
 * inspects requests that mapped to an opted-in handler, and springdoc and the actuator are
 * untouched because they are not annotated controllers.
 */
public class StrictQueryParameterInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        if (!isStrict(handlerMethod)) {
            return true;
        }
        Set<String> declared = declaredParameterNames(handlerMethod);
        // A Map-typed @RequestParam accepts every parameter by design, so strictness cannot apply.
        if (declared.contains(WILDCARD)) {
            return true;
        }
        Set<String> undeclared = new TreeSet<>();
        for (String supplied : request.getParameterMap().keySet()) {
            if (!declared.contains(supplied)) {
                undeclared.add(supplied);
            }
        }
        if (!undeclared.isEmpty()) {
            throw new AppException(
                    ApiErrorCode.BAD_REQUEST,
                    "Unsupported query "
                            + (undeclared.size() == 1 ? "parameter" : "parameters")
                            + ": "
                            + String.join(", ", undeclared)
                            + ". Accepted: "
                            + String.join(", ", new TreeSet<>(declared)));
        }
        return true;
    }

    private static final String WILDCARD = "*";

    private boolean isStrict(HandlerMethod handlerMethod) {
        if (handlerMethod.getMethodAnnotation(StrictQueryParameters.class) != null) {
            return true;
        }
        // Also honour the annotation when it sits on the API interface rather than the controller.
        for (Method interfaceMethod : interfaceMethods(handlerMethod)) {
            if (interfaceMethod.isAnnotationPresent(StrictQueryParameters.class)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Collects every declared query parameter name for the handler.
     *
     * <p>Reads both declaration sites. This codebase declares {@code @RequestParam} on the
     * controller implementation for some handlers and only on the {@code *Api} interface for
     * others, and at least one controller declares no mappings of its own at all, so reconstructing
     * the set from either site alone would reject legitimate traffic.
     */
    private Set<String> declaredParameterNames(HandlerMethod handlerMethod) {
        Set<String> names = new TreeSet<>();
        for (MethodParameter parameter : handlerMethod.getMethodParameters()) {
            collect(
                    parameter.getParameter().getType(),
                    parameter.getParameterAnnotation(RequestParam.class),
                    parameter.getParameterName(),
                    names);
        }
        for (Method interfaceMethod : interfaceMethods(handlerMethod)) {
            java.lang.reflect.Parameter[] parameters = interfaceMethod.getParameters();
            for (java.lang.reflect.Parameter parameter : parameters) {
                collect(
                        parameter.getType(),
                        parameter.getAnnotation(RequestParam.class),
                        parameter.getName(),
                        names);
            }
        }
        return names;
    }

    private void collect(
            Class<?> type, RequestParam annotation, String parameterName, Set<String> names) {
        if (annotation == null) {
            return;
        }
        if (Map.class.isAssignableFrom(type)) {
            names.add(WILDCARD);
            return;
        }
        String declared =
                StringUtils.hasText(annotation.value()) ? annotation.value() : annotation.name();
        names.add(StringUtils.hasText(declared) ? declared : parameterName);
    }

    private Set<Method> interfaceMethods(HandlerMethod handlerMethod) {
        Set<Method> found = new java.util.LinkedHashSet<>();
        Method method = handlerMethod.getMethod();
        for (Class<?> candidate :
                ClassUtils.getAllInterfacesForClass(handlerMethod.getBeanType())) {
            try {
                found.add(candidate.getMethod(method.getName(), method.getParameterTypes()));
            } catch (NoSuchMethodException ignored) {
                // The interface does not declare this handler; nothing to read from it.
            }
        }
        return found;
    }
}
