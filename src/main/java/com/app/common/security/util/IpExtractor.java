package com.app.common.security.util;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.app.common.config.security.SecurityProperties;

/**
 * Extracts the real client IP from an {@link HttpServletRequest}.
 *
 * <p>Only reads {@code X-Forwarded-For} when the direct connection peer ({@code getRemoteAddr()})
 * matches a configured trusted-proxy entry. This prevents spoofing by untrusted clients who set the
 * header themselves.
 */
@Component
public class IpExtractor {

    private static final Logger log = LoggerFactory.getLogger(IpExtractor.class);

    private final List<CidrRange> trustedRanges;

    /**
     * Pre-computes parsed CIDR/IP entries from {@code SecurityProperties.trustedProxyCidrs()}.
     *
     * <p>Malformed entries are logged at WARN level and skipped; they do not prevent startup or
     * affect the remaining valid entries.
     */
    public IpExtractor(SecurityProperties properties) {
        List<CidrRange> ranges = new ArrayList<>();
        for (String entry : properties.trustedProxyCidrs()) {
            try {
                ranges.add(CidrRange.parse(entry));
            } catch (IllegalArgumentException | UnknownHostException ex) {
                log.warn("Skipping malformed trusted-proxy entry '{}': {}", entry, ex.getMessage());
            }
        }
        this.trustedRanges = List.copyOf(ranges);
    }

    /**
     * Returns the effective client IP for {@code request}.
     *
     * <p>If {@code request.getRemoteAddr()} is a configured trusted proxy, the leftmost value of
     * the {@code X-Forwarded-For} header is returned (trimmed). If no such header is present the
     * remote address is returned as fallback. If the remote address is not trusted, the remote
     * address is returned unconditionally and the header is ignored.
     */
    public String extract(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        if (!isTrusted(remote)) {
            return remote;
        }
        String xff = request.getHeader("X-Forwarded-For");
        if (!StringUtils.hasText(xff)) {
            return remote;
        }
        int comma = xff.indexOf(',');
        return (comma > 0 ? xff.substring(0, comma) : xff).trim();
    }

    private boolean isTrusted(String remote) {
        InetAddress addr;
        try {
            addr = InetAddress.getByName(remote);
        } catch (UnknownHostException ex) {
            return false;
        }
        for (CidrRange range : trustedRanges) {
            if (range.matches(addr)) {
                return true;
            }
        }
        return false;
    }

    private record CidrRange(InetAddress network, int prefixLength, boolean isExact) {

        /**
         * Parses an entry that is either a plain IP ({@code "127.0.0.1"}) or CIDR notation ({@code
         * "10.0.0.0/8"}). Throws {@link IllegalArgumentException} or {@link UnknownHostException}
         * on malformed input.
         */
        static CidrRange parse(String entry) throws UnknownHostException {
            int slash = entry.indexOf('/');
            if (slash < 0) {
                InetAddress addr = InetAddress.getByName(entry.trim());
                // Normalize to ensure consistent string representation.
                InetAddress normalized = InetAddress.getByName(addr.getHostAddress());
                return new CidrRange(normalized, -1, true);
            }
            String ipPart = entry.substring(0, slash).trim();
            String prefixPart = entry.substring(slash + 1).trim();
            int prefix;
            try {
                prefix = Integer.parseInt(prefixPart);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Invalid prefix length: " + prefixPart);
            }
            InetAddress network = InetAddress.getByName(ipPart);
            if (!(network instanceof Inet4Address)) {
                throw new IllegalArgumentException("Only IPv4 CIDR ranges are supported: " + entry);
            }
            if (prefix < 0 || prefix > 32) {
                throw new IllegalArgumentException("Prefix length out of range: " + prefix);
            }
            return new CidrRange(network, prefix, false);
        }

        boolean matches(InetAddress address) {
            if (isExact) {
                return network.getHostAddress().equals(address.getHostAddress());
            }
            // CIDR match: only works for IPv4.
            if (!(address instanceof Inet4Address) || !(network instanceof Inet4Address)) {
                return false;
            }
            byte[] addrBytes = address.getAddress();
            byte[] netBytes = network.getAddress();
            int mask = prefixLength == 0 ? 0 : (0xFFFFFFFF << (32 - prefixLength));
            int addrInt =
                    ((addrBytes[0] & 0xFF) << 24)
                            | ((addrBytes[1] & 0xFF) << 16)
                            | ((addrBytes[2] & 0xFF) << 8)
                            | (addrBytes[3] & 0xFF);
            int netInt =
                    ((netBytes[0] & 0xFF) << 24)
                            | ((netBytes[1] & 0xFF) << 16)
                            | ((netBytes[2] & 0xFF) << 8)
                            | (netBytes[3] & 0xFF);
            return (addrInt & mask) == (netInt & mask);
        }
    }
}
