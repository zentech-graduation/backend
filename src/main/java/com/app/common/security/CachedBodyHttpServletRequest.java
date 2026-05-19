package com.app.common.security;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

/**
 * Wraps an {@link HttpServletRequest} so its body can be read multiple times. Required by {@link
 * AuthRateLimitFilter}, which inspects the JSON body to derive a per-account rate-limit key while
 * still allowing the controller to bind the same body to a DTO.
 *
 * <p>Enforces an upper bound on the body size so an attacker cannot exhaust memory by streaming a
 * multi-MB payload at an unauthenticated endpoint. If the limit is exceeded an {@link IOException}
 * is thrown whose message starts with {@code "Request body exceeds maximum"}; callers translate
 * that to a 400 response.
 */
public class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

    private final byte[] cachedBody;

    public CachedBodyHttpServletRequest(HttpServletRequest request, int maxBodyBytes)
            throws IOException {
        super(request);
        InputStream inputStream = request.getInputStream();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int totalRead = 0;
        int bytesRead;
        while ((bytesRead = inputStream.read(chunk)) != -1) {
            totalRead += bytesRead;
            if (totalRead > maxBodyBytes) {
                throw new IOException(
                        "Request body exceeds maximum allowed size of " + maxBodyBytes + " bytes");
            }
            buffer.write(chunk, 0, bytesRead);
        }
        this.cachedBody = buffer.toByteArray();
    }

    public byte[] getCachedBody() {
        return cachedBody;
    }

    @Override
    public ServletInputStream getInputStream() {
        return new CachedBodyServletInputStream(cachedBody);
    }

    @Override
    public BufferedReader getReader() {
        Charset charset =
                getCharacterEncoding() != null
                        ? Charset.forName(getCharacterEncoding())
                        : StandardCharsets.UTF_8;
        return new BufferedReader(new InputStreamReader(getInputStream(), charset));
    }

    private static final class CachedBodyServletInputStream extends ServletInputStream {

        private final ByteArrayInputStream delegate;

        CachedBodyServletInputStream(byte[] body) {
            this.delegate = new ByteArrayInputStream(body);
        }

        @Override
        public boolean isFinished() {
            return delegate.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int read() {
            return delegate.read();
        }
    }
}
