package com.app.common.config;

import java.util.concurrent.Executor;

import com.app.common.config.app.AppProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.resend.Resend;

/**
 * Wiring for the outbound mail subsystem.
 *
 * <p>Provides the {@link Resend} client (API key sourced exclusively from the {@code
 * RESEND_API_KEY} environment variable) and a dedicated {@link Executor} used by {@code @Async}
 * mail dispatch. The executor isolates blocking SMTP/HTTP I/O from the request-serving virtual
 * thread carrier pool.
 */
@Configuration
@EnableAsync
@EnableConfigurationProperties(AppProperties.class)
public class MailConfig {

    @Bean
    public Resend resend(@Value("${RESEND_API_KEY}") String apiKey) {
        return new Resend(apiKey);
    }

    @Bean(name = "mailTaskExecutor")
    public Executor mailTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("mail-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
