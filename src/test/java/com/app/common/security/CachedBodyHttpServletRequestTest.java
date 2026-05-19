package com.app.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class CachedBodyHttpServletRequestTest {

    @Test
    void smallBody_withinLimit_readsSuccessfully() throws IOException {
        byte[] payload = new byte[100];
        java.util.Arrays.fill(payload, (byte) 'a');
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContent(payload);

        CachedBodyHttpServletRequest wrapped = new CachedBodyHttpServletRequest(request, 2048);

        assertThat(wrapped.getCachedBody()).isEqualTo(payload);
        assertThat(new String(wrapped.getCachedBody(), StandardCharsets.UTF_8)).hasSize(100);
    }

    @Test
    void bodyExceedingLimit_throwsIOException() {
        byte[] payload = new byte[3000];
        java.util.Arrays.fill(payload, (byte) 'b');
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContent(payload);

        assertThatThrownBy(() -> new CachedBodyHttpServletRequest(request, 2048))
                .isInstanceOf(IOException.class)
                .hasMessageStartingWith("Request body exceeds maximum");
    }

    @Test
    void emptyBody_readsSuccessfully() throws IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContent(new byte[0]);

        CachedBodyHttpServletRequest wrapped = new CachedBodyHttpServletRequest(request, 2048);

        assertThat(wrapped.getCachedBody()).isEmpty();
    }

    @Test
    void bodyExactlyAtBoundary_readsSuccessfully() throws IOException {
        byte[] payload = new byte[2048];
        java.util.Arrays.fill(payload, (byte) 'c');
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContent(payload);

        CachedBodyHttpServletRequest wrapped = new CachedBodyHttpServletRequest(request, 2048);

        assertThat(wrapped.getCachedBody()).hasSize(2048).isEqualTo(payload);
    }
}
