package com.app.common.security.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;
import com.app.common.security.jwt.JwtClaims;
import com.app.common.security.jwt.JwtTokenProvider;
import com.app.common.security.service.TokenBlacklistService;
import com.app.common.security.user.SecurityMapper;
import com.app.common.security.user.UserPrincipal;
import com.app.modules.users.enums.UserStatus;
import com.app.modules.users.repository.UserRepository;
import com.app.modules.users.repository.UserSecurityProjection;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

@ExtendWith(MockitoExtension.class)
class TokenPrincipalResolverImplTest {

    private static final String TOKEN = "header.payload.signature";
    private static final UUID USER_ID = UUID.randomUUID();
    private static final String JTI = UUID.randomUUID().toString();

    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private TokenBlacklistService tokenBlacklistService;
    @Mock private UserRepository userRepository;
    @Mock private SecurityMapper securityMapper;

    private TokenPrincipalResolverImpl resolver;

    @BeforeEach
    void setUp() {
        resolver =
                new TokenPrincipalResolverImpl(
                        jwtTokenProvider, tokenBlacklistService, userRepository, securityMapper);
    }

    @Test
    void resolve_activeUser_returnsPrincipal() {
        JwtClaims claims = new JwtClaims(USER_ID, "USER", JTI, Instant.now().plusSeconds(300));
        UserSecurityProjection projection = buildProjection(UserStatus.ACTIVE);
        UserPrincipal principal = new UserPrincipal(USER_ID, "user@example.com", "USER", "ACTIVE");
        when(jwtTokenProvider.validateAndParse(TOKEN)).thenReturn(claims);
        when(tokenBlacklistService.isBlacklisted(JTI)).thenReturn(false);
        when(userRepository.findProjectedByIdAndDeletedAtIsNull(USER_ID))
                .thenReturn(Optional.of(projection));
        when(securityMapper.toUserPrincipal(projection)).thenReturn(principal);

        Optional<UserPrincipal> result = resolver.resolve(TOKEN);

        assertThat(result).contains(principal);
    }

    @Test
    void resolve_blacklistedJti_returnsEmpty() {
        JwtClaims claims = new JwtClaims(USER_ID, "USER", JTI, Instant.now().plusSeconds(300));
        when(jwtTokenProvider.validateAndParse(TOKEN)).thenReturn(claims);
        when(tokenBlacklistService.isBlacklisted(JTI)).thenReturn(true);

        Optional<UserPrincipal> result = resolver.resolve(TOKEN);

        assertThat(result).isEmpty();
    }

    @Test
    void resolve_bannedUser_returnsEmpty() {
        JwtClaims claims = new JwtClaims(USER_ID, "USER", JTI, Instant.now().plusSeconds(300));
        UserSecurityProjection projection = buildProjection(UserStatus.BANNED);
        when(jwtTokenProvider.validateAndParse(TOKEN)).thenReturn(claims);
        when(tokenBlacklistService.isBlacklisted(JTI)).thenReturn(false);
        when(userRepository.findProjectedByIdAndDeletedAtIsNull(USER_ID))
                .thenReturn(Optional.of(projection));

        Optional<UserPrincipal> result = resolver.resolve(TOKEN);

        assertThat(result).isEmpty();
    }

    @Test
    void resolve_suspendedUser_returnsEmpty() {
        JwtClaims claims = new JwtClaims(USER_ID, "USER", JTI, Instant.now().plusSeconds(300));
        UserSecurityProjection projection = buildProjection(UserStatus.SUSPENDED);
        when(jwtTokenProvider.validateAndParse(TOKEN)).thenReturn(claims);
        when(tokenBlacklistService.isBlacklisted(JTI)).thenReturn(false);
        when(userRepository.findProjectedByIdAndDeletedAtIsNull(USER_ID))
                .thenReturn(Optional.of(projection));

        Optional<UserPrincipal> result = resolver.resolve(TOKEN);

        assertThat(result).isEmpty();
    }

    @Test
    void resolve_userNotFoundOrSoftDeleted_returnsEmpty() {
        JwtClaims claims = new JwtClaims(USER_ID, "USER", JTI, Instant.now().plusSeconds(300));
        when(jwtTokenProvider.validateAndParse(TOKEN)).thenReturn(claims);
        when(tokenBlacklistService.isBlacklisted(JTI)).thenReturn(false);
        when(userRepository.findProjectedByIdAndDeletedAtIsNull(USER_ID))
                .thenReturn(Optional.empty());

        Optional<UserPrincipal> result = resolver.resolve(TOKEN);

        assertThat(result).isEmpty();
    }

    @Test
    void resolve_invalidToken_logsErrorCodeAndReturnsEmpty() {
        when(jwtTokenProvider.validateAndParse(TOKEN))
                .thenThrow(new AppException(ApiErrorCode.AUTH_TOKEN_INVALID));

        Logger logger = (Logger) LoggerFactory.getLogger(TokenPrincipalResolverImpl.class);
        Level originalLevel = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.DEBUG);
        Optional<UserPrincipal> result;
        try {
            result = resolver.resolve(TOKEN);
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(originalLevel);
        }

        assertThat(result).isEmpty();
        assertThat(appender.list)
                .anyMatch(event -> event.getFormattedMessage().contains("AUTH_TOKEN_INVALID"));
    }

    private static UserSecurityProjection buildProjection(UserStatus status) {
        UserSecurityProjection projection = mock(UserSecurityProjection.class);
        when(projection.getStatus()).thenReturn(status);
        return projection;
    }
}
