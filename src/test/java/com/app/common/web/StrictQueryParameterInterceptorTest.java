package com.app.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.method.HandlerMethod;

import com.app.common.exception.AppException;

class StrictQueryParameterInterceptorTest {

    private static final String HANDLER_METHOD_NAME = "handle";

    private StrictQueryParameterInterceptor interceptor;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        interceptor = new StrictQueryParameterInterceptor();
        response = new MockHttpServletResponse();
    }

    /** Declares its query parameters on the interface only, as several controllers here do. */
    interface ParamsOnInterface {
        String handle(@RequestParam("cursor") String cursor, @RequestParam("limit") int limit);
    }

    static class InterfaceDeclaringHandler implements ParamsOnInterface {
        @Override
        @StrictQueryParameters
        public String handle(String cursor, int limit) {
            return "ok";
        }
    }

    static class ImplDeclaringHandler {
        @StrictQueryParameters
        public String handle(@RequestParam("cursor") String cursor) {
            return "ok";
        }
    }

    static class LenientHandler {
        public String handle(@RequestParam("cursor") String cursor) {
            return "ok";
        }
    }

    /** Declares parameters without naming them, which is how most admin handlers are written. */
    static class UnnamedParamsHandler {
        @StrictQueryParameters
        public String handle(
                @RequestParam(required = false) String status,
                @RequestParam(required = false) String cursor) {
            return "ok";
        }
    }

    static class WildcardHandler {
        @StrictQueryParameters
        public String handle(@RequestParam Map<String, String> all) {
            return "ok";
        }
    }

    @Test
    void parametersDeclaredOnlyOnTheInterface_areAccepted() throws Exception {
        // The controller override carries no @RequestParam at all; reading only the implementation
        // would find an empty declared set and reject every request.
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/x");
        request.setParameter("cursor", "abc");
        request.setParameter("limit", "20");

        assertThat(
                        interceptor.preHandle(
                                request, response, handlerFor(new InterfaceDeclaringHandler())))
                .isTrue();
    }

    @Test
    void undeclaredParameter_onAnInterfaceDeclaringHandler_isRejected() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/x");
        request.setParameter("cursor", "abc");
        request.setParameter("mediaType", "IMAGE");

        assertThatThrownBy(
                        () ->
                                interceptor.preHandle(
                                        request,
                                        response,
                                        handlerFor(new InterfaceDeclaringHandler())))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("mediaType")
                .hasMessageContaining("cursor")
                .hasMessageContaining("limit");
    }

    @Test
    void parametersDeclaredOnTheImplementation_areAccepted() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/x");
        request.setParameter("cursor", "abc");

        assertThat(interceptor.preHandle(request, response, handlerFor(new ImplDeclaringHandler())))
                .isTrue();
    }

    @Test
    void undeclaredParameter_onAnImplementationDeclaringHandler_isRejected() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/x");
        request.setParameter("hasMedia", "true");

        assertThatThrownBy(
                        () ->
                                interceptor.preHandle(
                                        request, response, handlerFor(new ImplDeclaringHandler())))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("hasMedia");
    }

    @Test
    void handlerWithoutTheAnnotation_ignoresUndeclaredParameters() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/x");
        request.setParameter("anything", "goes");

        assertThat(interceptor.preHandle(request, response, handlerFor(new LenientHandler())))
                .isTrue();
    }

    @Test
    void mapValuedRequestParam_acceptsEverything() throws Exception {
        // A Map-typed @RequestParam is an open contract by construction, so closing it would be
        // wrong.
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/x");
        request.setParameter("whatever", "1");

        assertThat(interceptor.preHandle(request, response, handlerFor(new WildcardHandler())))
                .isTrue();
    }

    @Test
    void requestParamsThatDoNotNameThemselves_areStillDeclared() throws Exception {
        // An @RequestParam with no explicit value falls back to the compiled parameter name. A
        // handler whose parameters are all written that way must accept them, not reject every
        // request as carrying something undeclared.
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/x");
        request.setParameter("status", "banned");
        request.setParameter("cursor", "abc");

        assertThat(interceptor.preHandle(request, response, handlerFor(new UnnamedParamsHandler())))
                .isTrue();
    }

    @Test
    void undeclaredParameter_onAnUnnamedParamsHandler_isRejected() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/x");
        request.setParameter("staus", "banned");

        assertThatThrownBy(
                        () ->
                                interceptor.preHandle(
                                        request, response, handlerFor(new UnnamedParamsHandler())))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("staus")
                .hasMessageContaining("status");
    }

    @Test
    void nonHandlerMethodTargets_arePassedThrough() throws Exception {
        // springdoc and the actuator are not HandlerMethods, so the interceptor must not touch
        // them.
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api-docs");
        request.setParameter("group", "default");

        assertThatCode(() -> interceptor.preHandle(request, response, new Object()))
                .doesNotThrowAnyException();
        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
    }

    @Test
    void noParametersAtAll_isAccepted() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/x");

        assertThat(interceptor.preHandle(request, response, handlerFor(new ImplDeclaringHandler())))
                .isTrue();
    }

    /**
     * Resolves the handler method by name, skipping compiler-generated members.
     *
     * <p>{@code getDeclaredMethods()} is documented as returning methods in no particular order, so
     * indexing into it is not reproducible. A class implementing an interface method also carries a
     * synthetic bridge alongside the real declaration, and a bridge does not expose the
     * {@code @RequestParam} annotations. Picking the bridge made the interceptor see an unannotated
     * handler, which it passes through by design, so the tests asserting a rejection failed
     * intermittently depending on the order the JVM happened to report.
     */
    private static HandlerMethod handlerFor(Object controller) {
        Method target =
                Arrays.stream(controller.getClass().getDeclaredMethods())
                        .filter(method -> HANDLER_METHOD_NAME.equals(method.getName()))
                        .filter(method -> !method.isBridge() && !method.isSynthetic())
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "No declared handler method named "
                                                        + HANDLER_METHOD_NAME
                                                        + " on "
                                                        + controller.getClass()));
        return new HandlerMethod(controller, target);
    }
}
