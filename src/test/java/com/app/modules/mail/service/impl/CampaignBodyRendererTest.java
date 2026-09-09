package com.app.modules.mail.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;

class CampaignBodyRendererTest {

    private final CampaignBodyRenderer renderer = new CampaignBodyRenderer();

    @Test
    void renderSanitized_ordinaryMarkdown_rendersStructure() {
        String html = renderer.renderSanitized("# Title\n\nSome **bold** text.\n\n- one\n- two\n");

        assertThat(html)
                .contains("<h1>Title</h1>")
                .contains("<strong>bold</strong>")
                .contains("<li>");
    }

    // The three cases the security review names explicitly.
    @Test
    void renderSanitized_scriptTag_isRemoved() {
        String html = renderer.renderSanitized("Hello\n\n<script>alert(1)</script>\n");

        assertThat(html).doesNotContain("<script").doesNotContain("alert(1)");
    }

    @Test
    void renderSanitized_eventHandlerAttribute_isRemoved() {
        String html =
                renderer.renderSanitized("<img src=\"https://a.test/x.png\" onerror=\"alert(1)\">");

        assertThat(html).doesNotContain("onerror").doesNotContain("alert(1)");
    }

    @Test
    void renderSanitized_javascriptUrl_isRemoved() {
        String html = renderer.renderSanitized("[click me](javascript:alert(1))");

        assertThat(html).doesNotContain("javascript:").doesNotContain("alert(1)");
    }

    @Test
    void renderSanitized_dataUrl_isRemoved() {
        String html = renderer.renderSanitized("[x](data:text/html;base64,PHNjcmlwdD4=)");

        assertThat(html).doesNotContain("data:");
    }

    @Test
    void renderSanitized_httpAndMailtoLinks_survive() {
        String html =
                renderer.renderSanitized(
                        "[site](https://example.test) and [mail](mailto:a@b.test)");

        // The sanitizer HTML-encodes the @ as &#64;, which decodes back to @ in any client. The
        // link is intact; only its source representation is entity-encoded.
        assertThat(html).contains("https://example.test").contains("mailto:a&#64;b.test");
    }

    @Test
    void renderSanitized_iframeAndForm_areRemoved() {
        String html =
                renderer.renderSanitized(
                        "<iframe src=\"https://evil.test\"></iframe><form action=\"/x\">"
                                + "<input name=\"password\"></form>");

        assertThat(html).doesNotContain("<iframe").doesNotContain("<form").doesNotContain("<input");
    }

    @Test
    void renderSanitized_styleAttribute_isRemoved() {
        String html = renderer.renderSanitized("<p style=\"position:fixed;top:0\">x</p>");

        assertThat(html).doesNotContain("style=");
    }

    @Test
    void validateVariables_permittedTokens_areAccepted() {
        assertThatCode(() -> renderer.validateVariables("Hi {{username}} and {{fullName}}"))
                .doesNotThrowAnyException();
    }

    // Raised at save time, and the message names the offending token so the author can fix it.
    @Test
    void validateVariables_unknownToken_isRejectedAndNamed() {
        AppException thrown =
                catchThrowableOfType(
                        () -> renderer.validateVariables("Hi {{email}}"), AppException.class);

        assertThat(thrown.getErrorCode()).isEqualTo(ApiErrorCode.CAMPAIGN_UNKNOWN_VARIABLE);
        assertThat(thrown.getMessage()).contains("email");
    }

    @Test
    void validateVariables_toleratesSurroundingWhitespace() {
        assertThatCode(() -> renderer.validateVariables("Hi {{ username }}"))
                .doesNotThrowAnyException();
    }

    @Test
    void renderForRecipient_substitutesBothTokens() {
        String html =
                renderer.renderForRecipient(
                        "Hi {{username}}, also known as {{fullName}}.",
                        Map.of("username", "ada", "fullName", "Ada Lovelace"));

        assertThat(html).contains("ada").contains("Ada Lovelace").doesNotContain("{{");
    }

    // Substitution runs after sanitization and escapes its values, so a display name containing
    // markup is rendered as text and can never become an element.
    @Test
    void renderForRecipient_markupInAValue_isEscapedNotExecuted() {
        String html =
                renderer.renderForRecipient(
                        "Hi {{fullName}}.",
                        Map.of("username", "x", "fullName", "<script>alert(1)</script>"));

        assertThat(html).doesNotContain("<script>");
        assertThat(html).contains("&lt;script&gt;");
    }

    @Test
    void renderForRecipient_missingValue_substitutesEmpty() {
        String html = renderer.renderForRecipient("Hi {{username}}.", Map.of());

        assertThat(html).doesNotContain("{{username}}");
    }

    @Test
    void renderSanitized_blankBody_returnsEmpty() {
        assertThat(renderer.renderSanitized(null)).isEmpty();
        assertThat(renderer.renderSanitized("   ")).isEmpty();
    }
}
