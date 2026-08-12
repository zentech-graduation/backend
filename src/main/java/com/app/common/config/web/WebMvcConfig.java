package com.app.common.config.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.app.common.web.StrictQueryParameterInterceptor;

/**
 * Spring MVC customizations that apply across modules.
 *
 * <p>The strict query parameter interceptor is registered for all paths on purpose: it is inert
 * unless the resolved handler carries {@code @StrictQueryParameters}, so the path pattern is not
 * what scopes it. Narrowing the registration to specific paths would duplicate that scoping in a
 * second place and let the two drift apart.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new StrictQueryParameterInterceptor());
    }
}
