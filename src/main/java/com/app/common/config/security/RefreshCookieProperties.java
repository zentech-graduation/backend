package com.app.common.config.security;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Binds the refresh-token cookie's transport attributes from {@code app.security.refresh-cookie.*}.
 *
 * <p>{@code secure} defaults to {@code true} so a deployment that forgets to configure it fails
 * closed rather than transmitting a thirty-day credential in clear text. Local development runs on
 * plain HTTP and overrides it to {@code false} in the {@code dev} profile.
 *
 * <p>{@code sameSite} is the CSRF control for the refresh endpoint, which is anonymous and exempt
 * from CSRF filtering. {@code Lax} withholds the cookie from cross-site POSTs. {@code None} removes
 * that protection and is permitted only when the browser client and the API sit on different
 * registrable domains.
 *
 * <p>The cookie's {@code Max-Age} is deliberately not configurable here. It is derived from {@code
 * app.jwt.refresh-token-ttl}, which also sets the persisted token's expiry, so the two cannot drift
 * apart.
 */
@Validated
@ConfigurationProperties(prefix = "app.security.refresh-cookie")
public record RefreshCookieProperties(
        @NotBlank @DefaultValue("luvax_refresh") String name,
        @NotBlank @DefaultValue("/api/v1/auth") String path,
        @DefaultValue("true") boolean secure,
        @Pattern(regexp = "Lax|Strict|None") @DefaultValue("Lax") String sameSite) {

    /**
     * Rejects {@code SameSite=None} without {@code Secure}. Browsers discard such a cookie without
     * reporting anything to the server, so the failure would otherwise surface only as every user
     * being logged out on page reload.
     */
    @AssertTrue(
            message =
                    "app.security.refresh-cookie.secure must be true when same-site is None,"
                            + " otherwise browsers discard the cookie")
    public boolean isSecureWhenSameSiteIsNone() {
        return !"None".equals(sameSite) || secure;
    }
}
