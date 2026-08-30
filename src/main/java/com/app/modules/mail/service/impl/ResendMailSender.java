package com.app.modules.mail.service.impl;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;
import com.app.modules.mail.config.MailProperties;
import com.app.modules.mail.config.resend.ResendProperties;
import com.app.modules.mail.util.MailTemplateRenderer;
import com.resend.Resend;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;

/**
 * Synchronous Resend-backed mail sender.
 *
 * <p>RabbitMQ consumers use this component so provider failures are observed before acknowledging a
 * message. Raw tokens must only appear inside the final recipient URL and must never be logged.
 */
@Component
@ConditionalOnProperty(prefix = "app.mail", name = "transport", havingValue = "resend")
public class ResendMailSender extends AbstractTemplateMailSender {

    private static final Logger log = LoggerFactory.getLogger(ResendMailSender.class);

    private final Resend resend;
    private final ResendProperties resendProperties;

    // One virtual thread per call. The pool exists only to make the SDK call cancellable from the
    // outside so the configured call timeout can be enforced; it is not a concurrency bound and
    // must not become one, because the consumer already bounds how many sends run at once.
    private final ExecutorService callExecutor =
            Executors.newThreadPerTaskExecutor(
                    Thread.ofVirtual().name("resend-call-", 0).factory());

    public ResendMailSender(
            Resend resend,
            ResendProperties resendProperties,
            MailProperties mailProperties,
            MailTemplateRenderer mailTemplateRenderer) {
        super(mailProperties, mailTemplateRenderer);
        this.resend = resend;
        this.resendProperties = resendProperties;
    }

    @PreDestroy
    void shutdown() {
        callExecutor.shutdownNow();
    }

    @Override
    protected String deliver(String toEmail, String subject, String htmlBody) {
        CreateEmailOptions options =
                CreateEmailOptions.builder()
                        .from(fromHeader())
                        .to(toEmail)
                        .subject(subject)
                        .html(htmlBody)
                        .build();
        Callable<CreateEmailResponse> call = () -> resend.emails().send(options);
        Future<CreateEmailResponse> pending = callExecutor.submit(call);
        try {
            CreateEmailResponse response =
                    pending.get(
                            resendProperties.getCallTimeout().toMillis(), TimeUnit.MILLISECONDS);
            String providerMessageId = response == null ? null : response.getId();
            log.info(
                    "Email sent | subject: {} | providerMessageId: {}", subject, providerMessageId);
            return providerMessageId;
        } catch (TimeoutException e) {
            // Cancels the waiting task, not the socket: the SDK owns the connection and exposes no
            // way to abort it. The call is abandoned and the thread is released when OkHttp's own
            // read timeout fires underneath.
            pending.cancel(true);
            log.error(
                    "Timed out sending email after {} | subject: {}",
                    resendProperties.getCallTimeout(),
                    subject);
            throw new AppException(ApiErrorCode.SERVICE_UNAVAILABLE);
        } catch (InterruptedException e) {
            pending.cancel(true);
            Thread.currentThread().interrupt();
            throw new AppException(ApiErrorCode.SERVICE_UNAVAILABLE);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            log.error(
                    "Failed to send email | subject: {} | error: {}", subject, cause.getMessage());
            throw new AppException(ApiErrorCode.SERVICE_UNAVAILABLE);
        }
    }
}
