package com.app.modules.mail.service.impl;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;

/**
 * The single implementation that turns a campaign's Markdown into the HTML that is sent.
 *
 * <p>Both the preview endpoint and the send path call this and nothing else. One implementation
 * means a preview cannot diverge from the mail that actually goes out, and it means there is
 * exactly one place where untrusted authored content becomes HTML, rather than a second
 * sanitization surface in the browser.
 *
 * <p>The order matters and is deliberate:
 *
 * <ol>
 *   <li>CommonMark renders the Markdown to HTML.
 *   <li>The OWASP sanitizer filters that HTML against the allowlist below.
 *   <li>Personalisation values are substituted last, HTML-escaped, into the sanitized output.
 * </ol>
 *
 * <p>Substituting before sanitizing would be a hole: a display name containing markup would be
 * treated as authored content and pass through the allowlist. Substituting after, escaped, means a
 * user called {@code <script>} is rendered as that text and can never become an element.
 */
@Component
public class CampaignBodyRenderer {

    /**
     * The two permitted personalisation tokens.
     *
     * <p>Validated at campaign save time rather than at send time, so a campaign that saved can
     * never fail later for an unknown variable with the mail half-sent.
     */
    public static final List<String> ALLOWED_VARIABLES = List.of("username", "fullName");

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{\\s*([^}]*?)\\s*}}");

    // What a campaign body may contain. Prose, structure, emphasis, lists, links, images, quotes
    // and
    // code - everything a Markdown author would reasonably write.
    //
    // What it may not, and why each is absent rather than merely unlisted:
    //   script, style, iframe, object, embed, form, input  - active content and credential capture
    //   on* attributes                                     - never allowed by this builder at all
    //   class, id, style attributes                        - no hook for injected CSS
    //   any URL scheme but http, https and mailto          - blocks javascript: and data: payloads
    //
    // allowUrlProtocols is the load-bearing line: without it a sanitized <a> could still carry a
    // javascript: href, which is the single most likely way a mail body becomes an attack.
    private static final PolicyFactory POLICY =
            new HtmlPolicyBuilder()
                    .allowElements(
                            "h1",
                            "h2",
                            "h3",
                            "h4",
                            "h5",
                            "h6",
                            "p",
                            "br",
                            "hr",
                            "strong",
                            "em",
                            "b",
                            "i",
                            "code",
                            "pre",
                            "ul",
                            "ol",
                            "li",
                            "blockquote",
                            "a",
                            "img")
                    .allowAttributes("href")
                    .onElements("a")
                    .allowAttributes("src", "alt", "title")
                    .onElements("img")
                    .allowUrlProtocols("http", "https", "mailto")
                    // Outbound links in mail open in a client, not a tab, but rel is cheap and
                    // correct for the webmail clients that do render in a browser frame.
                    .requireRelNofollowOnLinks()
                    .toFactory();

    private final Parser parser = Parser.builder().build();
    private final HtmlRenderer htmlRenderer = HtmlRenderer.builder().build();

    /**
     * Renders a body to sanitized HTML, without substituting any personalisation.
     *
     * <p>This is what the preview endpoint returns, so the administrator sees the tokens as tokens
     * rather than resolved against an arbitrary recipient.
     *
     * @param markdown the campaign body
     * @return sanitized HTML
     */
    public String renderSanitized(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }
        return POLICY.sanitize(htmlRenderer.render(parser.parse(markdown)));
    }

    /**
     * Renders a body for one recipient, substituting the permitted tokens.
     *
     * @param markdown the campaign body
     * @param values the values to substitute, keyed by token name
     * @return sanitized HTML with escaped values substituted
     */
    public String renderForRecipient(String markdown, Map<String, String> values) {
        // Sentinels, not the braces themselves. The OWASP sanitizer deliberately breaks up a "{{"
        // sequence into "{<!-- -->{" as a defence against client-side template injection, so a
        // token left as {{username}} in the Markdown does not survive sanitization intact and a
        // naive post-sanitize replace silently matches nothing - which would ship every campaign
        // with its placeholders visible to the recipient.
        //
        // Swapping each token for an opaque alphanumeric sentinel before rendering sidesteps that:
        // the sentinel passes through CommonMark and the sanitizer untouched, and is replaced
        // afterwards with an HTML-escaped value. Escaping still happens after sanitization, which
        // is the property that matters - substituting before would let a display name containing
        // markup be treated as authored content and pass the allowlist.
        String seeded = markdown == null ? null : applySentinels(markdown);
        return replaceSentinels(renderSanitized(seeded), values);
    }

    /**
     * Rejects any personalisation token outside the allowlist.
     *
     * <p>Called at save time. The error names the offending token so the author can fix it rather
     * than hunting through the body.
     *
     * @param markdown the campaign body
     * @throws AppException {@code CAMPAIGN_UNKNOWN_VARIABLE} naming the first unknown token
     */
    public void validateVariables(String markdown) {
        if (markdown == null) {
            return;
        }
        Matcher matcher = VARIABLE_PATTERN.matcher(markdown);
        while (matcher.find()) {
            String token = matcher.group(1);
            if (!ALLOWED_VARIABLES.contains(token)) {
                throw new AppException(
                        ApiErrorCode.CAMPAIGN_UNKNOWN_VARIABLE,
                        "Unknown variable {{"
                                + token
                                + "}}. Permitted: {{username}}, {{fullName}}");
            }
        }
    }

    // Reuses VARIABLE_PATTERN rather than rebuilding a regex per variable, so the token syntax is
    // defined in exactly one place and this cannot drift from what validateVariables accepts.
    private static String applySentinels(String markdown) {
        Matcher matcher = VARIABLE_PATTERN.matcher(markdown);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String token = matcher.group(1);
            String replacement =
                    ALLOWED_VARIABLES.contains(token) ? sentinel(token) : matcher.group(0);
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    // Every value is HTML-escaped on the way in, and this runs on already-sanitized output. A
    // recipient whose display name is "<script>" is rendered as that text and can never become an
    // element, which is why escaping stays on this side of the sanitizer.
    private static String replaceSentinels(String html, Map<String, String> values) {
        String result = html;
        for (String variable : ALLOWED_VARIABLES) {
            result =
                    result.replace(
                            sentinel(variable),
                            HtmlUtils.htmlEscape(values.getOrDefault(variable, "")));
        }
        return result;
    }

    // Alphanumeric on purpose: no punctuation for CommonMark to escape and nothing for the
    // sanitizer to rewrite, so it arrives in the rendered output byte-for-byte.
    private static String sentinel(String variable) {
        return "LUVAXVAR" + variable.toUpperCase(Locale.ROOT) + "ENDLUVAX";
    }
}
