package com.app.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import com.app.common.config.security.SecurityProperties;

class IpExtractorTest {

    private IpExtractor extractor(List<String> cidrs) {
        SecurityProperties props = new SecurityProperties(cidrs, 2048);
        return new IpExtractor(props);
    }

    @Test
    void emptyTrustedList_alwaysReturnsRemoteAddr() {
        IpExtractor ex = extractor(List.of());
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("1.2.3.4");
        req.addHeader("X-Forwarded-For", "203.0.113.5");

        assertThat(ex.extract(req)).isEqualTo("1.2.3.4");
    }

    @Test
    void trustedExactIp_xffPresent_returnsXff() {
        IpExtractor ex = extractor(List.of("127.0.0.1"));
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("127.0.0.1");
        req.addHeader("X-Forwarded-For", "203.0.113.5");

        assertThat(ex.extract(req)).isEqualTo("203.0.113.5");
    }

    @Test
    void remoteNotTrusted_returnsRemoteAddr_ignoresXff() {
        IpExtractor ex = extractor(List.of("127.0.0.1"));
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("10.0.0.1");
        req.addHeader("X-Forwarded-For", "203.0.113.5");

        assertThat(ex.extract(req)).isEqualTo("10.0.0.1");
    }

    @Test
    void trustedExactIp_xffChain_returnsLeftmost() {
        IpExtractor ex = extractor(List.of("127.0.0.1"));
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("127.0.0.1");
        req.addHeader("X-Forwarded-For", "203.0.113.5, 198.51.100.1");

        assertThat(ex.extract(req)).isEqualTo("203.0.113.5");
    }

    @Test
    void trustedExactIp_noXff_fallsBackToRemote() {
        IpExtractor ex = extractor(List.of("127.0.0.1"));
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("127.0.0.1");

        assertThat(ex.extract(req)).isEqualTo("127.0.0.1");
    }

    @Test
    void trustedCidr_remoteInsideRange_returnsXff() {
        IpExtractor ex = extractor(List.of("10.0.0.0/8"));
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("10.5.4.3");
        req.addHeader("X-Forwarded-For", "203.0.113.5");

        assertThat(ex.extract(req)).isEqualTo("203.0.113.5");
    }

    @Test
    void trustedCidr_remoteOutsideRange_returnsRemoteAddr() {
        IpExtractor ex = extractor(List.of("10.0.0.0/8"));
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("11.0.0.1");
        req.addHeader("X-Forwarded-For", "203.0.113.5");

        assertThat(ex.extract(req)).isEqualTo("11.0.0.1");
    }

    @Test
    void malformedEntry_skipped_validEntryStillWorks() {
        // "notanip/99" is invalid and must be skipped at construction; "127.0.0.1" must remain
        // functional.
        IpExtractor ex = extractor(List.of("notanip/99", "127.0.0.1"));
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("127.0.0.1");
        req.addHeader("X-Forwarded-For", "203.0.113.5");

        assertThat(ex.extract(req)).isEqualTo("203.0.113.5");
    }
}
