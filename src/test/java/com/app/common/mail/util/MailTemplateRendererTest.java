package com.app.common.mail.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import com.app.modules.mail.enums.MailTemplate;
import com.app.modules.mail.util.MailTemplateRenderer;

@ExtendWith(MockitoExtension.class)
class MailTemplateRendererTest {

    @Mock private TemplateEngine templateEngine;

    private MailTemplateRenderer renderer;

    @BeforeEach
    void setUp() {
        renderer = new MailTemplateRenderer(templateEngine);
    }

    @Test
    void render_passesTemplatePathAndVariablesToEngine() {
        when(templateEngine.process(any(String.class), any(Context.class)))
                .thenReturn("<html>rendered</html>");
        Map<String, Object> variables = Map.of("toName", "Alice", "appName", "Social");

        String result = renderer.render(MailTemplate.WELCOME, variables);

        ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);
        verify(templateEngine).process(eq("mail/welcome"), contextCaptor.capture());
        Context captured = contextCaptor.getValue();
        assertThat(captured.getVariable("toName")).isEqualTo("Alice");
        assertThat(captured.getVariable("appName")).isEqualTo("Social");
        assertThat(result).isEqualTo("<html>rendered</html>");
    }

    @Test
    void render_usesEnglishLocale() {
        when(templateEngine.process(any(String.class), any(Context.class))).thenReturn("");
        ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);

        renderer.render(MailTemplate.EMAIL_VERIFICATION, Map.of());

        verify(templateEngine).process(any(String.class), contextCaptor.capture());
        assertThat(contextCaptor.getValue().getLocale().getLanguage()).isEqualTo("en");
    }
}
