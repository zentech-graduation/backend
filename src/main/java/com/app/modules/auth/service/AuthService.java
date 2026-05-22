package com.app.modules.auth.service;

import jakarta.servlet.http.HttpServletRequest;

import com.app.modules.auth.dto.request.ForgotPasswordRequest;
import com.app.modules.auth.dto.request.LoginRequest;
import com.app.modules.auth.dto.request.RefreshRequest;
import com.app.modules.auth.dto.request.RegisterRequest;
import com.app.modules.auth.dto.request.ResetPasswordRequest;
import com.app.modules.auth.dto.response.AuthResponse;

/**
 * Email + password authentication flows. Tokens issued by these methods are stateless JWT access
 * tokens paired with opaque refresh tokens.
 */
public interface AuthService {

    /**
     * Registers a new user and records mail side-effect events for verification and welcome
     * messages. No session is issued — the user must verify their email before logging in.
     *
     * @param request validated registration payload
     */
    void register(RegisterRequest request);

    /**
     * Authenticates an existing user by email and password and issues a fresh session pair.
     *
     * @param request validated login payload
     * @param httpRequest underlying servlet request, used to capture device metadata
     * @return access + refresh tokens with the user summary
     */
    AuthResponse login(LoginRequest request, HttpServletRequest httpRequest);

    /**
     * Rotates the supplied refresh token, mints a new access token, and returns both.
     *
     * @param request payload containing the raw refresh token
     * @param httpRequest underlying servlet request, used to capture the new IP
     * @return new access + refresh tokens with the user summary
     */
    AuthResponse refresh(RefreshRequest request, HttpServletRequest httpRequest);

    /**
     * Revokes the supplied refresh token. The operation is idempotent.
     *
     * @param request payload containing the raw refresh token
     */
    void logout(RefreshRequest request);

    /**
     * Consumes the email-verification token, marks the credential as verified, and issues the first
     * session pair so the user is logged in immediately.
     *
     * @param rawToken raw verification token from the link the user followed
     * @param httpRequest underlying servlet request, used to capture device metadata
     * @return access + refresh tokens with the verified user summary
     */
    AuthResponse verifyEmail(String rawToken, HttpServletRequest httpRequest);

    /**
     * Records a fresh verification-mail request if an account with the supplied email exists.
     * Behaviour is silent when the address is unknown to avoid account enumeration.
     *
     * @param email candidate email address
     */
    void resendVerification(String email);

    /**
     * Records a password-reset mail request if an account with the supplied email exists. Behaviour
     * is silent when the address is unknown.
     *
     * @param request payload containing the email address
     */
    void forgotPassword(ForgotPasswordRequest request);

    /**
     * Consumes a password-reset token, replaces the password hash, revokes every active session for
     * that user, and records a security-notification mail event.
     *
     * @param request payload containing the raw token and the new password
     */
    void resetPassword(ResetPasswordRequest request);
}
