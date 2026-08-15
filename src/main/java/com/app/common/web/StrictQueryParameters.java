package com.app.common.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a handler whose query parameters are closed: any parameter the handler does not declare is
 * rejected with 400 rather than ignored.
 *
 * <p>Spring MVC resolves arguments by pulling each declared {@code @RequestParam} out of the
 * request and never inspects what is left over, so an undeclared parameter is silently discarded.
 * For a filter parameter that is indistinguishable from a filter that matched every row, which
 * makes a client unable to tell an unsupported filter from an ineffective one.
 *
 * <p>Deliberately opt-in per handler rather than global. Applying it everywhere would reject
 * traffic this repository cannot enumerate, because clients demonstrably send parameters that no
 * handler declares. Annotating the endpoints whose contract depends on the parameter being honoured
 * buys the diagnostic value where it matters and leaves every other endpoint's behaviour untouched.
 *
 * @see StrictQueryParameterInterceptor
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface StrictQueryParameters {}
